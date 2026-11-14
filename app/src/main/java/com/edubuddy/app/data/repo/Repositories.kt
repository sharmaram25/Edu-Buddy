package com.edubuddy.app.data.repo

import androidx.room.withTransaction
import com.edubuddy.app.core.Clock
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.db.AttendanceEntity
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.BudgetEntity
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.SubtaskEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.reminders.ReminderScheduler
import com.edubuddy.app.widget.WidgetUpdater
import java.time.LocalDate
import java.time.YearMonth

/*
 * Repositories are the only place that writes to the database. Every write that can affect a
 * reminder re-syncs that reminder here, so the UI can never forget to (the old app's main bug).
 */

class SubjectRepository(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
) {
    val subjects = db.subjects().observeAll()
    fun observe(id: Long) = db.subjects().observe(id)
    suspend fun get(id: Long) = db.subjects().get(id)

    suspend fun save(subject: SubjectEntity): Long {
        val id = db.subjects().upsert(subject).let { if (subject.id != 0L) subject.id else it }
        widgets.refresh()
        return id
    }

    /** Deletes the subject with its timetable slots and attendance (cascade). */
    suspend fun delete(subject: SubjectEntity) {
        val slots = db.classSlots().forSubject(subject.id)
        db.subjects().delete(subject)
        slots.forEach { reminders.cancelClass(it.id) }
        widgets.refresh()
    }
}

class ScheduleRepository(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
) {
    val slots = db.classSlots().observeAll()
    val events = db.events().observeAll()
    fun slotsForSubject(subjectId: Long) = db.classSlots().observeForSubject(subjectId)
    fun eventsBetween(from: Long, to: Long) = db.events().observeBetween(from, to)

    suspend fun getSlot(id: Long) = db.classSlots().get(id)
    suspend fun getEvent(id: Long) = db.events().get(id)

    suspend fun saveSlot(slot: ClassSlotEntity): Long {
        require(slot.endMinute > slot.startMinute) { "Class must end after it starts" }
        val id = db.classSlots().upsert(slot).let { if (slot.id != 0L) slot.id else it }
        reminders.syncClass(slot.copy(id = id))
        widgets.refresh()
        return id
    }

    suspend fun deleteSlot(slot: ClassSlotEntity) {
        db.classSlots().delete(slot)
        reminders.cancelClass(slot.id)
        widgets.refresh()
    }

    /** Returns any existing slots on the same day that overlap the given time range. */
    suspend fun findClashes(dayOfWeek: Int, start: Int, end: Int, ignoreId: Long): List<ClassSlotEntity> =
        db.classSlots().all().filter {
            it.id != ignoreId && it.dayOfWeek == dayOfWeek && it.startMinute < end && start < it.endMinute
        }

    suspend fun saveEvent(event: EventEntity): Long {
        val id = db.events().upsert(event).let { if (event.id != 0L) event.id else it }
        reminders.syncEvent(event.copy(id = id))
        widgets.refresh()
        return id
    }

    suspend fun deleteEvent(event: EventEntity) {
        db.events().delete(event)
        reminders.cancelEvent(event.id)
        widgets.refresh()
    }

    /** Re-inserts a previously deleted item with its original id (for Undo). */
    suspend fun restoreSlot(slot: ClassSlotEntity) { db.classSlots().insertAll(listOf(slot)); reminders.syncClass(slot); widgets.refresh() }
    suspend fun restoreEvent(event: EventEntity) { db.events().insertAll(listOf(event)); reminders.syncEvent(event); widgets.refresh() }
}

class TaskRepository(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
    private val clock: Clock,
) {
    val tasks = db.tasks().observeAllWithProgress()
    fun observe(id: Long) = db.tasks().observe(id)
    fun subtasks(taskId: Long) = db.tasks().observeSubtasks(taskId)
    suspend fun get(id: Long) = db.tasks().get(id)
    suspend fun getSubtasks(taskId: Long) = db.tasks().subtasks(taskId)

    suspend fun save(task: TaskEntity, subtasks: List<SubtaskEntity>? = null): Long {
        val id = db.withTransaction {
            val newId = db.tasks().upsert(task).let { if (task.id != 0L) task.id else it }
            if (subtasks != null) db.tasks().replaceSubtasks(newId, subtasks)
            newId
        }
        reminders.syncTask(task.copy(id = id))
        widgets.refresh()
        return id
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val t = db.tasks().get(id) ?: return
        val updated = t.copy(completedAt = if (completed) clock.now().toEpochMilli() else null)
        db.tasks().upsert(updated)
        reminders.syncTask(updated)
        widgets.refresh()
    }

    suspend fun toggleSubtask(s: SubtaskEntity) {
        db.tasks().upsertSubtask(s.copy(done = !s.done))
    }

    data class Deleted(val task: TaskEntity, val subtasks: List<SubtaskEntity>)

    suspend fun delete(id: Long): Deleted? {
        val t = db.tasks().get(id) ?: return null
        val subs = db.tasks().subtasks(id)
        db.tasks().delete(t)
        reminders.cancelTask(id)
        widgets.refresh()
        return Deleted(t, subs)
    }

    suspend fun restore(d: Deleted) {
        db.withTransaction {
            db.tasks().insertAll(listOf(d.task))
            db.tasks().insertSubtasks(d.subtasks)
        }
        reminders.syncTask(d.task)
        widgets.refresh()
    }
}

class MoneyRepository(private val db: AppDatabase, private val widgets: WidgetUpdater) {
    fun monthTransactions(ym: YearMonth) =
        db.money().observeBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())

    fun effectiveBudget(ym: YearMonth) = db.money().observeEffectiveBudget(ym.toString())

    suspend fun get(id: Long) = db.money().get(id)
    suspend fun suggestions(type: TxnType, prefix: String) =
        if (prefix.isBlank()) emptyList() else db.money().titleSuggestions(type, prefix.trim())

    suspend fun save(t: TransactionEntity): Long {
        val id = db.money().upsert(t).let { if (t.id != 0L) t.id else it }
        widgets.refresh()
        return id
    }

    suspend fun delete(t: TransactionEntity) { db.money().delete(t); widgets.refresh() }
    suspend fun restore(t: TransactionEntity) { db.money().insertAll(listOf(t)); widgets.refresh() }

    /** Sets the budget from [ym] onwards (later months inherit it until changed). 0 clears it. */
    suspend fun setBudget(ym: YearMonth, paise: Long) {
        db.money().upsertBudget(BudgetEntity(ym.toString(), paise.coerceAtLeast(0)))
        widgets.refresh()
    }

    suspend fun allNewestFirst() = db.money().allNewestFirst()
}

class AttendanceRepository(private val db: AppDatabase, private val widgets: WidgetUpdater) {
    fun forDay(day: LocalDate) = db.attendance().observeForDay(day.toEpochDay())
    fun forSubject(subjectId: Long) = db.attendance().observeForSubject(subjectId)
    val counts = db.attendance().observeCounts()

    /** Marks (or re-marks) a timetabled class. Passing null clears the mark. */
    suspend fun markSlot(slot: ClassSlotEntity, day: LocalDate, status: AttendanceStatus?) {
        val existing = db.attendance().findForSlot(slot.id, day.toEpochDay())
        when {
            status == null -> existing?.let { db.attendance().delete(it) }
            existing != null -> db.attendance().upsert(existing.copy(status = status))
            else -> db.attendance().upsert(
                AttendanceEntity(subjectId = slot.subjectId, slotId = slot.id, epochDay = day.toEpochDay(), status = status)
            )
        }
        widgets.refresh()
    }

    /** An extra or past class that isn't in the timetable. */
    suspend fun addManual(subjectId: Long, day: LocalDate, status: AttendanceStatus) {
        db.attendance().upsert(AttendanceEntity(subjectId = subjectId, slotId = null, epochDay = day.toEpochDay(), status = status))
    }

    suspend fun update(a: AttendanceEntity) = db.attendance().upsert(a)
    suspend fun delete(a: AttendanceEntity) = db.attendance().delete(a)
    suspend fun restore(a: AttendanceEntity) = db.attendance().insertAll(listOf(a))
}

class HolidayRepository(private val db: AppDatabase, private val widgets: WidgetUpdater) {
    val holidays = db.holidays().observeAll()

    /**
     * Class alarms keep their weekly chain; the receiver checks for a holiday when one fires,
     * so adding or removing a break never needs to touch AlarmManager.
     */
    suspend fun save(h: HolidayEntity): Long {
        val fixed = if (h.endDay < h.startDay) h.copy(startDay = h.endDay, endDay = h.startDay) else h
        val id = db.holidays().upsert(fixed).let { if (fixed.id != 0L) fixed.id else it }
        widgets.refresh()
        return id
    }

    suspend fun delete(h: HolidayEntity) { db.holidays().delete(h); widgets.refresh() }
    suspend fun restore(h: HolidayEntity) { db.holidays().insertAll(listOf(h)); widgets.refresh() }
    suspend fun on(day: LocalDate): HolidayEntity? = db.holidays().on(day.toEpochDay())
}
