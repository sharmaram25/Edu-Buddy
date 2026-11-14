package com.edubuddy.app.ui.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.Money
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.TaskWithProgress
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.data.model.Categories
import com.edubuddy.app.ui.common.ColorDot
import com.edubuddy.app.ui.common.CategoryVisuals
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.Hairline
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.icon
import com.edubuddy.app.ui.navigation.EventEditRoute
import com.edubuddy.app.ui.navigation.HolidaysRoute
import com.edubuddy.app.ui.navigation.SubjectDetailRoute
import com.edubuddy.app.ui.navigation.TaskEditRoute
import com.edubuddy.app.ui.navigation.TxnEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SearchResults(
    val query: String = "",
    val tasks: List<TaskWithProgress> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val holidays: List<HolidayEntity> = emptyList(),
    val money: List<TransactionEntity> = emptyList(),
    val subjectById: Map<Long, SubjectEntity> = emptyMap(),
) {
    val isEmpty get() = tasks.isEmpty() && subjects.isEmpty() && events.isEmpty() && holidays.isEmpty() && money.isEmpty()
}

@OptIn(FlowPreview::class)
class SearchViewModel(c: AppContainer) : EduViewModel(c) {
    val query = MutableStateFlow("")

    private data class Corpus(
        val tasks: List<TaskWithProgress>,
        val subjects: List<SubjectEntity>,
        val events: List<EventEntity>,
        val holidays: List<HolidayEntity>,
        val money: List<TransactionEntity>,
    )

    // Everything a student keeps is small (hundreds of rows), so we filter in memory: no FTS
    // tables to migrate, instant results, and edits elsewhere show up live.
    private val corpus = combine(
        c.tasks.tasks, c.subjects.subjects, c.schedule.events, c.holidays.holidays,
        c.db.money().observeBetween(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2),
    ) { t, s, e, h, m -> Corpus(t, s, e, h, m) }

    val results = combine(query.debounce(120), corpus) { raw, cp ->
        val q = raw.trim()
        if (q.isEmpty()) return@combine SearchResults(subjectById = cp.subjects.associateBy { it.id })
        fun String.hit() = contains(q, ignoreCase = true)
        val subjectById = cp.subjects.associateBy { it.id }
        val matchingSubjectIds = cp.subjects.filter { it.name.hit() || it.code.hit() }.map { it.id }.toSet()
        SearchResults(
            query = q,
            tasks = cp.tasks.filter { it.title.hit() || it.notes.hit() || it.subjectId in matchingSubjectIds }
                .sortedWith(compareBy({ it.completedAt != null }, { it.dueAt })).take(25),
            subjects = cp.subjects.filter { it.name.hit() || it.code.hit() || it.instructor.hit() }.take(10),
            events = cp.events.filter { it.title.hit() || it.speaker.hit() || it.location.hit() || it.notes.hit() || it.kind.label.hit() }
                .sortedByDescending { it.startAt }.take(15),
            holidays = cp.holidays.filter { it.title.hit() }.take(10),
            money = cp.money.filter { it.title.hit() || it.note.hit() || Categories.of(it.category).label.hit() }.take(25),
            subjectById = subjectById,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}

@Composable
fun SearchScreen(nav: NavHostController) {
    val vm = eduViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val r by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Row(
                Modifier.weight(1f).height(48.dp).padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search EduBuddy", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { vm.query.value = it.take(80) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(EduTheme.extra.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                }
            }
        }
        Hairline()

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            when {
                query.isBlank() -> item {
                    EmptyState(Icons.Rounded.Search, "Search everything", "Tasks, subjects, teachers, events, holidays and expenses.")
                }
                r.query.isNotEmpty() && r.isEmpty -> item {
                    EmptyState(Icons.Rounded.Search, "No results for “${r.query}”", "Check the spelling or try a shorter word.")
                }
            }
            if (r.tasks.isNotEmpty()) {
                item { GroupLabel("Tasks") }
                items(r.tasks, key = { "t${it.id}" }) { t ->
                    val due = Instant.ofEpochMilli(t.dueAt).atZone(zone)
                    val subject = t.subjectId?.let { r.subjectById[it] }
                    ResultRow(
                        title = highlight(t.title, r.query),
                        subtitle = listOfNotNull(
                            subject?.name,
                            if (t.completedAt != null) "Completed" else "Due " + Fmt.relativeDay(due.toLocalDate(), today).let { d -> if (d == "Today" || d == "Tomorrow" || d == "Yesterday") d.lowercase() else d },
                        ).joinToString(" · "),
                        leading = { Icon(t.kind.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { nav.navigate(TaskEditRoute(id = t.id)) },
                    )
                }
            }
            if (r.subjects.isNotEmpty()) {
                item { GroupLabel("Subjects") }
                items(r.subjects, key = { "s${it.id}" }) { s ->
                    ResultRow(
                        title = highlight(s.name, r.query),
                        subtitle = listOf(s.code, s.instructor).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { if (s.archived) "Archived" else "Subject" },
                        leading = { ColorDot(SubjectPalette.color(s.colorIndex), 12.dp) },
                        onClick = { nav.navigate(SubjectDetailRoute(s.id)) },
                    )
                }
            }
            if (r.events.isNotEmpty()) {
                item { GroupLabel("Events") }
                items(r.events, key = { "e${it.id}" }) { e ->
                    val start = Instant.ofEpochMilli(e.startAt).atZone(zone).toLocalDate()
                    ResultRow(
                        title = highlight(e.title, r.query),
                        subtitle = listOfNotNull(e.kind.label, Fmt.dateShort(start), e.location.takeIf { it.isNotBlank() }).joinToString(" · "),
                        leading = { Icon(e.kind.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { nav.navigate(EventEditRoute(e.id)) },
                    )
                }
            }
            if (r.holidays.isNotEmpty()) {
                item { GroupLabel("Holidays & breaks") }
                items(r.holidays, key = { "h${it.id}" }) { h ->
                    ResultRow(
                        title = highlight(h.title, r.query),
                        subtitle = Fmt.dateShort(LocalDate.ofEpochDay(h.startDay)) +
                            if (h.endDay != h.startDay) " – " + Fmt.dateShort(LocalDate.ofEpochDay(h.endDay)) else "",
                        leading = { Icon(Icons.Outlined.BeachAccess, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { nav.navigate(HolidaysRoute) },
                    )
                }
            }
            if (r.money.isNotEmpty()) {
                item { GroupLabel("Money") }
                items(r.money, key = { "m${it.id}" }) { m ->
                    val v = CategoryVisuals.of(m.category)
                    ResultRow(
                        title = highlight(m.title, r.query),
                        subtitle = "${Categories.of(m.category).label} · ${Fmt.dateShort(LocalDate.ofEpochDay(m.epochDay))}",
                        leading = { Icon(v.icon, null, Modifier.size(20.dp), tint = v.color) },
                        trailing = (if (m.type == TxnType.EXPENSE) "−" else "+") + Money.format(m.amountPaise),
                        onClick = { nav.navigate(TxnEditRoute(id = m.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun highlight(text: String, q: String): AnnotatedString {
    if (q.isBlank()) return AnnotatedString(text)
    val accent = MaterialTheme.colorScheme.onSurface
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val hit = text.indexOf(q, i, ignoreCase = true)
            if (hit < 0) { append(text.substring(i)); break }
            append(text.substring(i, hit))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(text.substring(hit, hit + q.length)) }
            i = hit + q.length
        }
    }
}

@Composable
private fun ResultRow(
    title: AnnotatedString,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge)
        }
    }
}

