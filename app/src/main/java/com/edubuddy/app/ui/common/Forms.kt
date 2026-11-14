package com.edubuddy.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.ui.theme.SubjectPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Full-screen editor shell: back arrow, title, Save button, optional delete,
 * scrolling content, and a "discard changes?" guard when [dirty].
 */
@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    dirty: Boolean,
    saveLabel: String = "Save",
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val tryBack = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = tryBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = tryBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                    }
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled,
                        modifier = Modifier.padding(end = 12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) { Text(saveLabel) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits haven't been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = onBack,
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun EduTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    maxLength: Int = 120,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        prefix = prefix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A read-only field that opens a picker when tapped. */
@Composable
fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val borderColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Material date picker that speaks LocalDate (the M3 picker uses UTC midnight millis). */
@Composable
fun EduDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun EduTimePicker(initial: LocalTime, is24h: Boolean, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit, title: String = "Select time") {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.labelLarge) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Single-select chip group. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { opt ->
            val isSel = opt == selected
            FilterChip(
                selected = isSel,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
                leadingIcon = when {
                    isSel -> { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } }
                    icon != null -> { { Icon(icon(opt), null, Modifier.size(FilterChipDefaults.IconSize)) } }
                    else -> null
                },
            )
        }
    }
}

/** Segmented single choice for 2–4 options. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label(opt), maxLines = 1) },
            )
        }
    }
}

/** Generic dropdown field. */
@Composable
fun <T> DropdownField(
    label: String,
    value: String,
    icon: ImageVector,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    optionLeading: (@Composable (T) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PickerFieldWithChevron(label, value, icon) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    leadingIcon = optionLeading?.let { { it(opt) } },
                    onClick = { onSelect(opt); open = false },
                )
            }
            if (footer != null) {
                if (options.isNotEmpty()) HorizontalDivider()
                footer { open = false }
            }
        }
    }
}

@Composable
private fun PickerFieldWithChevron(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

val ReminderOptions = listOf(-1, 0, 5, 10, 15, 30, 60, 120, 180, 24 * 60, 2 * 24 * 60, 3 * 24 * 60, 7 * 24 * 60)

@Composable
fun ReminderField(minutes: Int, onChange: (Int) -> Unit, icon: ImageVector, options: List<Int> = ReminderOptions, modifier: Modifier = Modifier) {
    val opts = if (minutes in options) options else (options + minutes).sorted()
    DropdownField(
        label = "Reminder",
        value = Fmt.reminderLabel(minutes),
        icon = icon,
        options = opts,
        optionLabel = { Fmt.reminderLabel(it) },
        onSelect = onChange,
        modifier = modifier,
    )
}

/**
 * Subject selector with inline "New subject" creation, so a first-time user never hits a
 * dead end ("you need to create a subject first").
 */
@Composable
fun SubjectField(
    subjects: List<SubjectEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (name: String, colorIndex: Int) -> Unit,
    icon: ImageVector,
    allowNone: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val active = subjects.filter { !it.archived || it.id == selectedId }
    val selected = active.firstOrNull { it.id == selectedId }
    val options: List<SubjectEntity?> = (if (allowNone) listOf(null) else emptyList<SubjectEntity?>()) + active
    Column(modifier) {
        DropdownField(
            label = "Subject",
            value = selected?.name ?: if (allowNone) "None" else "Choose a subject",
            icon = icon,
            options = options,
            optionLabel = { it?.name ?: "None" },
            onSelect = { onSelect(it?.id) },
            optionLeading = { s -> ColorDot(if (s == null) Color.Transparent else SubjectPalette.color(s.colorIndex), 12.dp) },
            footer = { dismiss ->
                DropdownMenuItem(
                    text = { Text("New subject…", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { dismiss(); creating = true },
                )
            },
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
    if (creating) {
        NewSubjectDialog(
            suggestedColor = subjects.size,
            onCreate = { n, c -> onCreate(n, c); creating = false },
            onDismiss = { creating = false },
        )
    }
}

@Composable
fun NewSubjectDialog(suggestedColor: Int, onCreate: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(Math.floorMod(suggestedColor, SubjectPalette.colors.size)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EduTextField(name, { name = it }, "Subject name", placeholder = "e.g. Data Structures", capitalization = KeyboardCapitalization.Words, maxLength = 60)
                ColorSwatches(color, onSelect = { color = it })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), color) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubjectPalette.colors.forEachIndexed { i, c ->
            val isSel = i == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(if (isSel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, SubjectPalette.names[i], tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(title)
        content()
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
