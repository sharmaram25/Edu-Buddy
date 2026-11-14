package com.edubuddy.app.ui.feature.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.FocusSessionEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.prefs.FocusTimerState
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.ui.common.BarChart
import com.edubuddy.app.ui.common.ColorDot
import com.edubuddy.app.ui.common.DropdownField
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.SectionHeader
import com.edubuddy.app.ui.common.StatTile
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusState(
    val timer: FocusTimerState = FocusTimerState(),
    val subjects: List<SubjectEntity> = emptyList(),
    val settings: Settings = Settings(),
    val sessions: List<FocusSessionEntity> = emptyList(),
)

class FocusViewModel(c: AppContainer) : EduViewModel(c) {
    private val weekStart = c.clock.today().minusDays(6).atStartOfDay(c.clock.zone()).toInstant().toEpochMilli()

    val state = combine(c.focus.state, c.subjects.subjects, c.settings.settings, c.db.focus().observeSince(weekStart)) { t, s, st, sessions ->
        FocusState(t, s.filter { !it.archived }, st, sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusState())

    fun start(subjectId: Long?, minutes: Int, isBreak: Boolean) = write { c.focus.start(subjectId, minutes, isBreak) }
    fun pause() = write { c.focus.pause() }
    fun resume() = write { c.focus.resume() }
    fun stop() = write { c.focus.stop() }
    fun completeIfDue() = viewModelScope.launch { c.focus.completeIfDue() }
}

@Composable
fun FocusScreen(onBack: () -> Unit) {
    val vm = eduViewModel { FocusViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val t = st.timer
    var subjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var minutes by rememberSaveable { mutableIntStateOf(0) }
    var lastWasFocus by rememberSaveable { mutableStateOf(false) }
    val planned = if (minutes == 0) st.settings.focusMinutes else minutes

    // 1-second tick only while running.
    var now by androidx.compose.runtime.remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.isRunning, t.endsAt) {
        while (t.isRunning) {
            now = System.currentTimeMillis()
            if (now >= t.endsAt) {
                lastWasFocus = !t.isBreak
                vm.completeIfDue()
                break
            }
            delay(250)
        }
    }

    val remainingMs = when {
        t.isRunning -> (t.endsAt - now).coerceAtLeast(0)
        t.isPaused -> t.pausedRemaining
        else -> planned * 60_000L
    }
    val totalMs = if (t.isIdle) planned * 60_000L else t.plannedMinutes * 60_000L
    val progress = if (totalMs == 0L) 0f else 1f - remainingMs.toFloat() / totalMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mode = when {
                t.isIdle -> "Ready"
                t.isBreak -> "Break"
                else -> "Focusing"
            }
            val activeSubject = st.subjects.firstOrNull { it.id == (if (t.isIdle) subjectId else t.subjectId) }
            val ringColor = when {
                t.isBreak -> com.edubuddy.app.ui.theme.EduTheme.extra.accent
                else -> MaterialTheme.colorScheme.onSurface
            }

            Spacer(Modifier.height(12.dp))
            TimerRing(progress, ringColor, Modifier.size(288.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mode.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (t.isBreak) ringColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    val totalSec = (remainingMs + 999) / 1000
                    Text(
                        "%02d:%02d".format(totalSec / 60, totalSec % 60),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        modifier = Modifier.semantics { contentDescription = "${totalSec / 60} minutes ${totalSec % 60} seconds left" },
                    )
                    Text(
                        activeSubject?.name ?: if (t.isBreak) "Stretch, hydrate, rest your eyes" else "General study",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            if (t.isIdle) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DropdownField(
                        label = "Studying",
                        value = st.subjects.firstOrNull { it.id == subjectId }?.name ?: "General study",
                        icon = Icons.Rounded.School,
                        options = listOf<SubjectEntity?>(null) + st.subjects,
                        optionLabel = { it?.name ?: "General study" },
                        onSelect = { subjectId = it?.id },
                        optionLeading = { s -> if (s != null) ColorDot(SubjectPalette.color(s.colorIndex), 12.dp) },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(st.settings.focusMinutes, 15, 25, 45, 60, 90).distinct().sorted().forEach { m ->
                            FilterChip(selected = planned == m, onClick = { minutes = m }, label = { Text("$m min") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.start(subjectId, planned, false) }, modifier = Modifier.weight(1f).height(56.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start focus")
                        }
                        if (lastWasFocus) {
                            FilledTonalButton(onClick = { lastWasFocus = false; vm.start(null, st.settings.breakMinutes, true) }, modifier = Modifier.height(56.dp)) {
                                Icon(Icons.Rounded.Coffee, null); Spacer(Modifier.width(8.dp)); Text("${st.settings.breakMinutes}m break")
                            }
                        }
                    }
                    Text(
                        "The timer keeps running if you leave the app. You'll get a notification when it ends.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (t.isRunning) {
                        FilledTonalButton(onClick = vm::pause, modifier = Modifier.height(56.dp).width(150.dp)) {
                            Icon(Icons.Rounded.Pause, null); Spacer(Modifier.width(8.dp)); Text("Pause")
                        }
                    } else {
                        Button(onClick = vm::resume, modifier = Modifier.height(56.dp).width(150.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Resume")
                        }
                    }
                    OutlinedButton(onClick = vm::stop, modifier = Modifier.height(56.dp).width(150.dp)) {
                        Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text(if (t.isBreak) "Skip break" else "Stop")
                    }
                }
                if (!t.isBreak) {
                    Text(
                        "Stopping early still logs the minutes you've done (5 min or more).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp),
                    )
                }
            }

            Stats(st)
        }
    }
}

@Composable
private fun TimerRing(progress: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "timer")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val tickMajor = MaterialTheme.colorScheme.outline
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Watch-face minute ticks around a thin progress ring.
            val tickOuter = size.minDimension / 2
            val c = center
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val len = (if (major) 10.dp else 5.dp).toPx()
                val a = Math.toRadians(i * 6.0 - 90.0)
                val cos = kotlin.math.cos(a).toFloat(); val sin = kotlin.math.sin(a).toFloat()
                drawLine(
                    if (major) tickMajor else track,
                    Offset(c.x + cos * (tickOuter - len), c.y + sin * (tickOuter - len)),
                    Offset(c.x + cos * tickOuter, c.y + sin * tickOuter),
                    strokeWidth = (if (major) 1.5.dp else 1.dp).toPx(),
                )
            }
            val stroke = 3.dp.toPx()
            val ringInset = 18.dp.toPx() + stroke / 2
            val inset = ringInset
            val sz = Size(size.width - ringInset * 2, size.height - ringInset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            // Remaining time drains clockwise.
            drawArc(color, -90f + 360f * animated, 360f * (1 - animated), false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun Stats(st: FocusState) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val perDay = days.map { d -> st.sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == d }.sumOf { it.minutes }.toLong() }
    val todayMin = perDay.last().toInt()
    val weekMin = perDay.sum().toInt()
    val streak = days.reversed().takeWhile { d -> st.sessions.any { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == d } }.size

    SectionHeader("Your focus")
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Today", Fmt.durationMinutes(todayMin), Modifier.weight(1f))
        StatTile("Last 7 days", Fmt.durationMinutes(weekMin), Modifier.weight(1f))
        StatTile("Day streak", streak.toString(), Modifier.weight(1f))
    }
    if (weekMin > 0) {
        Spacer(Modifier.height(10.dp))
        EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            BarChart(
                values = perDay, highlight = 6, height = 100.dp,
                labelFor = { i -> Fmt.dayShort(days[i].dayOfWeek.value).take(2) },
                description = "Focus minutes over the last 7 days",
            )
            val bySubject = st.sessions.groupBy { it.subjectId }.mapValues { (_, v) -> v.sumOf { it.minutes } }.entries.sortedByDescending { it.value }
            if (bySubject.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                bySubject.take(5).forEach { (sid, m) ->
                    val s = st.subjects.firstOrNull { it.id == sid }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(s?.let { SubjectPalette.color(it.colorIndex) } ?: MaterialTheme.colorScheme.outline, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s?.name ?: "General study", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Fmt.durationMinutes(m), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
