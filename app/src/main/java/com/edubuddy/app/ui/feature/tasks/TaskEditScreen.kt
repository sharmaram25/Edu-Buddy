package com.edubuddy.app.ui.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.Priority
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.SubtaskEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.db.TaskKind
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.ui.common.ChoiceChips
import com.edubuddy.app.ui.common.ConfirmDialog
import com.edubuddy.app.ui.common.EduDatePicker
import com.edubuddy.app.ui.common.EduTimePicker
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EditorScaffold
import com.edubuddy.app.ui.common.FormSection
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.PickerField
import com.edubuddy.app.ui.common.ReminderField
import com.edubuddy.app.ui.common.Segmented
import com.edubuddy.app.ui.common.SubjectField
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.icon
import com.edubuddy.app.ui.navigation.TaskEditRoute
import com.edubuddy.app.data.db.SubjectEntity as Subject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskEditViewModel(c: AppContainer, private val route: TaskEditRoute) : EduViewModel(c) {
    val isNew = route.id == 0L
    val subjects: StateFlow<List<SubjectEntity>> = c.subjects.subjects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var loaded by mutableStateOf(isNew)
    var title by mutableStateOf("")
    var subjectId by mutableStateOf(route.subjectId.takeIf { it != 0L })
    var kind by mutableStateOf(TaskKind.ASSIGNMENT)
    var date by mutableStateOf(LocalDate.now().plusDays(1))
    var time by mutableStateOf(LocalTime.of(23, 59))
    var priority by mutableStateOf(Priority.MEDIUM)
    var reminder by mutableStateOf(24 * 60)
    var notes by mutableStateOf("")
    val subtasks = mutableStateListOf<SubtaskEntity>()
    var titleError by mutableStateOf<String?>(null)

    private var original: TaskEntity? = null
    private var originalSubtasks: List<SubtaskEntity> = emptyList()
    private var baseline = ""

    init {
        viewModelScope.launch {
            if (isNew) {
                reminder = c.settings.current().defaultTaskReminder
            } else {
                val t = c.tasks.get(route.id)
                if (t != null) {
                    original = t
                    title = t.title; subjectId = t.subjectId; kind = t.kind; priority = t.priority
                    reminder = t.reminderMinutes; notes = t.notes
                    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.dueAt), c.clock.zone())
                    date = dt.toLocalDate(); time = dt.toLocalTime()
                    originalSubtasks = c.tasks.getSubtasks(t.id)
                    subtasks.addAll(originalSubtasks)
                }
                loaded = true
            }
            baseline = fingerprint()
        }
    }

    private fun fingerprint() = listOf(title, subjectId, kind, date, time, priority, reminder, notes, subtasks.map { it.title to it.done }).toString()
    val dirty get() = loaded && fingerprint() != baseline

    fun createSubject(name: String, color: Int) = viewModelScope.launch {
        subjectId = c.subjects.save(Subject(name = name, colorIndex = color))
    }

    fun addSubtask(text: String) {
        if (text.isBlank()) return
        subtasks.add(SubtaskEntity(taskId = route.id, title = text.trim(), position = subtasks.size))
    }

    fun save(): Boolean {
        if (title.isBlank()) { titleError = "Give the task a name"; return false }
        val due = date.atTime(time).atZone(c.clock.zone()).toInstant().toEpochMilli()
        val entity = (original ?: TaskEntity(title = "", dueAt = due)).copy(
            title = title.trim(), subjectId = subjectId, kind = kind, dueAt = due,
            priority = priority, notes = notes.trim(), reminderMinutes = reminder,
        )
        val subs = subtasks.toList()
        write { c.tasks.save(entity, subs) }
        return true
    }

    fun delete(onDeleted: (com.edubuddy.app.data.repo.TaskRepository.Deleted) -> Unit) = write {
        c.tasks.delete(route.id)?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDeleted(it) } }
    }

    fun restore(d: com.edubuddy.app.data.repo.TaskRepository.Deleted) = write { c.tasks.restore(d) }
}

private val QuickTimes = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(17, 0), LocalTime.of(23, 59))

@Composable
fun TaskEditScreen(route: TaskEditRoute, onBack: () -> Unit) {
    val vm = eduViewModel(key = "task-${route.id}-${route.subjectId}") { TaskEditViewModel(it, route) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }

    EditorScaffold(
        title = if (vm.isNew) "New task" else "Edit task",
        onBack = onBack,
        onSave = { if (vm.save()) { messenger.show(if (vm.isNew) "Task added" else "Task updated"); onBack() } },
        dirty = vm.dirty,
        onDelete = if (vm.isNew) null else ({ confirmDelete = true }),
    ) {
        // Prominent title field.
        TextField(
            value = vm.title,
            onValueChange = { if (it.length <= 120) { vm.title = it; vm.titleError = null } },
            placeholder = { Text("What needs doing?", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            isError = vm.titleError != null,
            supportingText = vm.titleError?.let { { Text(it) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        FormSection("Type") {
            ChoiceChips(TaskKind.entries, vm.kind, { it.label }, { vm.kind = it }, icon = { it.icon() })
        }

        SubjectField(
            subjects = subjects, selectedId = vm.subjectId, onSelect = { vm.subjectId = it },
            onCreate = { n, col -> vm.createSubject(n, col) }, icon = Icons.Rounded.School, allowNone = true,
        )

        FormSection("Due") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField("Date", Fmt.pickerDate(vm.date, LocalDate.now()), Icons.Rounded.Event, { pickDate = true }, Modifier.weight(1.3f))
                PickerField("Time", Fmt.time(vm.time, settings.use24h), Icons.Rounded.Schedule, { pickTime = true }, Modifier.weight(1f))
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                QuickTimes.forEach { t ->
                    androidx.compose.material3.SuggestionChip(onClick = { vm.time = t }, label = { Text(if (t == LocalTime.of(23, 59)) "End of day" else Fmt.time(t, settings.use24h)) })
                }
            }
        }

        FormSection("Priority") {
            Segmented(Priority.entries.reversed(), vm.priority, { it.label }, { vm.priority = it })
        }

        ReminderField(vm.reminder, { vm.reminder = it }, Icons.Rounded.NotificationsActive)

        FormSection("Checklist") {
            Column {
                vm.subtasks.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.done, onCheckedChange = { vm.subtasks[i] = s.copy(done = it) })
                        Text(
                            s.title, modifier = Modifier.weight(1f),
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { vm.subtasks.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove step") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSubtask, onValueChange = { if (it.length <= 100) newSubtask = it },
                        placeholder = { Text("Add a step") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                        keyboardActions = KeyboardActions(onDone = { vm.addSubtask(newSubtask); newSubtask = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { vm.addSubtask(newSubtask); newSubtask = "" },
                        enabled = newSubtask.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Icon(Icons.Rounded.Add, "Add step") }
                }
            }
        }

        OutlinedTextField(
            value = vm.notes, onValueChange = { if (it.length <= 2000) vm.notes = it },
            label = { Text("Notes") },
            minLines = 3,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickDate) EduDatePicker(vm.date, { vm.date = it }, { pickDate = false })
    if (pickTime) EduTimePicker(vm.time, settings.use24h, { vm.time = it }, { pickTime = false })
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            message = "“${vm.title}” and its checklist will be removed.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = {
                val name = vm.title
                vm.delete { d -> messenger.undo("Deleted “$name”") { vm.restore(d) } }
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
