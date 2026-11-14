package com.edubuddy.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.edubuddy.app.core.Clock
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.prefs.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

enum class ReminderType(val code: Int) {
    CLASS(1), EVENT(2), TASK(3), DIGEST(4), FOCUS(5);

    companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
}

/**
 * Owns every AlarmManager alarm the app sets.
 *
 * Each reminder has a stable request code derived from (type, id), so re-syncing an item
 * replaces its alarm instead of stacking duplicates, and deleting an item cancels it.
 * [rescheduleAll] rebuilds everything from the database; it runs on app start, after reboot,
 * after app update, and when the clock or timezone changes.
 */
class ReminderScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    // ---- Per-item sync ---------------------------------------------------------------------

    fun syncTask(task: TaskEntity) {
        cancel(ReminderType.TASK, task.id)
        if (task.completedAt != null) return
        TimeMath.effectiveReminderAt(task.dueAt, task.reminderMinutes, clock.now().toEpochMilli())
            ?.let { schedule(ReminderType.TASK, task.id, it) }
    }

    /** Re-fires the task's reminder [minutes] from now without changing its deadline. */
    fun snoozeTask(id: Long, minutes: Int) {
        schedule(ReminderType.TASK, id, clock.now().toEpochMilli() + minutes * 60_000L)
    }

    fun cancelTask(id: Long) {
        cancel(ReminderType.TASK, id)
        Notifier(context).cancel(notificationId(ReminderType.TASK, id))
    }

    fun syncEvent(event: EventEntity) {
        cancel(ReminderType.EVENT, event.id)
        TimeMath.effectiveReminderAt(event.startAt, event.reminderMinutes, clock.now().toEpochMilli())
            ?.let { schedule(ReminderType.EVENT, event.id, it) }
    }

    fun cancelEvent(id: Long) = cancel(ReminderType.EVENT, id)

    fun syncClass(slot: ClassSlotEntity) {
        cancel(ReminderType.CLASS, slot.id)
        if (slot.reminderMinutes < 0) return
        val (fireAt, _) = nextClassReminder(slot, clock.now())
        schedule(ReminderType.CLASS, slot.id, fireAt.toEpochMilli())
    }

    fun nextClassReminder(slot: ClassSlotEntity, after: Instant): Pair<Instant, ZonedDateTime> =
        TimeMath.nextWeeklyReminder(slot.dayOfWeek, slot.startMinute, slot.reminderMinutes, after, clock.zone())

    fun cancelClass(id: Long) = cancel(ReminderType.CLASS, id)

    suspend fun syncDigest() {
        cancel(ReminderType.DIGEST, 0)
        val s = settings.current()
        if (!s.dailyDigest || !s.onboarded) return
        val now = clock.now()
        val zone = clock.zone()
        var at = ZonedDateTime.of(clock.today(), TimeMath.timeOf(s.dailyDigestMinute), zone)
        if (!at.toInstant().isAfter(now)) at = at.plusDays(1)
        schedule(ReminderType.DIGEST, 0, at.toInstant().toEpochMilli())
    }

    fun scheduleFocusEnd(endsAt: Long) = schedule(ReminderType.FOCUS, 0, endsAt)
    fun cancelFocusEnd() = cancel(ReminderType.FOCUS, 0)

    // ---- Full rebuild ----------------------------------------------------------------------

    suspend fun rescheduleAll() = mutex.withLock {
        val now = clock.now().toEpochMilli()
        db.classSlots().all().forEach { syncClass(it) }

        // AlarmManager allows ~500 alarms per app. Keep a generous margin by only arming the
        // nearest items; the rest are armed on a later rebuild (every app open and every morning).
        val horizon = now + Duration.ofDays(120).toMillis()
        db.events().upcoming(now)
            .filter { it.startAt < horizon }
            .sortedBy { it.startAt }.take(150)
            .forEach { syncEvent(it) }
        db.tasks().pendingAfter(now)
            .filter { it.dueAt < horizon }
            .sortedBy { it.dueAt }.take(200)
            .forEach { syncTask(it) }

        syncDigest()

        val focus = settings.currentFocus()
        if (focus.isRunning && focus.endsAt > 0) scheduleFocusEnd(focus.endsAt)
    }

    /** Cancels every alarm for items currently in the database. Call before bulk replacing data. */
    suspend fun cancelAll() = mutex.withLock {
        db.classSlots().all().forEach { cancel(ReminderType.CLASS, it.id) }
        db.events().all().forEach { cancel(ReminderType.EVENT, it.id) }
        db.tasks().all().forEach { cancel(ReminderType.TASK, it.id) }
        cancel(ReminderType.DIGEST, 0)
        cancel(ReminderType.FOCUS, 0)
    }

    // ---- AlarmManager plumbing ---------------------------------------------------------------

    private fun schedule(type: ReminderType, id: Long, atMillis: Long) {
        val pi = pendingIntent(type, id, atMillis, create = true) ?: return
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                // Without the exact-alarm grant the system may delay by a few minutes.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun cancel(type: ReminderType, id: Long) {
        pendingIntent(type, id, 0L, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(type: ReminderType, id: Long, atMillis: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_TYPE, type.code)
            putExtra(ReminderReceiver.EXTRA_ID, id)
            putExtra(ReminderReceiver.EXTRA_AT, atMillis)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, requestCode(type, id), intent, flags)
    }

    companion object {
        fun requestCode(type: ReminderType, id: Long): Int = type.code * 10_000_000 + (id % 10_000_000).toInt()
        fun notificationId(type: ReminderType, id: Long): Int = requestCode(type, id)
    }
}
