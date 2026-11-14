package com.edubuddy.app.ui.feature.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.AttendanceMath
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.Money
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.TaskWithProgress
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.domain.AgendaMath
import com.edubuddy.app.domain.ClassOccurrence
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.Hairline
import com.edubuddy.app.ui.common.HeroCard
import com.edubuddy.app.ui.common.IconBadge
import com.edubuddy.app.ui.common.InlineEmpty
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.SectionHeader
import com.edubuddy.app.ui.common.StatTile
import com.edubuddy.app.ui.common.ThinProgress
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.rememberHaptics
import com.edubuddy.app.ui.feature.tasks.TaskRow
import com.edubuddy.app.ui.navigation.ClassEditRoute
import com.edubuddy.app.ui.navigation.EventEditRoute
import com.edubuddy.app.ui.navigation.FocusRoute
import com.edubuddy.app.ui.navigation.HolidaysRoute
import com.edubuddy.app.ui.navigation.MoneyRoute
import com.edubuddy.app.ui.navigation.ScheduleRoute
import com.edubuddy.app.ui.navigation.SearchRoute
import com.edubuddy.app.ui.navigation.SubjectsRoute
import com.edubuddy.app.ui.navigation.TaskEditRoute
import com.edubuddy.app.ui.navigation.TasksRoute
import com.edubuddy.app.ui.navigation.TxnEditRoute
import com.edubuddy.app.ui.navigation.switchTab
import com.edubuddy.app.ui.theme.EduTheme
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ---- State -------------------------------------------------------------------------------------

data class NextUp(val occurrence: ClassOccurrence, val ongoing: Boolean, val isTomorrow: Boolean)

data class TodayState(
    val loading: Boolean = true,
    val now: Instant = Instant.now(),
    val today: LocalDate = LocalDate.now(),
    val settings: Settings = Settings(),
    val classes: List<ClassOccurrence> = emptyList(),
    val nextUp: NextUp? = null,
    val holiday: HolidayEntity? = null,
    val dueSoon: List<TaskWithProgress> = emptyList(),
    val overdueCount: Int = 0,
    val dueTodayCount: Int = 0,
    val subjects: Map<Long, SubjectEntity> = emptyMap(),
    val events: List<EventEntity> = emptyList(),
    val spentPaise: Long = 0,
    val budgetPaise: Long = 0,
    val attendancePercent: Double? = null,
    val subjectsAtRisk: Int = 0,
    val hasSlots: Boolean = false,
    val focusMinutesToday: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(c: AppContainer) : EduViewModel(c) {

    private val ticker = flow {
        while (true) {
            emit(c.clock.now())
            delay(30_000)
        }
    }
    private val dayFlow = ticker.map { c.clock.today() }.distinctUntilChanged()

    private val core = combine(c.settings.settings, c.subjects.subjects, c.schedule.slots, c.holidays.holidays, ticker) { s, subjects, slots, holidays, now ->
        Core(s, subjects.associateBy { it.id }, slots, holidays, now)
    }

    private val marksToday = dayFlow.flatMapLatest { day -> c.attendance.forDay(day) }

    private val monthMoney = dayFlow.flatMapLatest { day ->
        val ym = YearMonth.from(day)
        combine(c.money.monthTransactions(ym), c.money.effectiveBudget(ym)) { txns, budget ->
            txns.filter { it.type == com.edubuddy.app.data.db.TxnType.EXPENSE }.sumOf { it.amountPaise } to (budget?.amountPaise ?: 0L)
        }
    }

    private val focusToday = dayFlow.flatMapLatest { day ->
        val from = day.atStartOfDay(c.clock.zone()).toInstant().toEpochMilli()
        c.db.focus().observeSince(from).map { list -> list.sumOf { it.minutes } }
    }

    private val extras = combine(c.tasks.tasks, c.schedule.events, c.attendance.counts, monthMoney, focusToday) { tasks, events, counts, money, focus ->
        Extras(tasks, events, counts, money, focus)
    }

    val state: StateFlow<TodayState> = combine(core, marksToday, extras) { core, marks, ex ->
        val s = core.settings
        val subjects = core.subjects
        val slots = core.slots
        val now = core.now
        val today = now.atZone(c.clock.zone()).toLocalDate()
        val holiday = AgendaMath.holidayOn(today, core.holidays)
        val markMap = marks.filter { it.slotId != null }.associate { it.slotId!! to it.status }
        val classes = if (holiday != null) emptyList() else AgendaMath.classesOn(today, slots, subjects, markMap, c.clock)
        val tomorrow = today.plusDays(1)
        val tomorrowClasses = if (AgendaMath.holidayOn(tomorrow, core.holidays) != null) emptyList()
        else AgendaMath.classesOn(tomorrow, slots, subjects, emptyMap(), c.clock)

        val ongoing = classes.firstOrNull { it.isOngoing(now) }
        val next = ongoing?.let { NextUp(it, true, false) }
            ?: classes.firstOrNull { now.isBefore(it.start.toInstant()) }?.let { NextUp(it, false, false) }
            ?: tomorrowClasses.firstOrNull()?.let { NextUp(it, false, true) }

        val nowMs = now.toEpochMilli()
        val endOfToday = tomorrow.atStartOfDay(c.clock.zone()).toInstant().toEpochMilli()
        val weekAhead = nowMs + 7L * 24 * 3600 * 1000
        val pending = ex.tasks.filter { it.completedAt == null }
        val overdue = pending.count { it.dueAt < nowMs }
        val dueToday = pending.count { it.dueAt in nowMs until endOfToday }
        val dueSoon = pending.filter { it.dueAt < weekAhead }.sortedBy { it.dueAt }.take(5)

        val horizon = nowMs + 14L * 24 * 3600 * 1000
        val events = ex.events.filter { (it.endAt ?: it.startAt) >= nowMs && it.startAt < horizon }.take(3)

        val activeCounts = ex.counts.filter { subjects[it.subjectId]?.archived == false }
        val present = activeCounts.sumOf { it.present }
        val total = activeCounts.sumOf { it.present + it.absent }
        val atRisk = activeCounts.count { cnt ->
            val t = (subjects[cnt.subjectId]?.attendanceTarget ?: s.attendanceTarget) / 100.0
            !AttendanceMath.Summary(cnt.present, cnt.present + cnt.absent, t).onTrack
        }

        TodayState(
            loading = false, now = now, today = today, settings = s, classes = classes, nextUp = next, holiday = holiday,
            dueSoon = dueSoon, overdueCount = overdue, dueTodayCount = dueToday, subjects = subjects,
            events = events, spentPaise = ex.money.first, budgetPaise = ex.money.second,
            attendancePercent = if (total > 0) present * 100.0 / total else null,
            subjectsAtRisk = atRisk, hasSlots = slots.isNotEmpty(), focusMinutesToday = ex.focus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun mark(o: ClassOccurrence, status: AttendanceStatus?) = write { c.attendance.markSlot(o.slot, o.date, status) }
    fun setCompleted(id: Long, done: Boolean) = write { c.tasks.setCompleted(id, done) }

    private data class Core(
        val settings: Settings,
        val subjects: Map<Long, SubjectEntity>,
        val slots: List<com.edubuddy.app.data.db.ClassSlotEntity>,
        val holidays: List<HolidayEntity>,
        val now: Instant,
    )
    private data class Extras(
        val tasks: List<TaskWithProgress>,
        val events: List<EventEntity>,
        val counts: List<com.edubuddy.app.data.db.SubjectAttendanceCount>,
        val money: Pair<Long, Long>,
        val focus: Int,
    )
}

// ---- UI ----------------------------------------------------------------------------------------

@Composable
fun TodayScreen(nav: NavHostController) {
    val vm = eduViewModel { TodayViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var quickAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars)) }
            item { Header(st, onSearch = { nav.navigate(SearchRoute) }) }
            item { Hero(st, onOpenSchedule = { nav.switchTab(ScheduleRoute) }, onOpenHolidays = { nav.navigate(HolidaysRoute) }) }
            item {
                val ex = EduTheme.extra
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(
                        label = "Due today",
                        value = st.dueTodayCount.toString(),
                        accent = ex.danger,
                        caption = if (st.overdueCount > 0) "${st.overdueCount} overdue" else null,
                        modifier = Modifier.weight(1f),
                        onClick = { nav.switchTab(TasksRoute) },
                    )
                    StatTile(
                        label = "Attendance",
                        value = st.attendancePercent?.let { "${it.toInt()}%" } ?: "—",
                        accent = ex.warning,
                        caption = if (st.subjectsAtRisk > 0) "${st.subjectsAtRisk} below target" else null,
                        modifier = Modifier.weight(1f),
                        onClick = { nav.navigate(SubjectsRoute) },
                    )
                    val over = st.budgetPaise > 0 && st.spentPaise > st.budgetPaise
                    StatTile(
                        label = if (st.budgetPaise > 0) "Budget left" else "Spent",
                        value = if (st.budgetPaise > 0) Money.formatCompact(st.budgetPaise - st.spentPaise) else Money.formatCompact(st.spentPaise),
                        accent = ex.danger,
                        caption = if (over) "Over budget" else null,
                        modifier = Modifier.weight(1f),
                        onClick = { nav.switchTab(MoneyRoute) },
                    )
                }
            }

            // Classes
            item {
                SectionHeader(
                    if (st.classes.isEmpty()) "Classes" else "Classes · ${st.classes.size}",
                    actionLabel = "Timetable",
                    onAction = { nav.switchTab(ScheduleRoute) },
                )
            }
            when {
                !st.loading && !st.hasSlots -> item {
                    SetupRow(
                        icon = Icons.Rounded.School,
                        title = "Set up your timetable",
                        subtitle = "Add your weekly classes once for reminders and one-tap attendance.",
                        onClick = { nav.navigate(ClassEditRoute()) },
                    )
                }
                st.holiday != null -> item { InlineEmpty("${st.holiday!!.title}. No classes today.") }
                st.classes.isEmpty() -> item { InlineEmpty("No classes today.") }
                else -> item {
                    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter).animateContentSize(), contentPadding = PaddingValues(0.dp)) {
                        st.classes.forEachIndexed { i, o ->
                            if (i > 0) Hairline(start = 16.dp)
                            ClassAttendanceRow(
                                o = o,
                                now = st.now,
                                use24h = st.settings.use24h,
                                onMark = { status ->
                                    val previous = o.mark
                                    vm.mark(o, status)
                                    if (status != null) {
                                        val word = when (status) {
                                            AttendanceStatus.PRESENT -> "present"
                                            AttendanceStatus.ABSENT -> "absent"
                                            AttendanceStatus.CANCELLED -> "cancelled"
                                        }
                                        messenger.undo("${o.subject.name} marked $word") { vm.mark(o, previous) }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Tasks
            item { SectionHeader("Due soon", actionLabel = "All tasks", onAction = { nav.switchTab(TasksRoute) }) }
            if (st.dueSoon.isEmpty()) {
                item { InlineEmpty("Nothing due this week.", actionLabel = "Add task", onAction = { nav.navigate(TaskEditRoute()) }) }
            } else {
                item {
                    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter).animateContentSize(), contentPadding = PaddingValues(vertical = 2.dp)) {
                        st.dueSoon.forEachIndexed { i, t ->
                            if (i > 0) Hairline(start = 60.dp)
                            TaskRow(
                                task = t,
                                subject = t.subjectId?.let { st.subjects[it] },
                                now = st.now,
                                today = st.today,
                                use24h = st.settings.use24h,
                                onToggle = {
                                    vm.setCompleted(t.id, true)
                                    messenger.undo("Completed “${t.title}”") { vm.setCompleted(t.id, false) }
                                },
                                onClick = { nav.navigate(TaskEditRoute(id = t.id)) },
                            )
                        }
                    }
                }
            }

            // Events
            if (st.events.isNotEmpty()) {
                item { SectionHeader("Coming up", actionLabel = "Schedule", onAction = { nav.switchTab(ScheduleRoute) }) }
                items(st.events, key = { "e" + it.id }) { e ->
                    com.edubuddy.app.ui.feature.schedule.EventCard(
                        e = e, today = st.today, use24h = st.settings.use24h,
                        onClick = { nav.navigate(EventEditRoute(e.id)) },
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                        subject = e.subjectId?.let { st.subjects[it] },
                    )
                }
            }

            // Focus
            item {
                SectionHeader("Focus")
                SetupRow(
                    icon = Icons.Rounded.Timer,
                    title = if (st.focusMinutesToday > 0) "${Fmt.durationMinutes(st.focusMinutesToday)} focused today" else "Start a focus session",
                    subtitle = "${st.settings.focusMinutes}-minute sessions, tracked per subject",
                    onClick = { nav.navigate(FocusRoute) },
                )
            }
        }

        com.edubuddy.app.ui.common.EduFab(
            onClick = { quickAdd = true },
            icon = Icons.Rounded.Add,
            text = "Add",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (quickAdd) {
        ModalBottomSheet(onDismissRequest = { quickAdd = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp))
                QuickAction(Icons.Rounded.TaskAlt, "Task or assignment", "With a deadline reminder") { quickAdd = false; nav.navigate(TaskEditRoute()) }
                QuickAction(Icons.Rounded.Payments, "Expense", "Track against your monthly budget") { quickAdd = false; nav.navigate(TxnEditRoute()) }
                QuickAction(Icons.Rounded.School, "Class", "A weekly slot in your timetable") { quickAdd = false; nav.navigate(ClassEditRoute()) }
                QuickAction(Icons.Rounded.Event, "Event or exam", "Seminars, guest lectures, exam dates") { quickAdd = false; nav.navigate(EventEditRoute()) }
                QuickAction(Icons.Outlined.BeachAccess, "Holiday or break", "Pause class reminders for a few days") { quickAdd = false; nav.navigate(HolidaysRoute) }
            }
        }
    }
}

@Composable
private fun Header(st: TodayState, onSearch: () -> Unit) {
    val hour = st.now.atZone(java.time.ZoneId.systemDefault()).hour
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val name = st.settings.name.substringBefore(' ').ifBlank { null }
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.dateLong(st.today).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(if (name != null) "$part, $name" else part, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = onSearch,
            modifier = Modifier.size(44.dp).border(1.dp, EduTheme.extra.hairline, CircleShape),
        ) { Icon(Icons.Rounded.Search, "Search", Modifier.size(20.dp)) }
    }
}

@Composable
private fun Hero(st: TodayState, onOpenSchedule: () -> Unit, onOpenHolidays: () -> Unit) {
    val ex = EduTheme.extra
    val n = st.nextUp
    HeroCard(
        Modifier.fillMaxWidth().padding(horizontal = Gutter)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = if (st.holiday != null && n?.isTomorrow != false) onOpenHolidays else onOpenSchedule),
    ) {
        if (st.holiday != null && (n == null || n.isTomorrow)) {
            Status("HOLIDAY", dotColor = ex.accent)
            Spacer(Modifier.height(10.dp))
            Text(st.holiday.title, style = MaterialTheme.typography.headlineSmall, color = ex.onHero)
            Text(
                if (n != null) "Back tomorrow with ${n.occurrence.subject.name} at ${Fmt.time(n.occurrence.slot.startMinute, st.settings.use24h)}"
                else "No classes and no class reminders today",
                style = MaterialTheme.typography.bodyMedium, color = ex.onHeroMuted,
            )
            return@HeroCard
        }
        if (n == null) {
            Status("NEXT UP", dotColor = ex.onHeroMuted)
            Spacer(Modifier.height(10.dp))
            Text(
                if (st.hasSlots) "You're done for today" else "No timetable yet",
                style = MaterialTheme.typography.headlineSmall, color = ex.onHero,
            )
            Text(
                if (st.hasSlots) "No more classes. Tomorrow's first class will show here." else "Add your weekly classes to see what's next.",
                style = MaterialTheme.typography.bodyMedium, color = ex.onHeroMuted,
            )
            return@HeroCard
        }
        val o = n.occurrence
        val label = when {
            n.ongoing -> "NOW"
            n.isTomorrow -> "TOMORROW"
            else -> "NEXT · " + Fmt.countdown(st.now, o.start.toInstant()).uppercase()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Status(label, dotColor = if (n.ongoing) ex.accent else ex.onHeroMuted, modifier = Modifier.weight(1f))
            Text(
                "${Fmt.time(o.slot.startMinute, st.settings.use24h)} – ${Fmt.time(o.slot.endMinute, st.settings.use24h)}",
                style = MaterialTheme.typography.labelLarge, color = ex.onHeroMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(SubjectPalette.color(o.subject.colorIndex)))
            Spacer(Modifier.width(10.dp))
            Text(o.subject.name, style = MaterialTheme.typography.headlineSmall, color = ex.onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val meta = listOfNotNull(o.slot.kind.label, o.slot.room.takeIf { it.isNotBlank() }, o.subject.instructor.takeIf { it.isNotBlank() }).joinToString("  ·  ")
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = ex.onHeroMuted, modifier = Modifier.padding(start = 20.dp))
        if (n.ongoing) {
            val total = ChronoUnit.SECONDS.between(o.start, o.end).coerceAtLeast(1)
            val done = ChronoUnit.SECONDS.between(o.start.toInstant(), st.now).coerceIn(0, total)
            Spacer(Modifier.height(16.dp))
            ThinProgress(done.toFloat() / total, ex.accent, height = 4.dp, trackColor = ex.heroTrack)
            Spacer(Modifier.height(6.dp))
            Text("Ends ${Fmt.countdown(st.now, o.end.toInstant())}", style = MaterialTheme.typography.labelMedium, color = ex.onHeroMuted)
        }
    }
}

@Composable
private fun Status(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = EduTheme.extra.onHeroMuted)
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClassAttendanceRow(
    o: ClassOccurrence,
    now: Instant,
    use24h: Boolean,
    onMark: (AttendanceStatus?) -> Unit,
) {
    val color = SubjectPalette.color(o.subject.colorIndex)
    val started = !now.isBefore(o.start.toInstant())
    val ongoing = o.isOngoing(now)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 12.dp).padding(vertical = 14.dp).width(3.dp).fillMaxHeight().clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(76.dp).padding(vertical = 12.dp)) {
            Text(Fmt.time(o.slot.startMinute, use24h), style = MaterialTheme.typography.labelLarge)
            Text(Fmt.time(o.slot.endMinute, use24h), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.subject.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (ongoing) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(EduTheme.extra.accent))
                }
            }
            val meta = listOfNotNull(o.slot.kind.label, o.slot.room.takeIf { it.isNotBlank() }).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Attendance controls appear once the class has started (or if already marked).
        if (started || o.mark != null) AttendanceButtons(o.mark, onMark)
    }
}

/** Present / absent / cancelled toggles. Tapping the selected one clears it. */
@Composable
fun AttendanceButtons(mark: AttendanceStatus?, onMark: (AttendanceStatus?) -> Unit) {
    val ex = EduTheme.extra
    val haptics = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(AttendanceStatus.PRESENT, Icons.Rounded.Check, ex.success),
            Triple(AttendanceStatus.ABSENT, Icons.Rounded.Close, ex.danger),
            Triple(AttendanceStatus.CANCELLED, Icons.Rounded.Remove, MaterialTheme.colorScheme.outline),
        ).forEach { (status, icon, color) ->
            val selected = mark == status
            val label = when (status) {
                AttendanceStatus.PRESENT -> "Present"
                AttendanceStatus.ABSENT -> "Absent"
                AttendanceStatus.CANCELLED -> "Cancelled"
            }
            MarkButton(icon, label, color, selected, dim = mark != null && !selected) {
                if (selected) haptics.tick() else haptics.confirm()
                onMark(if (selected) null else status)
            }
        }
    }
}

@Composable
private fun MarkButton(icon: ImageVector, label: String, color: Color, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color else Color.Transparent, label = "markBg")
    val border = if (selected) color else EduTheme.extra.hairline
    val tint = when {
        selected -> Color.White
        dim -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}
