package com.edubuddy.app.ui.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.db.ClassKind
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.EventKind
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.ui.common.ChoiceChips
import com.edubuddy.app.ui.common.ConfirmDialog
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduDatePicker
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduTimePicker
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EditorScaffold
import com.edubuddy.app.ui.common.FormSection
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.PickerField
import com.edubuddy.app.ui.common.ReminderField
import com.edubuddy.app.ui.common.SubjectField
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.icon
import com.edubuddy.app.ui.navigation.ClassEditRoute
import com.edubuddy.app.ui.navigation.EventEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ================================ Class (timetable slot) ======================================

class ClassEditViewModel(c: AppContainer, private val route: ClassEditRoute) : EduViewModel(c) {
    val isNew = route.id == 0L
    val subjects: StateFlow<List<SubjectEntity>> = c.subjects.subjects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var loaded by mutableStateOf(isNew)
    var subjectId by mutableStateOf(route.subjectId.takeIf { it != 0L })
    var kind by mutableStateOf(ClassKind.LECTURE)
    /** New classes can be created on several days at once. */
    val days = mutableStateListOf<Int>().apply { add(route.day.takeIf { it in 1..7 } ?: LocalDate.now().dayOfWeek.value) }
    var start by mutableStateOf(LocalTime.of(9, 0))
    var end by mutableStateOf(LocalTime.of(10, 0))
    var room by mutableStateOf("")
    var reminder by mutableStateOf(10)
    var subjectError by mutableStateOf<String?>(null)
    var clashes by mutableStateOf<List<String>>(emptyList())

    private var original: ClassSlotEntity? = null
    private var baseline = ""

    init {
        viewModelScope.launch {
            if (isNew) {
                reminder = c.settings.current().defaultClassReminder
                // Default to the hour after the last class that day, a common pattern.
                val lastEnd = c.db.classSlots().all().filter { it.dayOfWeek == days.first() }.maxOfOrNull { it.endMinute }
                if (lastEnd != null && lastEnd <= 22 * 60) {
                    start = TimeMath.timeOf(lastEnd)
                    end = TimeMath.timeOf((lastEnd + 60).coerceAtMost(23 * 60 + 59))
                }
            } else {
                c.schedule.getSlot(route.id)?.let { s ->
                    original = s
                    subjectId = s.subjectId; kind = s.kind; days.clear(); days.add(s.dayOfWeek)
                    start = TimeMath.timeOf(s.startMinute); end = TimeMath.timeOf(s.endMinute)
                    room = s.room; reminder = s.reminderMinutes
                }
                loaded = true
            }
            baseline = fp()
            checkClashes()
        }
    }

    private fun fp() = listOf(subjectId, kind, days.toList(), start, end, room, reminder).toString()
    val dirty get() = loaded && fp() != baseline
    val timeError get() = !end.isAfter(start)

    fun changeStart(t: LocalTime) {
        val dur = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(30)
        start = t
        // Keep the duration when moving the start, the way calendar apps do.
        val newEnd = TimeMath.minuteOfDay(t) + dur
        end = if (newEnd < 24 * 60) TimeMath.timeOf(newEnd.toInt()) else LocalTime.of(23, 59)
        checkClashes()
    }

    fun changeEnd(t: LocalTime) { end = t; checkClashes() }
    fun toggleDay(d: Int) {
        if (!isNew) { days.clear(); days.add(d) }
        else if (d in days) { if (days.size > 1) days.remove(d) } else days.add(d)
        checkClashes()
    }

    fun checkClashes() = viewModelScope.launch {
        val s = TimeMath.minuteOfDay(start); val e = TimeMath.minuteOfDay(end)
        if (e <= s) { clashes = emptyList(); return@launch }
        val subjects = c.db.subjects().all().associateBy { it.id }
        clashes = days.flatMap { d ->
            c.schedule.findClashes(d, s, e, route.id).mapNotNull { other ->
                val name = subjects[other.subjectId]?.takeIf { !it.archived }?.name ?: return@mapNotNull null
                "$name on ${Fmt.dayShort(d)} (${Fmt.time(other.startMinute, false)}–${Fmt.time(other.endMinute, false)})"
            }
        }
    }

    fun createSubject(name: String, color: Int) = viewModelScope.launch {
        subjectId = c.subjects.save(SubjectEntity(name = name, colorIndex = color)); subjectError = null
    }

    fun save(): Boolean {
        val sid = subjectId
        if (sid == null) { subjectError = "Pick or create a subject"; return false }
        if (timeError) return false
        val base = original ?: ClassSlotEntity(subjectId = sid, dayOfWeek = 1, startMinute = 0, endMinute = 0)
        val toSave = days.sorted().mapIndexed { i, d ->
            base.copy(
                id = if (i == 0) base.id else 0L,
                subjectId = sid, dayOfWeek = d, kind = kind,
                startMinute = TimeMath.minuteOfDay(start), endMinute = TimeMath.minuteOfDay(end),
                room = room.trim(), reminderMinutes = reminder,
            )
        }
        write { toSave.forEach { c.schedule.saveSlot(it) } }
        return true
    }

    fun delete(onDeleted: (ClassSlotEntity) -> Unit) {
        val o = original ?: return
        write { c.schedule.deleteSlot(o) }
        onDeleted(o)
    }

    fun restore(s: ClassSlotEntity) = write { c.schedule.restoreSlot(s) }
}

@Composable
fun ClassEditScreen(route: ClassEditRoute, onBack: () -> Unit) {
    val vm = eduViewModel(key = "class-${route.id}-${route.subjectId}-${route.day}") { ClassEditViewModel(it, route) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (vm.isNew) "Add class" else "Edit class",
        onBack = onBack,
        onSave = {
            if (vm.save()) {
                messenger.show(if (vm.isNew && vm.days.size > 1) "Added on ${vm.days.size} days" else if (vm.isNew) "Class added" else "Class updated")
                onBack()
            }
        },
        dirty = vm.dirty,
        saveEnabled = !vm.timeError,
        onDelete = if (vm.isNew) null else ({ confirmDelete = true }),
    ) {
        SubjectField(
            subjects = subjects, selectedId = vm.subjectId, onSelect = { vm.subjectId = it; vm.subjectError = null },
            onCreate = vm::createSubject, icon = Icons.Rounded.School, allowNone = false, error = vm.subjectError,
        )

        FormSection("Type") { ChoiceChips(ClassKind.entries, vm.kind, { it.label }, { vm.kind = it }, icon = { it.icon() }) }

        FormSection(if (vm.isNew) "Repeats every" else "Day") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { d ->
                    FilterChip(selected = d in vm.days, onClick = { vm.toggleDay(d) }, label = { Text(Fmt.dayShort(d)) })
                }
            }
        }

        FormSection("Time") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField("Starts", Fmt.time(vm.start, settings.use24h), Icons.Rounded.Schedule, { pickStart = true }, Modifier.weight(1f))
                PickerField("Ends", Fmt.time(vm.end, settings.use24h), Icons.Rounded.Schedule, { pickEnd = true }, Modifier.weight(1f), error = vm.timeError)
            }
            if (vm.timeError) {
                Text("End time must be after the start", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
        }

        if (vm.clashes.isNotEmpty()) {
            EduCard(containerColor = EduTheme.extra.warningContainer) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = EduTheme.extra.warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Overlaps with " + vm.clashes.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        EduTextField(vm.room, { vm.room = it }, "Room / location", placeholder = "e.g. LT-3, Lab 204", leadingIcon = Icons.Rounded.LocationOn, capitalization = KeyboardCapitalization.Characters, maxLength = 60)

        ReminderField(vm.reminder, { vm.reminder = it }, Icons.Rounded.NotificationsActive, options = listOf(-1, 0, 5, 10, 15, 20, 30, 45, 60))
    }

    if (pickStart) EduTimePicker(vm.start, settings.use24h, vm::changeStart, { pickStart = false }, "Class starts")
    if (pickEnd) EduTimePicker(vm.end, settings.use24h, vm::changeEnd, { pickEnd = false }, "Class ends")
    if (confirmDelete) {
        ConfirmDialog(
            "Remove class?", "This removes it from your timetable. Attendance you've already marked is kept.",
            "Remove", destructive = true,
            onConfirm = { vm.delete { s -> messenger.undo("Class removed") { vm.restore(s) } }; onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ================================ Event ======================================================

class EventEditViewModel(c: AppContainer, private val route: EventEditRoute) : EduViewModel(c) {
    val isNew = route.id == 0L
    val subjects: StateFlow<List<SubjectEntity>> = c.subjects.subjects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var loaded by mutableStateOf(isNew)
    var title by mutableStateOf("")
    var kind by mutableStateOf(EventKind.SEMINAR)
    var date by mutableStateOf(LocalDate.now().plusDays(1))
    var start by mutableStateOf(LocalTime.of(10, 0))
    var hasEnd by mutableStateOf(true)
    var end by mutableStateOf(LocalTime.of(11, 0))
    var location by mutableStateOf("")
    var speaker by mutableStateOf("")
    var subjectId by mutableStateOf<Long?>(null)
    var reminder by mutableStateOf(60)
    var notes by mutableStateOf("")
    var titleError by mutableStateOf<String?>(null)

    private var original: EventEntity? = null
    private var baseline = ""

    init {
        viewModelScope.launch {
            if (isNew) reminder = c.settings.current().defaultEventReminder
            else {
                c.schedule.getEvent(route.id)?.let { e ->
                    original = e
                    title = e.title; kind = e.kind; location = e.location; speaker = e.speaker
                    subjectId = e.subjectId; reminder = e.reminderMinutes; notes = e.notes
                    val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.startAt), c.clock.zone())
                    date = s.toLocalDate(); start = s.toLocalTime()
                    hasEnd = e.endAt != null
                    e.endAt?.let { end = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), c.clock.zone()).toLocalTime() }
                }
                loaded = true
            }
            baseline = fp()
        }
    }

    private fun fp() = listOf(title, kind, date, start, hasEnd, end, location, speaker, subjectId, reminder, notes).toString()
    val dirty get() = loaded && fp() != baseline
    val timeError get() = hasEnd && !end.isAfter(start)

    fun changeStart(t: LocalTime) {
        val dur = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(30)
        start = t
        val e = TimeMath.minuteOfDay(t) + dur
        end = if (e < 24 * 60) TimeMath.timeOf(e.toInt()) else LocalTime.of(23, 59)
    }

    fun createSubject(name: String, color: Int) = viewModelScope.launch {
        subjectId = c.subjects.save(SubjectEntity(name = name, colorIndex = color))
    }

    fun save(): Boolean {
        if (title.isBlank()) { titleError = "Give the event a name"; return false }
        if (timeError) return false
        val zone = c.clock.zone()
        val startMs = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val endMs = if (hasEnd) date.atTime(end).atZone(zone).toInstant().toEpochMilli() else null
        val e = (original ?: EventEntity(title = "", startAt = startMs)).copy(
            title = title.trim(), kind = kind, startAt = startMs, endAt = endMs, location = location.trim(),
            speaker = speaker.trim(), subjectId = subjectId, reminderMinutes = reminder, notes = notes.trim(),
        )
        write { c.schedule.saveEvent(e) }
        return true
    }

    fun delete(onDeleted: (EventEntity) -> Unit) {
        val o = original ?: return
        write { c.schedule.deleteEvent(o) }
        onDeleted(o)
    }

    fun restore(e: EventEntity) = write { c.schedule.restoreEvent(e) }
}

@Composable
fun EventEditScreen(route: EventEditRoute, onBack: () -> Unit) {
    val vm = eduViewModel(key = "event-${route.id}") { EventEditViewModel(it, route) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (vm.isNew) "New event" else "Edit event",
        onBack = onBack,
        onSave = { if (vm.save()) { messenger.show(if (vm.isNew) "Event added" else "Event updated"); onBack() } },
        dirty = vm.dirty,
        saveEnabled = !vm.timeError,
        onDelete = if (vm.isNew) null else ({ confirmDelete = true }),
    ) {
        EduTextField(
            vm.title, { vm.title = it; vm.titleError = null }, "Title",
            placeholder = "e.g. Robotics in Medicine", error = vm.titleError, capitalization = KeyboardCapitalization.Words,
        )
        FormSection("Type") { ChoiceChips(EventKind.entries, vm.kind, { it.label }, { vm.kind = it }, icon = { it.icon() }) }

        FormSection("When") {
            PickerField("Date", Fmt.pickerDate(vm.date, LocalDate.now()), Icons.Rounded.Event, { pickDate = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PickerField("Starts", Fmt.time(vm.start, settings.use24h), Icons.Rounded.Schedule, { pickStart = true }, Modifier.weight(1f))
                if (vm.hasEnd) PickerField("Ends", Fmt.time(vm.end, settings.use24h), Icons.Rounded.Schedule, { pickEnd = true }, Modifier.weight(1f), error = vm.timeError)
                else Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Has an end time", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = vm.hasEnd, onCheckedChange = { vm.hasEnd = it })
            }
        }

        EduTextField(vm.location, { vm.location = it }, "Location", placeholder = "e.g. Main Auditorium", leadingIcon = Icons.Rounded.LocationOn, capitalization = KeyboardCapitalization.Words, maxLength = 80)
        EduTextField(vm.speaker, { vm.speaker = it }, "Speaker / host", placeholder = "Optional", leadingIcon = Icons.Rounded.Person, capitalization = KeyboardCapitalization.Words, maxLength = 80)
        SubjectField(subjects, vm.subjectId, { vm.subjectId = it }, vm::createSubject, Icons.Rounded.School, allowNone = true)
        ReminderField(vm.reminder, { vm.reminder = it }, Icons.Rounded.NotificationsActive)
        OutlinedTextField(
            value = vm.notes, onValueChange = { if (it.length <= 2000) vm.notes = it },
            label = { Text("Notes") }, minLines = 3, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickDate) EduDatePicker(vm.date, { vm.date = it }, { pickDate = false })
    if (pickStart) EduTimePicker(vm.start, settings.use24h, vm::changeStart, { pickStart = false }, "Starts")
    if (pickEnd) EduTimePicker(vm.end, settings.use24h, { vm.end = it }, { pickEnd = false }, "Ends")
    if (confirmDelete) {
        ConfirmDialog(
            "Delete event?", "“${vm.title}” will be removed.", "Delete", destructive = true,
            onConfirm = { vm.delete { e -> messenger.undo("Event deleted") { vm.restore(e) } }; onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
}
