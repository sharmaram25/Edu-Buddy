package com.edubuddy.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Storage conventions
 *  - Instants (deadlines, event times) are epoch milliseconds (UTC).
 *  - Calendar dates (expense date, attendance date) are LocalDate epoch days.
 *  - Weekly class times are local wall-clock: dayOfWeek (1=Mon..7=Sun) + minutes since midnight,
 *    so a timetable stays correct if the phone changes timezone.
 *  - Money is whole paise (Long).
 *  - reminderMinutes < 0 means "no reminder".
 */

@Serializable
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
    val instructor: String = "",
    val colorIndex: Int = 0,
    /** Per-subject attendance target in percent, or null to use the global one. */
    val attendanceTarget: Int? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class ClassKind(val label: String) { LECTURE("Lecture"), LAB("Lab"), TUTORIAL("Tutorial"), OTHER("Other") }

@Serializable
@Entity(
    tableName = "class_slots",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("subjectId"), Index("dayOfWeek")],
)
data class ClassSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val room: String = "",
    val kind: ClassKind = ClassKind.LECTURE,
    val reminderMinutes: Int = 10,
)

enum class EventKind(val label: String) {
    SEMINAR("Seminar"), GUEST_LECTURE("Guest lecture"), EXAM("Exam"),
    WORKSHOP("Workshop"), EXTRA_CLASS("Extra class"), OTHER("Other")
}

@Serializable
@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("subjectId"), Index("startAt")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: EventKind = EventKind.SEMINAR,
    val speaker: String = "",
    val location: String = "",
    val startAt: Long,
    val endAt: Long? = null,
    val notes: String = "",
    val subjectId: Long? = null,
    val reminderMinutes: Int = 60,
)

enum class TaskKind(val label: String) {
    ASSIGNMENT("Assignment"), PROJECT("Project"), EXAM("Exam prep"), READING("Reading"), OTHER("Other")
}

enum class Priority(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

@Serializable
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("subjectId"), Index("dueAt"), Index("completedAt")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val kind: TaskKind = TaskKind.ASSIGNMENT,
    val dueAt: Long,
    val priority: Priority = Priority.MEDIUM,
    val notes: String = "",
    val completedAt: Long? = null,
    val reminderMinutes: Int = 24 * 60,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

enum class TxnType { EXPENSE, INCOME }

@Serializable
@Entity(tableName = "transactions", indices = [Index("epochDay"), Index("type")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val type: TxnType = TxnType.EXPENSE,
    /** Stable key from [com.edubuddy.app.data.model.Categories]. */
    val category: String,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Monthly spending limit. A month without a row inherits the latest earlier one. */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    /** "yyyy-MM" */
    @PrimaryKey val yearMonth: String,
    val amountPaise: Long,
)

enum class AttendanceStatus { PRESENT, ABSENT, CANCELLED }

@Serializable
@Entity(
    tableName = "attendance",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    // One mark per timetable slot per day. Manual entries (slotId == null) are unconstrained,
    // because SQLite treats NULLs as distinct in unique indexes.
    indices = [Index("subjectId"), Index(value = ["slotId", "epochDay"], unique = true), Index("epochDay")],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** Not a foreign key on purpose: history survives if a slot is later removed from the timetable. */
    val slotId: Long? = null,
    val epochDay: Long,
    val status: AttendanceStatus,
)

@Serializable
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("subjectId"), Index("startedAt")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val startedAt: Long,
    val minutes: Int,
)

/**
 * A day or range of days with no classes (festival break, exam week, college closed).
 * Inclusive on both ends, stored as LocalDate epoch days. Class reminders are skipped and
 * attendance isn't expected on these days.
 */
@Serializable
@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDay: Long,
    val endDay: Long,
) {
    fun covers(epochDay: Long) = epochDay in startDay..endDay
}
