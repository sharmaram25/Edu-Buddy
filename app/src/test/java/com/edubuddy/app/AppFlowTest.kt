package com.edubuddy.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.ClassKind
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.EventKind
import com.edubuddy.app.data.db.Priority
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.SubtaskEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.db.TaskKind
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.data.prefs.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * End-to-end run of the real app on Robolectric: onboarding, seeding realistic data through
 * the repositories, visiting every screen, editing, and dark mode. Saves screenshots to
 * app/build/screenshots for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1_000) // let entry animations settle
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { act ->
            val v = act.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForText(text: String, timeout: Long = 15_000) {
        try {
            rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val name = "zz_failed_waiting_for_${text.filter { it.isLetterOrDigit() }}"
            shot(name)
            val roots = rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes()
            val dump = buildString {
                append("roots=${roots.size}\n")
                roots.indices.forEach { i ->
                    append(runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot())[i].printToString(maxDepth = 60) }.getOrElse { it.toString() })
                    append("\n-----\n")
                }
            }
            File(outDir, "$name.txt").writeText(dump)
            throw e
        }
    }

    private val container get() = (rule.activity.application as EduBuddyApp).container

    @Test
    fun fullJourney() {
        // ---- Onboarding ----
        waitForText("Get started")
        shot("01_welcome")
        rule.onNodeWithText("Get started").performClick()
        waitForText("About you")
        rule.onNodeWithText("Your name").performTextInput("Ananya Rao")
        rule.onNodeWithText("Course & year").performTextInput("B.Tech CSE, 2nd year")
        shot("02_profile")
        rule.onNodeWithText("Continue").performClick()
        waitForText("Set up reminders")
        shot("03_reminders")
        rule.onNodeWithText("Start using EduBuddy").performClick()
        waitForText("Good", 20_000)

        val settings = runBlocking { container.settings.current() }
        assertTrue(settings.onboarded)
        assertEquals("Ananya Rao", settings.name)

        // ---- Seed realistic data through the same repositories the UI uses ----
        seed()
        rule.waitForIdle()
        waitForText("Classes · ")
        shot("04_today")

        // ---- Tabs ----
        tab("Schedule")
        waitForText("Timetable")
        shot("05_schedule_timetable")
        click("Events")
        waitForText("Robotics in Medicine", 10_000)
        shot("06_schedule_events")

        tab("Tasks")
        waitForText("DUE THIS WEEK")
        shot("07_tasks")

        tab("Money")
        waitForText("Where it went")
        shot("08_money")

        tab("More")
        waitForText("Subjects & attendance")
        shot("09_more")

        click("Subjects & attendance")
        waitForText("OVERALL ATTENDANCE")
        shot("10_subjects")
        click("Data Structures")
        waitForText("Dr. Mehta")        // only present once the subject has loaded
        waitForText("attended")
        assertTrue(rule.onAllNodesWithText("Not in your timetable yet.").fetchSemanticsNodes().isEmpty())
        shot("11_subject_detail")
        back(); back()

        waitForText("Focus timer")
        click("Focus timer")
        waitForText("Start focus")
        shot("12_focus")
        back()

        waitForText("Reminders, appearance")
        click("Reminders, appearance", substring = true)
        waitForText("Attendance target")
        shot("13_settings")
        back()

        // ---- Create a task through the editor and verify it lands in the database ----
        waitForText("Subjects & attendance")
        tab("Tasks")
        waitForText("New task")
        click("New task")
        waitForText("What needs doing?")
        rule.onNodeWithText("What needs doing?").performTextInput("Submit DBMS mini project")
        shot("14_task_editor")
        click("Save")
        rule.waitUntil(10_000) {
            runBlocking { container.db.tasks().all().any { it.title == "Submit DBMS mini project" } }
        }
        // The new task must have a reminder alarm armed (due tomorrow 23:59, default 1 day before).
        val created = runBlocking { container.db.tasks().all().first { it.title == "Submit DBMS mini project" } }
        val alarms = org.robolectric.Shadows.shadowOf(rule.activity.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms
        assertTrue("expected alarms to be scheduled", alarms.isNotEmpty())
        assertTrue(created.dueAt > System.currentTimeMillis())

        // ---- Money editor ----
        tab("Money")
        waitForText("Add entry")
        click("Add entry")
        waitForText("Category")
        shot("15_money_editor")
        back()

        // ---- Global search ----
        tab("Today")
        rule.onNodeWithContentDescription("Search").performClick()
        settle()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("dbms")
        rule.mainClock.advanceTimeBy(500)
        waitForText("ER diagram")
        shot("19_search")
        back()

        // ---- Holidays ----
        tab("Schedule")
        rule.onNodeWithContentDescription("Holidays & breaks").performClick()
        waitForText("Diwali break")
        shot("20_holidays")
        back()

        // ---- Dark mode ----
        runBlocking { container.settings.update { it.copy(themeMode = ThemeMode.DARK) } }
        rule.waitForIdle()
        tab("Today")
        waitForText("Classes · ")
        shot("16_today_dark")
        tab("Money")
        waitForText("Where it went")
        shot("17_money_dark")
        tab("Tasks")
        waitForText("DUE THIS WEEK")
        shot("18_tasks_dark")
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun tab(name: String) {
        settle()
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("tab_$name")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tab_$name").performClick()
        settle()
    }

    private fun click(text: String, substring: Boolean = false) {
        settle()
        rule.onAllNodesWithText(text, substring = substring)[0].performClick()
        settle()
    }

    private fun back() {
        settle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun seed() = runBlocking {
        val c = container
        val today = LocalDate.now()
        val dow = today.dayOfWeek.value
        val ds = c.subjects.save(SubjectEntity(name = "Data Structures", code = "CS201", instructor = "Dr. Mehta", colorIndex = 0))
        val dbms = c.subjects.save(SubjectEntity(name = "DBMS", code = "CS204", instructor = "Prof. Iyer", colorIndex = 2))
        val maths = c.subjects.save(SubjectEntity(name = "Discrete Maths", code = "MA203", instructor = "Dr. Sen", colorIndex = 1))
        val phy = c.subjects.save(SubjectEntity(name = "Physics Lab", colorIndex = 4))

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        // Earlier classes today, one happening now (if the day allows), one later.
        val s1 = c.schedule.saveSlot(ClassSlotEntity(subjectId = ds, dayOfWeek = dow, startMinute = 9 * 60, endMinute = 10 * 60, room = "LT-2"))
        c.schedule.saveSlot(ClassSlotEntity(subjectId = dbms, dayOfWeek = dow, startMinute = 10 * 60 + 15, endMinute = 11 * 60 + 15, room = "LT-4"))
        val ongoingStart = (nowMin - 20).coerceIn(12 * 60, 22 * 60)
        c.schedule.saveSlot(ClassSlotEntity(subjectId = phy, dayOfWeek = dow, startMinute = ongoingStart, endMinute = ongoingStart + 90, room = "Lab 3", kind = ClassKind.LAB))
        listOf(1, 3, 5).filter { it != dow }.forEach {
            c.schedule.saveSlot(ClassSlotEntity(subjectId = maths, dayOfWeek = it, startMinute = 11 * 60, endMinute = 12 * 60, room = "LT-1"))
        }
        listOf(2, 4).filter { it != dow }.forEach {
            c.schedule.saveSlot(ClassSlotEntity(subjectId = ds, dayOfWeek = it, startMinute = 9 * 60, endMinute = 10 * 60, room = "LT-2", kind = ClassKind.TUTORIAL))
        }

        // Attendance history: DS healthy, DBMS at risk.
        repeat(18) { i -> c.attendance.addManual(ds, today.minusDays(i + 1L), if (i % 6 == 0) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT) }
        repeat(12) { i -> c.attendance.addManual(dbms, today.minusDays(i + 1L), if (i % 3 == 0) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT) }
        repeat(10) { i -> c.attendance.addManual(maths, today.minusDays(i + 1L), if (i == 4) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT) }
        c.attendance.markSlot(c.schedule.getSlot(s1)!!, today, AttendanceStatus.PRESENT)

        val zone = ZoneId.systemDefault()
        fun at(daysFromNow: Long, h: Int, m: Int = 0) = today.plusDays(daysFromNow).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        c.tasks.save(TaskEntity(title = "Linked list assignment", subjectId = ds, kind = TaskKind.ASSIGNMENT, dueAt = at(0, 23, 59), priority = Priority.HIGH),
            listOf(SubtaskEntity(taskId = 0, title = "Insert & delete", done = true), SubtaskEntity(taskId = 0, title = "Reverse list"), SubtaskEntity(taskId = 0, title = "Write report")))
        c.tasks.save(TaskEntity(title = "ER diagram for library system", subjectId = dbms, kind = TaskKind.PROJECT, dueAt = at(2, 17)))
        c.tasks.save(TaskEntity(title = "Revise graph theory", subjectId = maths, kind = TaskKind.EXAM, dueAt = at(5, 9), priority = Priority.LOW))
        c.tasks.save(TaskEntity(title = "Lab record: pendulum", subjectId = phy, kind = TaskKind.ASSIGNMENT, dueAt = at(-1, 17), priority = Priority.MEDIUM))
        c.tasks.save(TaskEntity(title = "Read chapter 4: Normalisation", subjectId = dbms, kind = TaskKind.READING, dueAt = at(12, 18)))

        c.schedule.saveEvent(EventEntity(title = "Robotics in Medicine", kind = EventKind.GUEST_LECTURE, speaker = "Dr. Kavya Nair", location = "Main Auditorium", startAt = at(3, 14), endAt = at(3, 16)))
        c.schedule.saveEvent(EventEntity(title = "DBMS Mid-sem", kind = EventKind.EXAM, location = "Exam Hall B", startAt = at(9, 10), endAt = at(9, 12), subjectId = dbms))
        c.holidays.save(HolidayEntity(title = "Diwali break", startDay = today.plusDays(6).toEpochDay(), endDay = today.plusDays(9).toEpochDay()))

        val ym = YearMonth.now()
        c.money.setBudget(ym, 6000_00)
        val d = today.dayOfMonth
        fun day(n: Int) = today.withDayOfMonth(n.coerceIn(1, d)).toEpochDay()
        listOf(
            Triple("Pocket money", 8000_00L, "pocket_money") to TxnType.INCOME,
            Triple("Hostel mess", 2400_00L, "food") to TxnType.EXPENSE,
            Triple("Metro card", 500_00L, "transport") to TxnType.EXPENSE,
            Triple("Algorithms book", 650_00L, "study") to TxnType.EXPENSE,
            Triple("Jio recharge", 299_00L, "recharge") to TxnType.EXPENSE,
            Triple("Movie", 350_00L, "fun") to TxnType.EXPENSE,
            Triple("Chai & samosa", 60_00L, "food") to TxnType.EXPENSE,
            Triple("Canteen lunch", 120_00L, "food") to TxnType.EXPENSE,
        ).forEachIndexed { i, (t, type) ->
            c.money.save(TransactionEntity(title = t.first, amountPaise = t.second, category = t.third, type = type, epochDay = day(if (i == 0) 1 else d - (i % d))))
        }
    }
}
