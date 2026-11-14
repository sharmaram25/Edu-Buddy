package com.edubuddy.app.ui.feature.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.AttendanceMath
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.AttendanceEntity
import com.edubuddy.app.data.db.AttendanceStatus
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.ui.common.ColorSwatches
import com.edubuddy.app.ui.common.ConfirmDialog
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduDatePicker
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EditorScaffold
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.FormSection
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.HeroCard
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.PickerField
import com.edubuddy.app.ui.common.ProgressRing
import com.edubuddy.app.ui.common.Segmented
import com.edubuddy.app.ui.common.SectionHeader
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.common.icon
import com.edubuddy.app.ui.navigation.ClassEditRoute
import com.edubuddy.app.ui.navigation.SubjectDetailRoute
import com.edubuddy.app.ui.navigation.SubjectEditRoute
import com.edubuddy.app.ui.navigation.TaskEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---- Helpers -------------------------------------------------------------------------------

fun SubjectEntity.targetFraction(s: Settings) = (attendanceTarget ?: s.attendanceTarget) / 100.0

@Composable
fun attendanceColor(summary: AttendanceMath.Summary): Color {
    val ex = EduTheme.extra
    return when {
        summary.total == 0 -> MaterialTheme.colorScheme.outline
        !summary.onTrack -> ex.danger
        summary.canSkip == 0 -> ex.warning
        else -> ex.success
    }
}

fun AttendanceMath.Summary.advice(): String = when {
    total == 0 -> "No classes marked yet"
    !onTrack -> "Attend the next $mustAttend ${if (mustAttend == 1) "class" else "classes"} to reach ${(target * 100).toInt()}%"
    canSkip == 0 -> "On the edge. Don't miss the next class"
    else -> "You can miss $canSkip ${if (canSkip == 1) "class" else "classes"} and stay above ${(target * 100).toInt()}%"
}

// ================================ List =======================================================

data class SubjectRow(val subject: SubjectEntity, val summary: AttendanceMath.Summary, val weeklyClasses: Int)

class SubjectsViewModel(c: AppContainer) : EduViewModel(c) {
    val state = combine(c.subjects.subjects, c.attendance.counts, c.schedule.slots, c.settings.settings) { subjects, counts, slots, s ->
        val byId = counts.associateBy { it.subjectId }
        subjects.map { sub ->
            val cnt = byId[sub.id]
            SubjectRow(
                sub,
                AttendanceMath.Summary(cnt?.present ?: 0, (cnt?.present ?: 0) + (cnt?.absent ?: 0), sub.targetFraction(s)),
                slots.count { it.subjectId == sub.id },
            )
        } to s
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SubjectsScreen(nav: NavHostController) {
    val vm = eduViewModel { SubjectsViewModel(it) }
    val data by vm.state.collectAsStateWithLifecycle()
    val rows = data?.first.orEmpty()
    val settings = data?.second ?: Settings()
    val active = rows.filter { !it.subject.archived }
    val archived = rows.filter { it.subject.archived }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Subjects & attendance") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            com.edubuddy.app.ui.common.EduFab(onClick = { nav.navigate(SubjectEditRoute()) }, icon = Icons.Rounded.Add, text = "Add subject")
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (data != null && rows.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.LibraryBooks, "No subjects yet",
                        "Add your subjects to track attendance against your college's minimum (${settings.attendanceTarget}% by default).",
                        actionLabel = "Add subject", onAction = { nav.navigate(SubjectEditRoute()) },
                    )
                }
            }
            if (active.isNotEmpty()) {
                item {
                    val present = active.sumOf { it.summary.attended }
                    val total = active.sumOf { it.summary.total }
                    val overall = AttendanceMath.Summary(present, total, settings.attendanceTarget / 100.0)
                    OverallCard(overall, active.count { it.summary.total > 0 && !it.summary.onTrack })
                }
            }
            items(active, key = { it.subject.id }) { r ->
                SubjectCard(r, onClick = { nav.navigate(SubjectDetailRoute(r.subject.id)) }, Modifier.padding(horizontal = Gutter, vertical = 4.dp).animateItem())
            }
            if (archived.isNotEmpty()) {
                item { GroupLabel("Archived") }
                items(archived, key = { it.subject.id }) { r ->
                    SubjectCard(r, onClick = { nav.navigate(SubjectDetailRoute(r.subject.id)) }, Modifier.padding(horizontal = Gutter, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun OverallCard(overall: AttendanceMath.Summary, atRisk: Int) {
    val ex = EduTheme.extra
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(overall.ratio, size = 76.dp, stroke = 6.dp, color = ex.onHero, trackColor = ex.heroTrack) {
                Text(if (overall.total == 0) "—" else "${overall.percent.toInt()}%", style = MaterialTheme.typography.titleMedium, color = ex.onHero)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("OVERALL ATTENDANCE", style = MaterialTheme.typography.labelSmall, color = ex.onHeroMuted)
                Text("${overall.attended} of ${overall.total} classes", style = MaterialTheme.typography.titleMedium, color = ex.onHero)
                Text(
                    if (atRisk == 0) "Every subject is on track" else "$atRisk ${if (atRisk == 1) "subject needs" else "subjects need"} attention",
                    style = MaterialTheme.typography.bodySmall, color = ex.onHeroMuted,
                )
            }
        }
    }
}

@Composable
private fun SubjectCard(r: SubjectRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = SubjectPalette.color(r.subject.colorIndex)
    val ac = attendanceColor(r.summary)
    EduCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.subject.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    r.subject.code.takeIf { it.isNotBlank() },
                    r.subject.instructor.takeIf { it.isNotBlank() },
                    if (r.weeklyClasses > 0) "${r.weeklyClasses}×/week" else null,
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(r.summary.advice(), style = MaterialTheme.typography.bodySmall, color = ac)
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(r.summary.ratio, size = 50.dp, stroke = 4.dp, color = ac) {
                Text(if (r.summary.total == 0) "—" else "${r.summary.percent.toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ================================ Detail =====================================================

data class SubjectDetailState(
    val subject: SubjectEntity? = null,
    val records: List<AttendanceEntity> = emptyList(),
    val slots: List<ClassSlotEntity> = emptyList(),
    val settings: Settings = Settings(),
    val loading: Boolean = true,
) {
    val summary: AttendanceMath.Summary get() {
        val p = records.count { it.status == AttendanceStatus.PRESENT }
        val a = records.count { it.status == AttendanceStatus.ABSENT }
        return AttendanceMath.Summary(p, p + a, subject?.targetFraction(settings) ?: 0.75)
    }
}

class SubjectDetailViewModel(c: AppContainer, val id: Long) : EduViewModel(c) {
    val state = combine(c.subjects.observe(id), c.attendance.forSubject(id), c.schedule.slotsForSubject(id), c.settings.settings) { s, r, sl, st ->
        SubjectDetailState(s, r, sl, st, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectDetailState())

    fun addManual(date: LocalDate, status: AttendanceStatus) = write { c.attendance.addManual(id, date, status) }
    fun setStatus(a: AttendanceEntity, status: AttendanceStatus) = write { c.attendance.update(a.copy(status = status)) }
    fun delete(a: AttendanceEntity) = write { c.attendance.delete(a) }
    fun restore(a: AttendanceEntity) = write { c.attendance.restore(a) }
}

@Composable
fun SubjectDetailScreen(id: Long, nav: NavHostController) {
    val vm = eduViewModel(key = "subject-$id") { SubjectDetailViewModel(it, id) }
    val st by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var addMark by remember { mutableStateOf(false) }
    val subject = st.subject

    if (!st.loading && subject == null) {
        // Deleted while open.
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val color = subject?.let { SubjectPalette.color(it.colorIndex) } ?: MaterialTheme.colorScheme.primary
    val summary = st.summary

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(subject?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { nav.navigate(SubjectEditRoute(id)) }) { Icon(Icons.Rounded.Edit, "Edit subject") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ac = attendanceColor(summary)
                        ProgressRing(summary.ratio, size = 96.dp, stroke = 7.dp, color = ac) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (summary.total == 0) "—" else "${summary.percent.toInt()}%", style = MaterialTheme.typography.headlineSmall)
                                Text("target ${(summary.target * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${summary.attended} attended · ${summary.total - summary.attended} missed", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(summary.advice(), style = MaterialTheme.typography.bodyMedium, color = ac)
                        }
                    }
                    if (subject != null && (subject.instructor.isNotBlank() || subject.code.isNotBlank())) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (subject.code.isNotBlank()) InfoBit(Icons.Rounded.Badge, subject.code)
                            if (subject.instructor.isNotBlank()) InfoBit(Icons.Rounded.Person, subject.instructor)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { addMark = true }, modifier = Modifier.weight(1f)) { Text("Log attendance") }
                        OutlinedButton(onClick = { nav.navigate(TaskEditRoute(subjectId = id)) }, modifier = Modifier.weight(1f)) { Text("Add task") }
                    }
                }
            }

            item { SectionHeader("Weekly classes", actionLabel = "Add", onAction = { nav.navigate(ClassEditRoute(subjectId = id)) }) }
            if (st.slots.isEmpty()) {
                item { Text("Not in your timetable yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter)) }
            }
            items(st.slots, key = { "s" + it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(ClassEditRoute(id = s.id)) }.padding(horizontal = Gutter, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.kind.icon(), null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(Fmt.dayFull(s.dayOfWeek), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
                    Text(
                        "${Fmt.time(s.startMinute, st.settings.use24h)} – ${Fmt.time(s.endMinute, st.settings.use24h)}" + (if (s.room.isNotBlank()) " · ${s.room}" else ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("History") }
            if (st.records.isEmpty()) {
                item {
                    Text(
                        "Mark attendance from the Today screen after each class, or log past classes here.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter),
                    )
                }
            }
            items(st.records, key = { "a" + it.id }) { a ->
                HistoryRow(
                    a,
                    onChange = { vm.setStatus(a, it) },
                    onDelete = { vm.delete(a); messenger.undo("Entry removed") { vm.restore(a) } },
                )
            }
        }
    }

    if (addMark) {
        LogAttendanceDialog(onSave = { d, s -> vm.addManual(d, s); messenger.show("Attendance logged") }, onDismiss = { addMark = false })
    }
}

@Composable
private fun InfoBit(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(a: AttendanceEntity, onChange: (AttendanceStatus) -> Unit, onDelete: () -> Unit) {
    val ex = EduTheme.extra
    var menu by remember { mutableStateOf(false) }
    val (icon, color, label) = when (a.status) {
        AttendanceStatus.PRESENT -> Triple(Icons.Rounded.Check, ex.success, "Present")
        AttendanceStatus.ABSENT -> Triple(Icons.Rounded.Close, ex.danger, "Absent")
        AttendanceStatus.CANCELLED -> Triple(Icons.Rounded.Remove, MaterialTheme.colorScheme.outline, "Cancelled")
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { menu = true }.padding(horizontal = Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(Fmt.dateShort(LocalDate.ofEpochDay(a.epochDay)) + " " + LocalDate.ofEpochDay(a.epochDay).year.let { if (it != LocalDate.now().year) it.toString() else "" },
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            if (a.slotId == null) {
                Spacer(Modifier.width(8.dp))
                Text("manual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AttendanceStatus.entries.filter { it != a.status }.forEach { s ->
                DropdownMenuItem(text = { Text("Change to ${s.name.lowercase()}") }, onClick = { onChange(s); menu = false })
            }
            DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); menu = false })
        }
    }
}

@Composable
private fun LogAttendanceDialog(onSave: (LocalDate, AttendanceStatus) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var status by remember { mutableStateOf(AttendanceStatus.PRESENT) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log attendance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("For extra classes or ones before you started using EduBuddy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerField("Date", Fmt.pickerDate(date, LocalDate.now()), Icons.Rounded.Event, { pick = true }, Modifier.fillMaxWidth())
                Segmented(listOf(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT), status, { if (it == AttendanceStatus.PRESENT) "Present" else "Absent" }, { status = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, status); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pick) EduDatePicker(date, { date = it }, { pick = false })
}

// ================================ Edit =======================================================

class SubjectEditViewModel(c: AppContainer, val id: Long) : EduViewModel(c) {
    val isNew = id == 0L
    var loaded by mutableStateOf(isNew)
    var name by mutableStateOf("")
    var code by mutableStateOf("")
    var instructor by mutableStateOf("")
    var color by mutableStateOf(0)
    var customTarget by mutableStateOf(false)
    var target by mutableStateOf(75f)
    var archived by mutableStateOf(false)
    var nameError by mutableStateOf<String?>(null)
    var globalTarget by mutableStateOf(75)
    private var original: SubjectEntity? = null
    private var baseline = ""

    init {
        viewModelScope.launch {
            globalTarget = c.settings.current().attendanceTarget
            target = globalTarget.toFloat()
            if (isNew) {
                color = c.db.subjects().all().size % SubjectPalette.colors.size
            } else {
                c.subjects.get(id)?.let { s ->
                    original = s
                    name = s.name; code = s.code; instructor = s.instructor; color = s.colorIndex
                    customTarget = s.attendanceTarget != null; target = (s.attendanceTarget ?: globalTarget).toFloat()
                    archived = s.archived
                }
                loaded = true
            }
            baseline = fp()
        }
    }

    private fun fp() = listOf(name, code, instructor, color, customTarget, target.toInt(), archived).toString()
    val dirty get() = loaded && fp() != baseline

    fun save(): Boolean {
        if (name.isBlank()) { nameError = "Enter a subject name"; return false }
        val s = (original ?: SubjectEntity(name = "")).copy(
            name = name.trim(), code = code.trim(), instructor = instructor.trim(), colorIndex = color,
            attendanceTarget = if (customTarget) target.toInt() else null, archived = archived,
        )
        write { c.subjects.save(s) }
        return true
    }

    fun delete() { original?.let { o -> write { c.subjects.delete(o) } } }
}

@Composable
fun SubjectEditScreen(id: Long, onBack: () -> Unit, onDeleted: () -> Unit) {
    val vm = eduViewModel(key = "subject-edit-$id") { SubjectEditViewModel(it, id) }
    val messenger = LocalMessenger.current
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (vm.isNew) "New subject" else "Edit subject",
        onBack = onBack,
        onSave = { if (vm.save()) { messenger.show(if (vm.isNew) "Subject added" else "Subject updated"); onBack() } },
        dirty = vm.dirty,
        onDelete = if (vm.isNew) null else ({ confirmDelete = true }),
    ) {
        EduTextField(vm.name, { vm.name = it; vm.nameError = null }, "Subject name", placeholder = "e.g. Operating Systems", error = vm.nameError, capitalization = KeyboardCapitalization.Words, maxLength = 60)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EduTextField(vm.code, { vm.code = it }, "Code", placeholder = "CS301", capitalization = KeyboardCapitalization.Characters, maxLength = 20, modifier = Modifier.weight(1f))
            EduTextField(vm.instructor, { vm.instructor = it }, "Instructor", placeholder = "Optional", capitalization = KeyboardCapitalization.Words, maxLength = 60, modifier = Modifier.weight(1.6f))
        }
        FormSection("Colour") { ColorSwatches(vm.color, { vm.color = it }) }

        FormSection("Attendance target") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Custom target for this subject", style = MaterialTheme.typography.bodyLarge)
                    Text(if (vm.customTarget) "${vm.target.toInt()}%" else "Using your default of ${vm.globalTarget}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.customTarget, onCheckedChange = { vm.customTarget = it })
            }
            if (vm.customTarget) {
                Slider(value = vm.target, onValueChange = { vm.target = it }, valueRange = 50f..95f, steps = 8)
            }
        }

        if (!vm.isNew) {
            EduCard(onClick = { vm.archived = !vm.archived }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Archive subject", style = MaterialTheme.typography.bodyLarge)
                        Text("Hides it from your timetable and Today, keeps its history. Use at semester end.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.archived, onCheckedChange = { vm.archived = it })
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${vm.name}?",
            "This permanently removes the subject, its timetable classes and all its attendance history. Tasks and events stay but lose the subject. Consider archiving instead.",
            "Delete", destructive = true,
            onConfirm = { vm.delete(); messenger.show("Subject deleted"); onDeleted() },
            onDismiss = { confirmDelete = false },
        )
    }
}
