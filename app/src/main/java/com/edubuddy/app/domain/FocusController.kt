package com.edubuddy.app.domain

import com.edubuddy.app.core.Clock
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.db.FocusSessionEntity
import com.edubuddy.app.data.prefs.FocusTimerState
import com.edubuddy.app.data.prefs.SettingsRepository
import com.edubuddy.app.reminders.ReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pomodoro-style timer whose state lives in DataStore, not in memory, and whose end is an
 * AlarmManager alarm. So the timer keeps going and the session is still recorded even if
 * the app is swiped away or the process is killed.
 */
class FocusController(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    val state = settings.focusTimer

    suspend fun start(subjectId: Long?, minutes: Int, isBreak: Boolean) = mutex.withLock {
        val now = clock.now().toEpochMilli()
        val ends = now + minutes * 60_000L
        settings.setFocus(FocusTimerState(endsAt = ends, startedAt = now, subjectId = subjectId, isBreak = isBreak, plannedMinutes = minutes))
        reminders.scheduleFocusEnd(ends)
    }

    suspend fun pause() = mutex.withLock {
        val s = settings.currentFocus()
        if (!s.isRunning) return@withLock
        val remaining = (s.endsAt - clock.now().toEpochMilli()).coerceAtLeast(1_000L)
        settings.setFocus(s.copy(pausedRemaining = remaining))
        reminders.cancelFocusEnd()
    }

    suspend fun resume() = mutex.withLock {
        val s = settings.currentFocus()
        if (!s.isPaused) return@withLock
        val ends = clock.now().toEpochMilli() + s.pausedRemaining
        settings.setFocus(s.copy(endsAt = ends, pausedRemaining = 0L))
        reminders.scheduleFocusEnd(ends)
    }

    /**
     * Stops early. A focus session of at least 5 minutes is still logged with the minutes
     * actually spent, because partial focus still counts.
     */
    suspend fun stop() = mutex.withLock {
        val s = settings.currentFocus()
        if (s.isIdle) return@withLock
        reminders.cancelFocusEnd()
        if (!s.isBreak) {
            val remaining = if (s.isPaused) s.pausedRemaining else (s.endsAt - clock.now().toEpochMilli()).coerceAtLeast(0)
            val spentMinutes = ((s.plannedMinutes * 60_000L - remaining) / 60_000L).toInt()
            if (spentMinutes >= 5) {
                db.focus().insert(FocusSessionEntity(subjectId = s.subjectId, startedAt = s.startedAt, minutes = spentMinutes))
            }
        }
        settings.setFocus(FocusTimerState())
    }

    /**
     * Completes the running timer if it has reached its end. Safe to call from both the UI
     * and the alarm receiver: only the first caller records the session.
     * @param expectedEndsAt the end time the caller believes in, or 0 to accept any.
     */
    suspend fun completeIfDue(expectedEndsAt: Long = 0L): FocusTimerState? = mutex.withLock {
        val s = settings.currentFocus()
        if (!s.isRunning) return@withLock null
        if (expectedEndsAt != 0L && s.endsAt != expectedEndsAt) return@withLock null
        if (clock.now().toEpochMilli() < s.endsAt - 2_000) return@withLock null
        if (!s.isBreak) {
            db.focus().insert(FocusSessionEntity(subjectId = s.subjectId, startedAt = s.startedAt, minutes = s.plannedMinutes))
        }
        settings.setFocus(FocusTimerState())
        reminders.cancelFocusEnd()
        s
    }
}
