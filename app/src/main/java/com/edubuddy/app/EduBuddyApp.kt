package com.edubuddy.app

import android.app.Application
import android.content.Context
import com.edubuddy.app.core.Clock
import com.edubuddy.app.core.SystemClock
import com.edubuddy.app.data.backup.BackupManager
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.prefs.SettingsRepository
import com.edubuddy.app.data.repo.AttendanceRepository
import com.edubuddy.app.data.repo.HolidayRepository
import com.edubuddy.app.data.repo.MoneyRepository
import com.edubuddy.app.data.repo.ScheduleRepository
import com.edubuddy.app.data.repo.SubjectRepository
import com.edubuddy.app.data.repo.TaskRepository
import com.edubuddy.app.domain.DigestBuilder
import com.edubuddy.app.domain.FocusController
import com.edubuddy.app.reminders.Notifier
import com.edubuddy.app.reminders.ReminderScheduler
import com.edubuddy.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EduBuddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        // Rebuild alarms on every cold start: cheap, idempotent, and self-healing if the
        // system dropped them (e.g. after a force-stop).
        container.appScope.launch { container.reminders.rescheduleAll() }
    }
}

/** Manual dependency graph. One instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        android.util.Log.e("EduBuddy", "Background task failed", e)
    })
    val clock: Clock = SystemClock

    val db: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val notifier = Notifier(appContext)
    val reminders = ReminderScheduler(appContext, db, settings, clock)
    val widgets = WidgetUpdater(appContext, appScope)

    val subjects = SubjectRepository(db, reminders, widgets)
    val schedule = ScheduleRepository(db, reminders, widgets)
    val tasks = TaskRepository(db, reminders, widgets, clock)
    val money = MoneyRepository(db, widgets)
    val attendance = AttendanceRepository(db, widgets)
    val holidays = HolidayRepository(db, widgets)
    val focus = FocusController(db, settings, reminders, clock)
    val digestBuilder = DigestBuilder(db, settings, clock)
    val backup = BackupManager(appContext, db, settings, reminders, clock, BuildConfig.VERSION_NAME)
}
