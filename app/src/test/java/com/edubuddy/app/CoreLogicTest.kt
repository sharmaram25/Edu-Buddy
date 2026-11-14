package com.edubuddy.app

import com.edubuddy.app.core.AttendanceMath
import com.edubuddy.app.core.Clock
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.Money
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.backup.BackupCodec
import com.edubuddy.app.data.backup.BackupException
import com.edubuddy.app.data.backup.BackupFile
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.db.TaskKind
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.domain.AgendaMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MoneyTest {
    private fun ok(s: String) = (Money.parse(s) as Money.ParseResult.Ok).paise
    private fun err(s: String) = Money.parse(s) is Money.ParseResult.Error

    @Test fun parsesWholeAndDecimalAmounts() {
        assertEquals(15000L, ok("150"))
        assertEquals(125050L, ok("1,250.50"))
        assertEquals(9990L, ok("₹ 99.9"))
        assertEquals(1L, ok("0.01"))
        assertEquals(10000L, ok("100.00"))
    }

    @Test fun rejectsBadInput() {
        assertTrue(err(""))
        assertTrue(err("abc"))
        assertTrue(err("0"))
        assertTrue(err("-50"))
        assertTrue(err("1.234"))
        assertTrue(err("1e9"))  // above one crore
    }

    @Test fun sumsHaveNoFloatingPointDrift() {
        // 0.1 + 0.2 style errors were possible with the old float storage.
        val total = listOf("0.10", "0.20", "0.30").sumOf { ok(it) }
        assertEquals(60L, total)
    }

    @Test fun formatsIndianGrouping() {
        assertEquals("₹1,25,000", Money.format(1_25_000_00))
        assertEquals("₹1,250.50", Money.format(125050))
        assertEquals("₹99", Money.format(9900))
        assertEquals("+₹50", Money.format(5000, showSign = true))
        assertEquals("−₹50", Money.format(-5000))
    }

    @Test fun compactFormat() {
        assertEquals("₹950", Money.formatCompact(95000))
        assertEquals("₹1.2K", Money.formatCompact(1_200_00))
        assertEquals("₹3.5L", Money.formatCompact(3_50_000_00))
    }

    @Test fun inputRoundTrip() {
        assertEquals("1250.5", Money.toInput(125050))
        assertEquals("100", Money.toInput(10000))
    }
}

class AttendanceMathTest {
    @Test fun canSkipWhenAboveTarget() {
        // 30/36 = 83.3%. Missing 4 more → 30/40 = 75% exactly, still OK. 5 → 30/41 = 73%.
        assertEquals(4, AttendanceMath.classesYouCanSkip(30, 36, 0.75))
    }

    @Test fun mustAttendWhenBelowTarget() {
        // 6/10 = 60%. Need n: (6+n)/(10+n) >= .75 → n >= 6.
        assertEquals(6, AttendanceMath.classesNeededToReach(6, 10, 0.75))
        assertEquals(0, AttendanceMath.classesYouCanSkip(6, 10, 0.75))
    }

    @Test fun exactlyOnTarget() {
        assertEquals(0, AttendanceMath.classesYouCanSkip(3, 4, 0.75))
        assertEquals(0, AttendanceMath.classesNeededToReach(3, 4, 0.75))
        assertTrue(AttendanceMath.Summary(3, 4, 0.75).onTrack)
    }

    @Test fun noClassesYet() {
        val s = AttendanceMath.Summary(0, 0, 0.75)
        assertEquals(0.0, s.percent, 0.0)
        assertTrue(s.onTrack)
        assertEquals(0, s.canSkip)
        assertEquals(0, s.mustAttend)
    }

    @Test fun answersAreConsistentWithDefinition() {
        // Exhaustive check against the definitions for small numbers and common targets.
        for (target in listOf(0.6, 0.65, 0.7, 0.75, 0.8, 0.85)) for (total in 1..60) for (att in 0..total) {
            val skip = AttendanceMath.classesYouCanSkip(att, total, target)
            if (skip > 0) assertTrue(att.toDouble() / (total + skip) >= target - 1e-9)
            assertFalse("skip+1 must break target", att.toDouble() / (total + skip + 1) >= target - 1e-9 && att.toDouble() / total >= target)
            val need = AttendanceMath.classesNeededToReach(att, total, target)
            assertTrue((att + need).toDouble() / (total + need) >= target - 1e-9)
            if (need > 0) assertTrue((att + need - 1).toDouble() / (total + need - 1) < target)
        }
    }
}

class TimeMathTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(s: String, zone: ZoneId = kolkata): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()

    @Test fun nextOccurrenceLaterThisWeek() {
        // Thu 2026-11-12 08:00 → next Friday 09:00 class is 2026-11-13.
        val next = TimeMath.nextWeeklyOccurrence(5, 9 * 60, at("2026-11-12T08:00"), kolkata)
        assertEquals(LocalDateTime.parse("2026-11-13T09:00"), next.toLocalDateTime())
    }

    @Test fun sameDayAlreadyStartedRollsToNextWeek() {
        val next = TimeMath.nextWeeklyOccurrence(4, 9 * 60, at("2026-11-12T09:00"), kolkata)
        assertEquals(LocalDateTime.parse("2026-11-19T09:00"), next.toLocalDateTime())
    }

    @Test fun reminderIsAlwaysInTheFuture() {
        // Class Thu 09:00 with 10 min reminder; it's Thu 08:55 → this week's reminder (08:50) passed,
        // so the next reminder must be next Thursday 08:50.
        val (fire, occ) = TimeMath.nextWeeklyReminder(4, 9 * 60, 10, at("2026-11-12T08:55"), kolkata)
        assertEquals(LocalDateTime.parse("2026-11-19T09:00"), occ.toLocalDateTime())
        assertEquals(at("2026-11-19T08:50"), fire)
    }

    @Test fun reminderThisWeekWhenStillAhead() {
        val (fire, _) = TimeMath.nextWeeklyReminder(4, 9 * 60, 10, at("2026-11-12T08:30"), kolkata)
        assertEquals(at("2026-11-12T08:50"), fire)
    }

    @Test fun dstSpringForwardKeepsWallClock() {
        // Europe/London springs forward on 2026-03-29. A Monday 09:00 class stays at 09:00 local.
        val london = ZoneId.of("Europe/London")
        val next = TimeMath.nextWeeklyOccurrence(1, 9 * 60, at("2026-03-27T12:00", london), london)
        assertEquals(LocalDateTime.parse("2026-03-30T09:00"), next.toLocalDateTime())
    }

    @Test fun reminderUsesChosenLeadWhenPossible() {
        val now = 0L
        val due = 3 * 86_400_000L
        assertEquals(due - 86_400_000L, TimeMath.effectiveReminderAt(due, 24 * 60, now))
    }

    @Test fun reminderFallsBackWhenLeadAlreadyPassed() {
        val now = 0L
        // Due in 5 hours with a 1-day reminder → fall back to 1 hour before.
        assertEquals(4 * 3_600_000L, TimeMath.effectiveReminderAt(5 * 3_600_000L, 24 * 60, now))
        // Due in 40 minutes → 15 minutes before.
        assertEquals(25 * 60_000L, TimeMath.effectiveReminderAt(40 * 60_000L, 24 * 60, now))
        // Due in 10 minutes → too late for any reminder.
        assertEquals(null, TimeMath.effectiveReminderAt(10 * 60_000L, 24 * 60, now))
    }

    @Test fun reminderNeverFallsBackToLongerLead() {
        // A 5-minute reminder must never become "1 hour before".
        assertEquals(55 * 60_000L, TimeMath.effectiveReminderAt(60 * 60_000L, 5, 0L))
        assertEquals(null, TimeMath.effectiveReminderAt(60 * 60_000L, -1, 0L))
        assertEquals(null, TimeMath.effectiveReminderAt(-1L, 10, 0L))
    }

    @Test fun daysLeftInMonthCountsToday() {
        assertEquals(1, TimeMath.daysLeftInMonth(LocalDate.of(2026, 11, 30)))
        assertEquals(30, TimeMath.daysLeftInMonth(LocalDate.of(2026, 11, 1)))
        assertEquals(1, TimeMath.daysLeftInMonth(LocalDate.of(2028, 2, 29)))
    }

    @Test fun reminderLabels() {
        assertEquals("No reminder", Fmt.reminderLabel(-1))
        assertEquals("At start time", Fmt.reminderLabel(0))
        assertEquals("10 min before", Fmt.reminderLabel(10))
        assertEquals("1 hour before", Fmt.reminderLabel(60))
        assertEquals("1 day before", Fmt.reminderLabel(1440))
        assertEquals("2 days before", Fmt.reminderLabel(2880))
        assertEquals("1 week before", Fmt.reminderLabel(10080))
    }

    @Test fun countdownText() {
        val base = at("2026-11-12T10:00")
        assertEquals("in 45m", Fmt.countdown(base, base.plusSeconds(45 * 60)))
        assertEquals("in 2h 15m", Fmt.countdown(base, base.plusSeconds(135 * 60)))
        assertEquals("in 3 days", Fmt.countdown(base, base.plusSeconds(3 * 86400)))
        assertEquals("5m ago", Fmt.countdown(base, base.minusSeconds(5 * 60)))
    }
}

class AgendaTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime.parse("2026-11-12T10:30").atZone(zone).toInstant()
        override fun zone(): ZoneId = zone
    }

    @Test fun classesForDaySortedAndArchivedHidden() {
        val math = SubjectEntity(id = 1, name = "Maths")
        val old = SubjectEntity(id = 2, name = "Old", archived = true)
        val slots = listOf(
            ClassSlotEntity(id = 10, subjectId = 1, dayOfWeek = 4, startMinute = 14 * 60, endMinute = 15 * 60),
            ClassSlotEntity(id = 11, subjectId = 1, dayOfWeek = 4, startMinute = 10 * 60, endMinute = 11 * 60),
            ClassSlotEntity(id = 12, subjectId = 2, dayOfWeek = 4, startMinute = 9 * 60, endMinute = 10 * 60),
            ClassSlotEntity(id = 13, subjectId = 1, dayOfWeek = 5, startMinute = 9 * 60, endMinute = 10 * 60),
        )
        val thursday = LocalDate.of(2026, 11, 12)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)
        val result = AgendaMath.classesOn(thursday, slots, mapOf(1L to math, 2L to old), mapOf(11L to AttendanceStatus.PRESENT), clock)
        assertEquals(listOf(11L, 10L), result.map { it.slot.id })
        assertEquals(AttendanceStatus.PRESENT, result[0].mark)
        assertTrue(result[0].isOngoing(clock.now()))
        assertFalse(result[1].isOngoing(clock.now()))
    }
}

class BackupCodecTest {
    @Test fun roundTrip() {
        val b = BackupFile(
            exportedAt = 1_700_000_000_000, appVersion = "1.0.0",
            settings = Settings(onboarded = true, name = "Ananya", attendanceTarget = 80),
            subjects = listOf(SubjectEntity(id = 1, name = "Physics", colorIndex = 3, attendanceTarget = 85)),
            classSlots = listOf(ClassSlotEntity(id = 5, subjectId = 1, dayOfWeek = 2, startMinute = 540, endMinute = 600, room = "LT-1")),
            tasks = listOf(TaskEntity(id = 9, title = "Lab report", subjectId = 1, kind = TaskKind.ASSIGNMENT, dueAt = 1_700_100_000_000)),
            transactions = listOf(TransactionEntity(id = 3, title = "Chai", amountPaise = 1500, category = "food", epochDay = 20000)),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(b))
        assertEquals(b, decoded)
    }

    @Test fun rejectsForeignFiles() {
        expectBackupError("""{"hello":"world"}""")
        expectBackupError("not json at all")
        expectBackupError("""{"format":"something-else","version":1,"exportedAt":0,"appVersion":"x","settings":{}}""")
    }

    @Test fun rejectsNewerVersions() {
        expectBackupError("""{"format":"edubuddy-backup","version":99,"exportedAt":0,"appVersion":"x","settings":{}}""")
    }

    @Test fun toleratesUnknownFields() {
        val text = """{"format":"edubuddy-backup","version":1,"exportedAt":0,"appVersion":"x","settings":{"futureField":true},"newTable":[]}"""
        val b = BackupCodec.decode(text)
        assertEquals(0, b.subjects.size)
    }

    private fun expectBackupError(text: String) {
        try { BackupCodec.decode(text); fail("Expected BackupException") } catch (_: BackupException) {}
    }
}

class HolidayTest {
    private val diwali = com.edubuddy.app.data.db.HolidayEntity(
        id = 1, title = "Diwali break",
        startDay = LocalDate.of(2026, 11, 7).toEpochDay(), endDay = LocalDate.of(2026, 11, 11).toEpochDay(),
    )

    @Test fun coversIsInclusiveOnBothEnds() {
        assertTrue(diwali.covers(LocalDate.of(2026, 11, 7).toEpochDay()))
        assertTrue(diwali.covers(LocalDate.of(2026, 11, 11).toEpochDay()))
        assertFalse(diwali.covers(LocalDate.of(2026, 11, 6).toEpochDay()))
        assertFalse(diwali.covers(LocalDate.of(2026, 11, 12).toEpochDay()))
    }

    @Test fun holidayOnFindsTheCoveringBreak() {
        val exams = diwali.copy(id = 2, title = "Exam week", startDay = LocalDate.of(2026, 12, 1).toEpochDay(), endDay = LocalDate.of(2026, 12, 7).toEpochDay())
        val list = listOf(diwali, exams)
        assertEquals("Diwali break", AgendaMath.holidayOn(LocalDate.of(2026, 11, 9), list)?.title)
        assertEquals("Exam week", AgendaMath.holidayOn(LocalDate.of(2026, 12, 3), list)?.title)
        assertEquals(null, AgendaMath.holidayOn(LocalDate.of(2026, 11, 20), list))
    }

    @Test fun backupsWithoutHolidaysStillDecode() {
        // Files written by 1.0 have no "holidays" key.
        val old = BackupCodec.encode(BackupFile(exportedAt = 0, appVersion = "1.0.0", settings = Settings()))
            .replace(Regex(""",\s*"holidays"\s*:\s*\[\s*]"""), "")
        assertFalse(old.contains("holidays"))
        assertTrue(BackupCodec.decode(old).holidays.isEmpty())
    }
}
