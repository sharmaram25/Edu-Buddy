package com.edubuddy.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun observe(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun get(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects")
    suspend fun all(): List<SubjectEntity>

    @Upsert suspend fun upsert(s: SubjectEntity): Long
    @Delete suspend fun delete(s: SubjectEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SubjectEntity>)
    @Query("DELETE FROM subjects") suspend fun clear()
}

@Dao
interface ClassSlotDao {
    @Query("SELECT * FROM class_slots ORDER BY dayOfWeek, startMinute")
    fun observeAll(): Flow<List<ClassSlotEntity>>

    @Query("SELECT * FROM class_slots WHERE subjectId = :subjectId ORDER BY dayOfWeek, startMinute")
    fun observeForSubject(subjectId: Long): Flow<List<ClassSlotEntity>>

    @Query("SELECT * FROM class_slots WHERE id = :id")
    suspend fun get(id: Long): ClassSlotEntity?

    @Query("SELECT * FROM class_slots")
    suspend fun all(): List<ClassSlotEntity>

    @Query("SELECT * FROM class_slots WHERE subjectId = :subjectId")
    suspend fun forSubject(subjectId: Long): List<ClassSlotEntity>

    @Upsert suspend fun upsert(s: ClassSlotEntity): Long
    @Delete suspend fun delete(s: ClassSlotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ClassSlotEntity>)
    @Query("DELETE FROM class_slots") suspend fun clear()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startAt")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    fun observeBetween(from: Long, to: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): EventEntity?

    @Query("SELECT * FROM events")
    suspend fun all(): List<EventEntity>

    @Query("SELECT * FROM events WHERE startAt > :after")
    suspend fun upcoming(after: Long): List<EventEntity>

    @Upsert suspend fun upsert(e: EventEntity): Long
    @Delete suspend fun delete(e: EventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<EventEntity>)
    @Query("DELETE FROM events") suspend fun clear()
}

data class TaskWithProgress(
    val id: Long,
    val title: String,
    val subjectId: Long?,
    val kind: TaskKind,
    val dueAt: Long,
    val priority: Priority,
    val notes: String,
    val completedAt: Long?,
    val reminderMinutes: Int,
    val createdAt: Long,
    val subtaskCount: Int,
    val subtaskDone: Int,
) {
    fun toEntity() = TaskEntity(id, title, subjectId, kind, dueAt, priority, notes, completedAt, reminderMinutes, createdAt)
}

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.*,
               (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id) AS subtaskCount,
               (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id AND s.done = 1) AS subtaskDone
        FROM tasks t
        ORDER BY (t.completedAt IS NOT NULL), t.dueAt
        """
    )
    fun observeAllWithProgress(): Flow<List<TaskWithProgress>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE completedAt IS NULL AND dueAt > :after")
    suspend fun pendingAfter(after: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE completedAt IS NULL AND dueAt >= :from AND dueAt < :to ORDER BY dueAt")
    suspend fun pendingBetween(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NULL AND dueAt < :now")
    suspend fun overdueCount(now: Long): Int

    @Upsert suspend fun upsert(t: TaskEntity): Long
    @Delete suspend fun delete(t: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<TaskEntity>)
    @Query("DELETE FROM tasks") suspend fun clear()

    // Subtasks
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    fun observeSubtasks(taskId: Long): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    suspend fun subtasks(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks")
    suspend fun allSubtasks(): List<SubtaskEntity>

    @Upsert suspend fun upsertSubtask(s: SubtaskEntity): Long
    @Delete suspend fun deleteSubtask(s: SubtaskEntity)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId") suspend fun deleteSubtasksFor(taskId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSubtasks(items: List<SubtaskEntity>)

    @Transaction
    suspend fun replaceSubtasks(taskId: Long, items: List<SubtaskEntity>) {
        deleteSubtasksFor(taskId)
        insertSubtasks(items.mapIndexed { i, s -> s.copy(id = 0, taskId = taskId, position = i) })
    }
}

data class CategoryTotal(val category: String, val total: Long)
data class DayTotal(val epochDay: Long, val total: Long)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM transactions WHERE epochDay >= :fromDay AND epochDay <= :toDay ORDER BY epochDay DESC, createdAt DESC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, createdAt DESC")
    suspend fun allNewestFirst(): List<TransactionEntity>

    @Query("SELECT DISTINCT title FROM transactions WHERE type = :type AND title LIKE :prefix || '%' ORDER BY createdAt DESC LIMIT 6")
    suspend fun titleSuggestions(type: TxnType, prefix: String): List<String>

    @Upsert suspend fun upsert(t: TransactionEntity): Long
    @Delete suspend fun delete(t: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<TransactionEntity>)
    @Query("DELETE FROM transactions") suspend fun clear()

    // Budgets
    @Query("SELECT * FROM budgets WHERE yearMonth <= :yearMonth ORDER BY yearMonth DESC LIMIT 1")
    fun observeEffectiveBudget(yearMonth: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<BudgetEntity>
    @Upsert suspend fun upsertBudget(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE yearMonth = :yearMonth") suspend fun deleteBudget(yearMonth: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgets(items: List<BudgetEntity>)
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
}

data class SubjectAttendanceCount(val subjectId: Long, val present: Int, val absent: Int)

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE subjectId = :subjectId ORDER BY epochDay DESC, id DESC")
    fun observeForSubject(subjectId: Long): Flow<List<AttendanceEntity>>

    @Query(
        """
        SELECT subjectId,
               SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS present,
               SUM(CASE WHEN status = 'ABSENT' THEN 1 ELSE 0 END) AS absent
        FROM attendance GROUP BY subjectId
        """
    )
    fun observeCounts(): Flow<List<SubjectAttendanceCount>>

    @Query("SELECT * FROM attendance WHERE slotId = :slotId AND epochDay = :epochDay LIMIT 1")
    suspend fun findForSlot(slotId: Long, epochDay: Long): AttendanceEntity?

    @Query("SELECT * FROM attendance") suspend fun all(): List<AttendanceEntity>

    @Upsert suspend fun upsert(a: AttendanceEntity): Long
    @Delete suspend fun delete(a: AttendanceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<AttendanceEntity>)
    @Query("DELETE FROM attendance") suspend fun clear()
}

data class SubjectMinutes(val subjectId: Long?, val minutes: Int)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeSince(from: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT subjectId, SUM(minutes) AS minutes FROM focus_sessions WHERE startedAt >= :from GROUP BY subjectId")
    fun observeMinutesBySubject(from: Long): Flow<List<SubjectMinutes>>

    @Query("SELECT * FROM focus_sessions") suspend fun all(): List<FocusSessionEntity>
    @Insert suspend fun insert(s: FocusSessionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<FocusSessionEntity>)
    @Query("DELETE FROM focus_sessions") suspend fun clear()
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY startDay")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays ORDER BY startDay") suspend fun all(): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE :epochDay BETWEEN startDay AND endDay ORDER BY startDay LIMIT 1")
    suspend fun on(epochDay: Long): HolidayEntity?

    @Upsert suspend fun upsert(h: HolidayEntity): Long
    @Delete suspend fun delete(h: HolidayEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<HolidayEntity>)
    @Query("DELETE FROM holidays") suspend fun clear()
}
