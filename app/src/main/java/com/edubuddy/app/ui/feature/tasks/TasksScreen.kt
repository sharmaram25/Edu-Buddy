package com.edubuddy.app.ui.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.Priority
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.TaskWithProgress
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.data.repo.TaskRepository
import com.edubuddy.app.ui.common.ColorDot
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.PageHeader
import com.edubuddy.app.ui.common.Segmented
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.navigation.TaskEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TasksState(
    val tasks: List<TaskWithProgress> = emptyList(),
    val subjects: Map<Long, SubjectEntity> = emptyMap(),
    val settings: Settings = Settings(),
    val now: Instant = Instant.now(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
)

class TasksViewModel(c: AppContainer) : EduViewModel(c) {
    private val ticker = flow { while (true) { emit(c.clock.now()); delay(60_000) } }

    val state = combine(c.tasks.tasks, c.subjects.subjects, c.settings.settings, ticker) { t, s, st, now ->
        TasksState(t, s.associateBy { it.id }, st, now, now.atZone(c.clock.zone()).toLocalDate(), false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun setCompleted(id: Long, done: Boolean) = write { c.tasks.setCompleted(id, done) }

    fun delete(id: Long, onDeleted: (TaskRepository.Deleted) -> Unit) = write {
        c.tasks.delete(id)?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDeleted(it) } }
    }

    fun restore(d: TaskRepository.Deleted) = write { c.tasks.restore(d) }
}

private enum class TaskFilter(val label: String) { TODO("To do"), DONE("Completed") }

@Composable
fun TasksScreen(nav: NavHostController) {
    val vm = eduViewModel { TasksViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var filter by rememberSaveable { mutableStateOf(TaskFilter.TODO) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var subjectFilter by rememberSaveable { mutableStateOf<Long?>(null) }

    val nowMs = st.now.toEpochMilli()
    val visible = st.tasks.filter { t ->
        (if (filter == TaskFilter.TODO) t.completedAt == null else t.completedAt != null) &&
            (subjectFilter == null || t.subjectId == subjectFilter) &&
            (query.isBlank() || t.title.contains(query, true) || t.notes.contains(query, true) ||
                (t.subjectId?.let { st.subjects[it]?.name?.contains(query, true) } == true))
    }
    val pending = st.tasks.filter { it.completedAt == null }
    val overdue = pending.count { it.dueAt < nowMs }
    val thisWeek = pending.count { it.dueAt in nowMs..(nowMs + 7L * 86_400_000) }

    val onToggle: (TaskWithProgress) -> Unit = { t ->
        val nowDone = t.completedAt == null
        vm.setCompleted(t.id, nowDone)
        messenger.undo(if (nowDone) "Completed “${t.title}”" else "Moved back to To do") { vm.setCompleted(t.id, !nowDone) }
    }
    val onDelete: (TaskWithProgress) -> Unit = { t ->
        vm.delete(t.id) { d -> messenger.undo("Deleted “${t.title}”") { vm.restore(d) } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars)) }
            item {
                PageHeader(
                    "Tasks",
                    subtitle = when {
                        st.loading -> null
                        pending.isEmpty() -> "All clear"
                        else -> listOfNotNull(
                            overdue.takeIf { it > 0 }?.let { "$it overdue" },
                            "$thisWeek due this week",
                        ).joinToString(" · ")
                    },
                ) {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search")
                    }
                }
            }
            item {
                AnimatedVisibility(searching) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search tasks") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
                    )
                }
            }
            item {
                com.edubuddy.app.ui.common.UnderlineTabs(TaskFilter.entries, filter, { it.label }, { filter = it }, Modifier.padding(bottom = 8.dp))
            }
            val usedSubjects = st.tasks.mapNotNull { it.subjectId }.distinct().mapNotNull { st.subjects[it] }
            if (usedSubjects.size > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = subjectFilter == null, onClick = { subjectFilter = null }, label = { Text("All subjects") })
                        usedSubjects.forEach { s ->
                            FilterChip(
                                selected = subjectFilter == s.id,
                                onClick = { subjectFilter = if (subjectFilter == s.id) null else s.id },
                                label = { Text(s.name) },
                                leadingIcon = { ColorDot(SubjectPalette.color(s.colorIndex)) },
                            )
                        }
                    }
                }
            }

            if (!st.loading && visible.isEmpty()) {
                item {
                    when {
                        query.isNotBlank() || subjectFilter != null -> EmptyState(Icons.Rounded.Search, "No matches", "Try a different search or filter.")
                        filter == TaskFilter.DONE -> EmptyState(Icons.Rounded.TaskAlt, "Nothing completed yet", "Finished tasks show up here.")
                        else -> EmptyState(
                            Icons.Rounded.TaskAlt, "You're all caught up",
                            "Add assignments, projects and exam prep. You'll get a reminder before each deadline.",
                            actionLabel = "Add a task", onAction = { nav.navigate(TaskEditRoute()) },
                        )
                    }
                }
            }

            val groups = if (filter == TaskFilter.TODO) groupPending(visible, st.now, st.today) else listOf("" to visible.sortedByDescending { it.completedAt })
            groups.forEach { (label, list) ->
                if (list.isEmpty()) return@forEach
                if (label.isNotEmpty()) {
                    item(key = "g$label") {
                        GroupLabel("$label · ${list.size}", color = if (label == "Overdue") EduTheme.extra.danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.id }) { t ->
                    SwipeableTask(
                        task = t,
                        onToggle = { onToggle(t) },
                        onDelete = { onDelete(t) },
                        modifier = Modifier.animateItem(),
                    ) {
                        EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 3.dp), contentPadding = PaddingValues(0.dp)) {
                            TaskRow(
                                task = t,
                                subject = t.subjectId?.let { st.subjects[it] },
                                now = st.now, today = st.today, use24h = st.settings.use24h,
                                onToggle = { onToggle(t) },
                                onClick = { nav.navigate(TaskEditRoute(id = t.id)) },
                            )
                        }
                    }
                }
            }
        }

        com.edubuddy.app.ui.common.EduFab(
            onClick = { nav.navigate(TaskEditRoute(subjectId = subjectFilter ?: 0)) },
            icon = Icons.Rounded.Add,
            text = "New task",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun groupPending(list: List<TaskWithProgress>, now: Instant, today: LocalDate): List<Pair<String, List<TaskWithProgress>>> {
    val nowMs = now.toEpochMilli()
    val zone = java.time.ZoneId.systemDefault()
    fun dayOf(t: TaskWithProgress) = Instant.ofEpochMilli(t.dueAt).atZone(zone).toLocalDate()
    val sorted = list.sortedBy { it.dueAt }
    val overdue = sorted.filter { it.dueAt < nowMs }
    val rest = sorted.filter { it.dueAt >= nowMs }
    val todayL = rest.filter { dayOf(it) == today }
    val tomorrow = rest.filter { dayOf(it) == today.plusDays(1) }
    val week = rest.filter { val d = ChronoUnit.DAYS.between(today, dayOf(it)); d in 2..7 }
    val later = rest.filter { ChronoUnit.DAYS.between(today, dayOf(it)) > 7 }
    return listOf("Overdue" to overdue, "Today" to todayL, "Tomorrow" to tomorrow, "Next 7 days" to week, "Later" to later)
}

@Composable
private fun SwipeableTask(
    task: TaskWithProgress,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onToggle(); false }   // snap back; list updates
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    val ex = EduTheme.extra
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toEnd) ex.success else ex.danger
            Box(
                Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = 3.dp).clip(MaterialTheme.shapes.large).background(color.copy(alpha = 0.14f)).padding(horizontal = 20.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    when {
                        !toEnd -> Icons.Rounded.DeleteOutline
                        task.completedAt != null -> Icons.Rounded.Undo
                        else -> Icons.Rounded.Check
                    },
                    contentDescription = null, tint = color,
                )
            }
        },
    ) { content() }
}

/** One task line, used in the Tasks list and on Today. */
@Composable
fun TaskRow(
    task: TaskWithProgress,
    subject: SubjectEntity?,
    now: Instant,
    today: LocalDate,
    use24h: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val ex = EduTheme.extra
    val done = task.completedAt != null
    val overdue = !done && task.dueAt < now.toEpochMilli()
    val due = Instant.ofEpochMilli(task.dueAt).atZone(java.time.ZoneId.systemDefault())
    val ringColor = if (task.priority == Priority.HIGH) ex.danger else MaterialTheme.colorScheme.outline
    val haptics = com.edubuddy.app.ui.common.rememberHaptics()
    val checkScale by androidx.compose.animation.core.animateFloatAsState(
        if (done) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "check",
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Circular checkbox, ring coloured by priority.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Checkbox) { if (!done) haptics.confirm() else haptics.tick(); onToggle() }
                .semantics {
                    contentDescription = "Mark ${task.title} ${if (done) "not done" else "done"}"
                    stateDescription = if (done) "Done" else "Not done"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (done) MaterialTheme.colorScheme.primary else ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(22.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkScale.coerceIn(0f, 1f) }
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subject != null) {
                    ColorDot(SubjectPalette.color(subject.colorIndex), 8.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(subject.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val dueText = if (overdue) {
                    "Overdue · " + Fmt.relativeDay(due.toLocalDate(), today)
                } else {
                    Fmt.relativeDay(due.toLocalDate(), today) + ", " + Fmt.time(due.toLocalTime(), use24h)
                }
                Text(
                    dueText, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = if (overdue) ex.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (task.subtaskCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "${task.subtaskDone}/${task.subtaskCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (task.priority == Priority.HIGH && !done) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Flag, "High priority", tint = ex.danger, modifier = Modifier.size(16.dp))
        }
    }
}
