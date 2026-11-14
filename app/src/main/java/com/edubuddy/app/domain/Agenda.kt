package com.edubuddy.app.domain

import com.edubuddy.app.core.Clock
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.prefs.SettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** A timetabled class on a specific date. */
data class ClassOccurrence(
    val slot: ClassSlotEntity,
    val subject: SubjectEntity,
    val date: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val mark: AttendanceStatus?,
) {
    fun isOngoing(now: Instant) = !now.isBefore(start.toInstant()) && now.isBefore(end.toInstant())
    fun isOver(now: Instant) = !now.isBefore(end.toInstant())
}

object AgendaMath {
    /** The holiday or break covering [date], if any. */
    fun holidayOn(date: LocalDate, holidays: List<HolidayEntity>): HolidayEntity? =
        holidays.firstOrNull { it.covers(date.toEpochDay()) }

    /** Classes for [date] from the weekly timetable, skipping archived subjects. */
    fun classesOn(
        date: LocalDate,
        slots: List<ClassSlotEntity>,
        subjects: Map<Long, SubjectEntity>,
        marks: Map<Long, AttendanceStatus>,
        clock: Clock,
    ): List<ClassOccurrence> {
        val dow = date.dayOfWeek.value
        return slots.asSequence()
            .filter { it.dayOfWeek == dow }
            .mapNotNull { slot ->
                val subject = subjects[slot.subjectId]?.takeIf { !it.archived } ?: return@mapNotNull null
                ClassOccurrence(
                    slot = slot,
                    subject = subject,
                    date = date,
                    start = ZonedDateTime.of(date, TimeMath.timeOf(slot.startMinute), clock.zone()),
                    end = ZonedDateTime.of(date, TimeMath.timeOf(slot.endMinute.coerceAtMost(24 * 60 - 1)), clock.zone()),
                    mark = marks[slot.id],
                )
            }
            .sortedBy { it.slot.startMinute }
            .toList()
    }
}

data class Digest(val title: String, val summary: String, val details: String)

/** Builds the morning summary notification text. Returns null when there's nothing to say. */
class DigestBuilder(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun build(): Digest? {
        val s = settings.current()
        val today = clock.today()
        val zone = clock.zone()
        val subjects = db.subjects().all().associateBy { it.id }
        val holiday = db.holidays().on(today.toEpochDay())
        val classes = if (holiday != null) emptyList() else AgendaMath.classesOn(today, db.classSlots().all(), subjects, emptyMap(), clock)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dueToday: List<TaskEntity> = db.tasks().pendingBetween(dayStart, dayEnd)
        val overdue = db.tasks().overdueCount(dayStart)
        val events: List<EventEntity> = db.events().all().filter { it.startAt in dayStart until dayEnd }

        if (classes.isEmpty() && dueToday.isEmpty() && overdue == 0 && events.isEmpty()) return null

        val parts = buildList {
            if (classes.isNotEmpty()) add("${classes.size} ${if (classes.size == 1) "class" else "classes"}")
            if (dueToday.isNotEmpty()) add("${dueToday.size} due today")
            if (events.isNotEmpty()) add("${events.size} ${if (events.size == 1) "event" else "events"}")
            if (overdue > 0) add("$overdue overdue")
        }
        val lines = buildList {
            if (holiday != null) add("${holiday.title}: no classes today")
            classes.firstOrNull()?.let {
                add("First class: ${it.subject.name} at ${Fmt.time(it.slot.startMinute, s.use24h)}" +
                    (it.slot.room.takeIf { r -> r.isNotBlank() }?.let { r -> " · $r" } ?: ""))
            }
            dueToday.take(3).forEach { add("Due: ${it.title}") }
            events.take(2).forEach {
                val t = Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalTime()
                add("${it.kind.label}: ${it.title} at ${Fmt.time(t, s.use24h)}")
            }
        }
        val greetingName = s.name.substringBefore(' ').ifBlank { null }
        return Digest(
            title = if (greetingName != null) "Good morning, $greetingName" else "Your day at a glance",
            summary = parts.joinToString(" · "),
            details = (listOf(parts.joinToString(" · ")) + lines).joinToString("\n"),
        )
    }
}
