package com.edubuddy.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val institution: String = "",
    val course: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val use24h: Boolean = false,
    /** Global attendance target in percent. */
    val attendanceTarget: Int = 75,
    val defaultClassReminder: Int = 10,
    val defaultTaskReminder: Int = 24 * 60,
    val defaultEventReminder: Int = 60,
    val dailyDigest: Boolean = true,
    /** Minutes after midnight for the morning summary. */
    val dailyDigestMinute: Int = 7 * 60 + 30,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val appLock: Boolean = false,
    /** Include Saturday in the timetable week strip. Sunday is shown only if classes exist. */
    val showSaturday: Boolean = true,
)

/** A running focus timer, persisted so it survives process death. */
data class FocusTimerState(
    val endsAt: Long = 0L,
    val startedAt: Long = 0L,
    val subjectId: Long? = null,
    val isBreak: Boolean = false,
    /** If paused: milliseconds remaining; 0 when running or idle. */
    val pausedRemaining: Long = 0L,
    val plannedMinutes: Int = 0,
) {
    val isRunning get() = endsAt > 0L && pausedRemaining == 0L
    val isPaused get() = pausedRemaining > 0L
    val isIdle get() = endsAt == 0L && pausedRemaining == 0L
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val institution = stringPreferencesKey("institution")
        val course = stringPreferencesKey("course")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val use24h = booleanPreferencesKey("use_24h")
        val target = intPreferencesKey("attendance_target")
        val classRem = intPreferencesKey("default_class_reminder")
        val taskRem = intPreferencesKey("default_task_reminder")
        val eventRem = intPreferencesKey("default_event_reminder")
        val digest = booleanPreferencesKey("daily_digest")
        val digestMinute = intPreferencesKey("daily_digest_minute")
        val focusMin = intPreferencesKey("focus_minutes")
        val breakMin = intPreferencesKey("break_minutes")
        val appLock = booleanPreferencesKey("app_lock")
        val showSat = booleanPreferencesKey("show_saturday")

        val fEndsAt = longPreferencesKey("focus_ends_at")
        val fStartedAt = longPreferencesKey("focus_started_at")
        val fSubject = longPreferencesKey("focus_subject")
        val fBreak = booleanPreferencesKey("focus_is_break")
        val fPaused = longPreferencesKey("focus_paused_remaining")
        val fPlanned = intPreferencesKey("focus_planned")
    }

    private val prefs: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<Settings> = prefs.map { p ->
        val d = Settings()
        Settings(
            onboarded = p[K.onboarded] ?: d.onboarded,
            name = p[K.name] ?: d.name,
            institution = p[K.institution] ?: d.institution,
            course = p[K.course] ?: d.course,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            use24h = p[K.use24h] ?: d.use24h,
            attendanceTarget = p[K.target] ?: d.attendanceTarget,
            defaultClassReminder = p[K.classRem] ?: d.defaultClassReminder,
            defaultTaskReminder = p[K.taskRem] ?: d.defaultTaskReminder,
            defaultEventReminder = p[K.eventRem] ?: d.defaultEventReminder,
            dailyDigest = p[K.digest] ?: d.dailyDigest,
            dailyDigestMinute = p[K.digestMinute] ?: d.dailyDigestMinute,
            focusMinutes = p[K.focusMin] ?: d.focusMinutes,
            breakMinutes = p[K.breakMin] ?: d.breakMinutes,
            appLock = p[K.appLock] ?: d.appLock,
            showSaturday = p[K.showSat] ?: d.showSaturday,
        )
    }

    val focusTimer: Flow<FocusTimerState> = prefs.map { p ->
        FocusTimerState(
            endsAt = p[K.fEndsAt] ?: 0L,
            startedAt = p[K.fStartedAt] ?: 0L,
            subjectId = p[K.fSubject]?.takeIf { it > 0 },
            isBreak = p[K.fBreak] ?: false,
            pausedRemaining = p[K.fPaused] ?: 0L,
            plannedMinutes = p[K.fPlanned] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()
    suspend fun currentFocus(): FocusTimerState = focusTimer.first()

    suspend fun update(transform: (Settings) -> Settings) {
        val old = current()
        val s = transform(old)
        context.dataStore.edit { p ->
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name.trim()
            p[K.institution] = s.institution.trim()
            p[K.course] = s.course.trim()
            p[K.theme] = s.themeMode.name
            p[K.dynamic] = s.dynamicColor
            p[K.use24h] = s.use24h
            p[K.target] = s.attendanceTarget.coerceIn(1, 99)
            p[K.classRem] = s.defaultClassReminder
            p[K.taskRem] = s.defaultTaskReminder
            p[K.eventRem] = s.defaultEventReminder
            p[K.digest] = s.dailyDigest
            p[K.digestMinute] = s.dailyDigestMinute.coerceIn(0, 24 * 60 - 1)
            p[K.focusMin] = s.focusMinutes.coerceIn(5, 180)
            p[K.breakMin] = s.breakMinutes.coerceIn(1, 60)
            p[K.appLock] = s.appLock
            p[K.showSat] = s.showSaturday
        }
    }

    suspend fun setFocus(state: FocusTimerState) {
        context.dataStore.edit { p ->
            p[K.fEndsAt] = state.endsAt
            p[K.fStartedAt] = state.startedAt
            p[K.fSubject] = state.subjectId ?: 0L
            p[K.fBreak] = state.isBreak
            p[K.fPaused] = state.pausedRemaining
            p[K.fPlanned] = state.plannedMinutes
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
