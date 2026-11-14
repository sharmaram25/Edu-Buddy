package com.edubuddy.app.ui.feature.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.EventKind
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.domain.AgendaMath
import com.edubuddy.app.ui.common.ColorDot
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.PageHeader
import com.edubuddy.app.ui.common.UnderlineTabs
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.icon
import com.edubuddy.app.ui.navigation.ClassEditRoute
import com.edubuddy.app.ui.navigation.EventEditRoute
import com.edubuddy.app.ui.navigation.HolidaysRoute
import com.edubuddy.app.ui.theme.EduTheme
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleState(
    val slots: List<ClassSlotEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val subjects: Map<Long, SubjectEntity> = emptyMap(),
    val settings: Settings = Settings(),
    val holidays: List<HolidayEntity> = emptyList(),
    val loading: Boolean = true,
)

class ScheduleViewModel(c: AppContainer) : EduViewModel(c) {
    val state = combine(c.schedule.slots, c.schedule.events, c.subjects.subjects, c.settings.settings, c.holidays.holidays) { sl, ev, su, st, ho ->
        ScheduleState(sl, ev, su.associateBy { it.id }, st, ho, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleState())
}

private enum class ScheduleTab(val label: String) { TIMETABLE("Timetable"), EVENTS("Events") }

@Composable
fun ScheduleScreen(nav: NavHostController) {
    val vm = eduViewModel { ScheduleViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ScheduleTab.TIMETABLE) }
    val today = LocalDate.now()
    var day by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            PageHeader(
                "Schedule",
                subtitle = when (tab) {
                    ScheduleTab.TIMETABLE -> {
                        val active = st.slots.filter { st.subjects[it.subjectId]?.archived == false }
                        if (active.isEmpty()) "Your week" else {
                            val mins = active.sumOf { it.endMinute - it.startMinute }
                            "${active.size} classes · ${Fmt.durationMinutes(mins)} a week"
                        }
                    }
                    ScheduleTab.EVENTS -> "Seminars, lectures & exams"
                },
            ) {
                IconButton(onClick = { nav.navigate(HolidaysRoute) }) {
                    Icon(Icons.Outlined.BeachAccess, "Holidays & breaks")
                }
            }
            UnderlineTabs(ScheduleTab.entries, tab, { it.label }, { tab = it })
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "scheduleTab") { t ->
                when (t) {
                    ScheduleTab.TIMETABLE -> Timetable(st, day, { day = it }, today, nav)
                    ScheduleTab.EVENTS -> EventsList(st, today, nav)
                }
            }
        }
        com.edubuddy.app.ui.common.EduFab(
            onClick = {
                if (tab == ScheduleTab.TIMETABLE) nav.navigate(ClassEditRoute(day = day)) else nav.navigate(EventEditRoute())
            },
            icon = Icons.Rounded.Add,
            text = if (tab == ScheduleTab.TIMETABLE) "Add class" else "Add event",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun Timetable(st: ScheduleState, day: Int, onDay: (Int) -> Unit, today: LocalDate, nav: NavHostController) {
    val active = st.slots.filter { st.subjects[it.subjectId]?.archived == false }
    val days = buildList {
        addAll(1..5)
        if (st.settings.showSaturday || active.any { it.dayOfWeek == 6 }) add(6)
        if (active.any { it.dayOfWeek == 7 } || day == 7) add(7)
    }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val ex = EduTheme.extra

    Column(Modifier.fillMaxSize()) {
        // Week strip: selected day in ink, today marked with the ember dot, breaks dimmed.
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            days.forEach { d ->
                val date = monday.plusDays((d - 1).toLong())
                val count = active.count { it.dayOfWeek == d }
                val sel = d == day
                val isToday = date == today
                val off = AgendaMath.holidayOn(date, st.holidays) != null
                val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, label = "day")
                val fg = when {
                    sel -> MaterialTheme.colorScheme.onPrimary
                    off -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable { onDay(d) }
                        .padding(vertical = 10.dp)
                        .semantics {
                            selected = sel
                            contentDescription = "${Fmt.dayFull(d)}, " + if (off) "holiday" else "$count classes"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Fmt.dayShort(d).take(3), style = MaterialTheme.typography.labelMedium, color = if (sel) fg.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.size(5.dp).clip(CircleShape).background(
                            when {
                                isToday -> ex.accent
                                count > 0 && !off -> fg.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            }
                        )
                    )
                }
            }
        }

        val selectedDate = monday.plusDays((day - 1).toLong())
        val holiday = AgendaMath.holidayOn(selectedDate, st.holidays)
        val dayClasses = active.filter { it.dayOfWeek == day }.sortedBy { it.startMinute }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (holiday != null) {
                item(key = "holiday") { HolidayBanner(holiday, onClick = { nav.navigate(HolidaysRoute) }) }
            }
            if (!st.loading && dayClasses.isEmpty()) {
                item {
                    if (active.isEmpty()) {
                        EmptyState(
                            Icons.Rounded.CalendarMonth, "Build your timetable",
                            "Add each weekly class once. You can put a class on several days at the same time.",
                            actionLabel = "Add first class", onAction = { nav.navigate(ClassEditRoute(day = day)) },
                        )
                    } else if (holiday == null) {
                        EmptyState(Icons.Rounded.CalendarMonth, "Nothing on ${Fmt.dayFull(day)}", "A free day. Add a class if that changes.")
                    }
                }
            }
            var prevEnd = -1
            dayClasses.forEach { slot ->
                val gap = if (prevEnd >= 0) slot.startMinute - prevEnd else 0
                val captured = prevEnd
                if (gap >= 30) {
                    item(key = "gap${slot.id}") { FreeGap(gap) }
                }
                prevEnd = maxOf(captured, slot.endMinute)
                item(key = slot.id) {
                    val subject = st.subjects[slot.subjectId] ?: return@item
                    ClassRow(
                        slot, subject, st.settings.use24h, dim = holiday != null,
                        onClick = { nav.navigate(ClassEditRoute(id = slot.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
fun HolidayBanner(h: HolidayEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val range = if (h.startDay == h.endDay) Fmt.dateShort(LocalDate.ofEpochDay(h.startDay))
    else "${Fmt.dateShort(LocalDate.ofEpochDay(h.startDay))} – ${Fmt.dateShort(LocalDate.ofEpochDay(h.endDay))}"
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.BeachAccess, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.titleSmall)
            Text("$range · no classes, reminders paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FreeGap(minutes: Int) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter + 78.dp, end = Gutter, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(18.dp).background(EduTheme.extra.hairline))
        Spacer(Modifier.width(14.dp))
        Text("${Fmt.durationMinutes(minutes)} free", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClassRow(slot: ClassSlotEntity, subject: SubjectEntity, use24h: Boolean, dim: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = SubjectPalette.color(subject.colorIndex)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).height(IntrinsicSize.Min)) {
        Column(Modifier.width(78.dp).padding(top = 14.dp)) {
            Text(Fmt.time(slot.startMinute, use24h), style = MaterialTheme.typography.labelLarge, color = if (dim) muted else MaterialTheme.colorScheme.onSurface)
            Text(Fmt.time(slot.endMinute, use24h), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        EduCard(Modifier.weight(1f), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(if (dim) color.copy(alpha = 0.3f) else color))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subject.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f), color = if (dim) muted else MaterialTheme.colorScheme.onSurface,
                        )
                        if (slot.reminderMinutes < 0) Icon(Icons.Rounded.NotificationsOff, "No reminder", Modifier.size(15.dp), tint = muted)
                    }
                    Spacer(Modifier.height(2.dp))
                    val meta = listOfNotNull(
                        slot.kind.label,
                        Fmt.durationMinutes(slot.endMinute - slot.startMinute),
                        slot.room.takeIf { it.isNotBlank() },
                        subject.instructor.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EventsList(st: ScheduleState, today: LocalDate, nav: NavHostController) {
    val nowMs = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val upcoming = st.events.filter { (it.endAt ?: it.startAt) >= nowMs }.sortedBy { it.startAt }
    val past = st.events.filter { (it.endAt ?: it.startAt) < nowMs }.sortedByDescending { it.startAt }
    var showPast by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
        if (!st.loading && upcoming.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Event, "No upcoming events",
                    "Add seminars, guest lectures, workshops and exam dates. You'll be reminded before each one.",
                    actionLabel = "Add event", onAction = { nav.navigate(EventEditRoute()) },
                )
            }
        }
        val byMonth = upcoming.groupBy { java.time.YearMonth.from(Instant.ofEpochMilli(it.startAt).atZone(zone)) }
        byMonth.forEach { (ym, list) ->
            item(key = "m$ym") { GroupLabel(Fmt.month(ym)) }
            items(list, key = { it.id }) { e ->
                EventCard(e, today, st.settings.use24h, onClick = { nav.navigate(EventEditRoute(e.id)) },
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp).animateItem(),
                    subject = e.subjectId?.let { st.subjects[it] })
            }
        }
        if (past.isNotEmpty()) {
            item {
                TextButton(onClick = { showPast = !showPast }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (showPast) "Hide past events" else "Past events (${past.size})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (showPast) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showPast) {
                items(past, key = { "p" + it.id }) { e ->
                    EventCard(e, today, st.settings.use24h, onClick = { nav.navigate(EventEditRoute(e.id)) },
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp), dim = true)
                }
            }
        }
    }
}

/** Event card with a calendar-leaf date block. Shared with Today and Search. */
@Composable
fun EventCard(
    e: EventEntity,
    today: LocalDate,
    use24h: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subject: SubjectEntity? = null,
    dim: Boolean = false,
) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(e.startAt).atZone(zone)
    val end = e.endAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val kindColor = if (e.kind == EventKind.EXAM && !dim) EduTheme.extra.danger else muted
    EduCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, EduTheme.extra.hairline, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Text(
                        start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
                        style = MaterialTheme.typography.labelSmall, color = muted,
                    )
                }
                Text(
                    start.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge,
                    color = if (dim) muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(e.kind.icon(), null, Modifier.size(13.dp), tint = kindColor)
                    Spacer(Modifier.width(4.dp))
                    Text(e.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = kindColor)
                    if (subject != null) {
                        Spacer(Modifier.width(8.dp))
                        ColorDot(SubjectPalette.color(subject.colorIndex), 6.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(subject.name, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (dim) muted else MaterialTheme.colorScheme.onSurface,
                )
                val timeText = Fmt.relativeDay(start.toLocalDate(), today) + " · " + Fmt.time(start.toLocalTime(), use24h) +
                    (end?.let { " – " + Fmt.time(it.toLocalTime(), use24h) } ?: "")
                val meta = listOfNotNull(timeText, e.location.takeIf { it.isNotBlank() }, e.speaker.takeIf { it.isNotBlank() }).joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
