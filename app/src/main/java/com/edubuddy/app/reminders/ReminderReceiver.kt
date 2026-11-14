package com.edubuddy.app.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.edubuddy.app.EduBuddyApp
import com.edubuddy.app.R
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.AttendanceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EduBuddyApp
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, app, intent)
                    ACTION_COMPLETE_TASK -> {
                        val id = intent.getLongExtra(EXTRA_ID, 0)
                        app.container.tasks.setCompleted(id, true)
                        Notifier(context).cancel(ReminderScheduler.notificationId(ReminderType.TASK, id))
                    }
                    ACTION_SNOOZE_TASK -> {
                        val id = intent.getLongExtra(EXTRA_ID, 0)
                        Notifier(context).cancel(ReminderScheduler.notificationId(ReminderType.TASK, id))
                        app.container.reminders.snoozeTask(id, SNOOZE_MINUTES)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, app: EduBuddyApp, intent: Intent) {
        val c = app.container
        val type = ReminderType.of(intent.getIntExtra(EXTRA_TYPE, 0)) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notifier = Notifier(context)
        val settings = c.settings.current()
        val nid = ReminderScheduler.notificationId(type, id)
        val zone = ZoneId.systemDefault()

        when (type) {
            ReminderType.CLASS -> {
                val slot = c.db.classSlots().get(id) ?: return
                val subject = c.db.subjects().get(slot.subjectId)
                // Arm next week's reminder first so a crash below can't break the chain.
                c.reminders.syncClass(slot)
                if (subject == null || subject.archived) return
                val today = LocalDate.now(zone)
                if (c.holidays.on(today) != null) return
                val mark = c.db.attendance().findForSlot(slot.id, today.toEpochDay())
                if (mark?.status == AttendanceStatus.CANCELLED) return
                val time = Fmt.time(slot.startMinute, settings.use24h)
                val where = slot.room.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                val lead = if (slot.reminderMinutes == 0) "now" else "in ${Fmt.durationMinutes(slot.reminderMinutes)}"
                notifier.post(
                    nid, Notifier.Channel.CLASSES,
                    title = "${subject.name} $lead",
                    text = "${slot.kind.label} at $time$where",
                    openRoute = "today",
                )
            }

            ReminderType.EVENT -> {
                val e = c.db.events().get(id) ?: return
                // Edits re-sync (and so replace) the alarm; this guards against anything stale.
                if (e.reminderMinutes < 0 || e.startAt < System.currentTimeMillis() - 5 * 60_000L) return
                val start = Instant.ofEpochMilli(e.startAt).atZone(zone)
                val whenText = "${Fmt.relativeDay(start.toLocalDate(), LocalDate.now(zone))}, ${Fmt.time(start.toLocalTime(), settings.use24h)}"
                val details = listOfNotNull(
                    whenText,
                    e.location.takeIf { it.isNotBlank() },
                    e.speaker.takeIf { it.isNotBlank() }?.let { "by $it" },
                ).joinToString(" · ")
                notifier.post(nid, Notifier.Channel.EVENTS, title = "${e.kind.label}: ${e.title}", text = details, openRoute = "schedule")
            }

            ReminderType.TASK -> {
                val t = c.db.tasks().get(id) ?: return
                if (t.completedAt != null) return
                val lead = ((t.dueAt - System.currentTimeMillis()) / 60_000L).toInt()
                val subject = t.subjectId?.let { c.db.subjects().get(it) }
                val due = Instant.ofEpochMilli(t.dueAt).atZone(zone)
                val day = Fmt.relativeDay(due.toLocalDate(), LocalDate.now(zone))
                val dayText = if (day == "Today" || day == "Tomorrow" || day == "Yesterday") day.lowercase() else day
                val dueText = if (lead in 0..90) "Due ${Fmt.countdown(Instant.now(), due.toInstant())}"
                else "Due $dayText at ${Fmt.time(due.toLocalTime(), settings.use24h)}"
                val text = listOfNotNull(subject?.name, dueText).joinToString(" · ")
                notifier.post(
                    nid, Notifier.Channel.TASKS,
                    title = t.title,
                    text = text,
                    openRoute = "task/${t.id}",
                    actions = listOf(
                        action(context, R.drawable.ic_stat_edubuddy, "Mark done", ACTION_COMPLETE_TASK, t.id),
                        action(context, R.drawable.ic_stat_edubuddy, "Snooze 1h", ACTION_SNOOZE_TASK, t.id),
                    ),
                )
            }

            ReminderType.DIGEST -> {
                // Re-arm tomorrow and refresh far-off alarms every morning.
                c.reminders.rescheduleAll()
                val digest = c.digestBuilder.build()
                if (digest != null) {
                    notifier.post(nid, Notifier.Channel.DIGEST, title = digest.title, text = digest.summary, bigText = digest.details, openRoute = "today")
                }
            }

            ReminderType.FOCUS -> {
                val finished = c.focus.completeIfDue(expectedEndsAt = intent.getLongExtra(EXTRA_AT, 0))
                if (finished != null) {
                    val (title, text) = if (finished.isBreak) {
                        "Break's over" to "Ready for another focus session?"
                    } else {
                        "Focus session complete" to "${finished.plannedMinutes} minutes done. Take a ${settings.breakMinutes}-minute break."
                    }
                    notifier.post(nid, Notifier.Channel.FOCUS, title = title, text = text, openRoute = "focus")
                }
            }
        }
    }

    private fun action(context: Context, icon: Int, label: String, act: String, id: Long): NotificationCompat.Action {
        val i = Intent(context, ReminderReceiver::class.java).apply {
            action = act
            putExtra(EXTRA_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            context, (act.hashCode() * 31 + id).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, label, pi)
    }

    companion object {
        const val ACTION_FIRE = "com.edubuddy.app.action.REMINDER"
        const val ACTION_COMPLETE_TASK = "com.edubuddy.app.action.COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.edubuddy.app.action.SNOOZE_TASK"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ID = "id"
        const val EXTRA_AT = "at"
        const val SNOOZE_MINUTES = 60
    }
}

/** Rebuilds every alarm after reboot, app update, or a clock/timezone change. */
class SystemEventsReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val app = context.applicationContext as EduBuddyApp
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                app.container.reminders.rescheduleAll()
                app.container.widgets.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
