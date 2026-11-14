package com.edubuddy.app.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A source of "now" that tests can control. */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    fun nowLocal(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

object TimeMath {

    /** Minutes since midnight → LocalTime. */
    fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    fun minuteOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

    /**
     * Next start (strictly after [after]) of a weekly class on [dayOfWeek] (1=Mon..7=Sun)
     * at [startMinute]. Uses the zone's rules so DST shifts are handled by java.time.
     */
    fun nextWeeklyOccurrence(
        dayOfWeek: Int,
        startMinute: Int,
        after: Instant,
        zone: ZoneId,
    ): ZonedDateTime {
        val now = after.atZone(zone)
        val target = DayOfWeek.of(dayOfWeek)
        var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target))
        var candidate = ZonedDateTime.of(date, timeOf(startMinute), zone)
        if (!candidate.toInstant().isAfter(after)) {
            date = date.plusWeeks(1)
            candidate = ZonedDateTime.of(date, timeOf(startMinute), zone)
        }
        return candidate
    }

    /**
     * When a reminder with [offsetMinutes] lead time should fire for the next occurrence
     * of a weekly class, such that the fire time is after [after].
     */
    fun nextWeeklyReminder(
        dayOfWeek: Int,
        startMinute: Int,
        offsetMinutes: Int,
        after: Instant,
        zone: ZoneId,
    ): Pair<Instant, ZonedDateTime> {
        // Look from (after + offset) so the reminder itself lands in the future.
        val occ = nextWeeklyOccurrence(dayOfWeek, startMinute, after.plus(Duration.ofMinutes(offsetMinutes.toLong())), zone)
        return occ.toInstant().minus(Duration.ofMinutes(offsetMinutes.toLong())) to occ
    }

    /**
     * When to fire a reminder for something starting at [startMillis] with the chosen [leadMinutes].
     * If that moment has already passed (e.g. a task due tonight with a "1 day before" reminder),
     * fall back to 1 hour, then 15 minutes before, so the user still gets a nudge.
     * Returns null when no reminder should be set.
     */
    fun effectiveReminderAt(startMillis: Long, leadMinutes: Int, nowMillis: Long): Long? {
        if (leadMinutes < 0 || startMillis <= nowMillis) return null
        val candidates = listOf(leadMinutes, 60, 15).filter { it <= leadMinutes }.distinct()
        return candidates.map { startMillis - it * 60_000L }.firstOrNull { it > nowMillis + 30_000L }
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Days left in the month including [today]. */
    fun daysLeftInMonth(today: LocalDate): Int {
        val ym = YearMonth.from(today)
        return ym.lengthOfMonth() - today.dayOfMonth + 1
    }
}

object Fmt {
    private val locale: Locale get() = Locale.getDefault()

    fun time(t: LocalTime, is24h: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))

    fun time(minuteOfDay: Int, is24h: Boolean): String = time(TimeMath.timeOf(minuteOfDay), is24h)

    fun dayShort(dow: Int): String = DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, locale)
    fun dayFull(dow: Int): String = DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, locale)

    /** "Thu, 13 Nov" */
    fun dateShort(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))

    /** "13 Nov 2026" */
    fun dateMedium(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    /** "Thursday, 13 November" */
    fun dateLong(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

    /** "November 2026" */
    fun month(ym: YearMonth): String = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    /** "Today", "Tomorrow", "Yesterday", weekday within a week, otherwise "Thu, 13 Nov". */
    fun relativeDay(d: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, d)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2..6 -> d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            d.year == today.year -> dateShort(d)
            else -> dateMedium(d)
        }
    }

    /** For date picker fields: "Today, 13 Nov", "Tomorrow, 14 Nov", otherwise "Sat, 15 Nov". */
    fun pickerDate(d: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, d)
        val dm = d.format(DateTimeFormatter.ofPattern("d MMM", locale))
        return when (diff) {
            0L -> "Today, $dm"
            1L -> "Tomorrow, $dm"
            -1L -> "Yesterday, $dm"
            else -> if (d.year == today.year) dateShort(d) else dateMedium(d)
        }
    }

    /** "in 2h 15m", "in 3 days", "5m ago", "2 days overdue"-style durations. */
    fun countdown(from: Instant, to: Instant): String {
        val minutes = Duration.between(from, to).toMinutes()
        val abs = kotlin.math.abs(minutes)
        val body = when {
            abs < 1 -> return "now"
            abs < 60 -> "${abs}m"
            abs < 60 * 24 -> {
                val h = abs / 60; val m = abs % 60
                if (m == 0L) "${h}h" else "${h}h ${m}m"
            }
            else -> {
                val d = abs / (60 * 24)
                if (d == 1L) "1 day" else "$d days"
            }
        }
        return if (minutes >= 0) "in $body" else "$body ago"
    }

    fun durationMinutes(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    fun reminderLabel(minutes: Int): String = when {
        minutes < 0 -> "No reminder"
        minutes == 0 -> "At start time"
        minutes < 60 -> "$minutes min before"
        minutes % (60 * 24 * 7) == 0 -> "${minutes / (60 * 24 * 7)} week before"
        minutes % (60 * 24) == 0 -> { val d = minutes / (60 * 24); if (d == 1) "1 day before" else "$d days before" }
        minutes % 60 == 0 -> { val h = minutes / 60; if (h == 1) "1 hour before" else "$h hours before" }
        else -> "${durationMinutes(minutes)} before"
    }
}
