package com.edubuddy.app.ui.feature.schedule

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.Pill
import com.edubuddy.app.ui.common.PickerField
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HolidaysViewModel(c: AppContainer) : EduViewModel(c) {
    val holidays = c.holidays.holidays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(h: HolidayEntity) = write { c.holidays.save(h) }
    fun delete(h: HolidayEntity) = write { c.holidays.delete(h) }
    fun restore(h: HolidayEntity) = write { c.holidays.restore(h) }
}

@Composable
fun HolidaysScreen(onBack: () -> Unit) {
    val vm = eduViewModel { HolidaysViewModel(it) }
    val list by vm.holidays.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var editing by remember { mutableStateOf<HolidayEntity?>(null) }
    val today = LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Holidays & breaks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            com.edubuddy.app.ui.common.EduFab(
                text = "Add break", icon = Icons.Rounded.Add,
                onClick = { editing = HolidayEntity(title = "", startDay = today, endDay = today) },
            )
        },
    ) { padding ->
        val items = list ?: return@Scaffold
        val upcoming = items.filter { it.endDay >= today }
        val past = items.filter { it.endDay < today }.sortedByDescending { it.startDay }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "On these days EduBuddy pauses class reminders and won't ask for attendance. Your timetable stays as it is.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.BeachAccess, "No breaks added",
                        "Add festival holidays, semester breaks or exam weeks so reminders stay quiet when there's no class.",
                    )
                }
            }
            if (upcoming.isNotEmpty()) {
                item { GroupLabel("Upcoming") }
                items(upcoming, key = { it.id }) { h ->
                    HolidayRow(h, today, onClick = { editing = h }, modifier = Modifier.animateItem())
                }
            }
            if (past.isNotEmpty()) {
                item { GroupLabel("Past") }
                items(past, key = { it.id }) { h ->
                    HolidayRow(h, today, onClick = { editing = h }, modifier = Modifier.animateItem())
                }
            }
        }
    }

    editing?.let { h ->
        HolidayEditor(
            initial = h,
            onSave = { vm.save(it); editing = null },
            onDelete = if (h.id != 0L) ({
                vm.delete(h); editing = null
                messenger.undo("Removed “${h.title}”") { vm.restore(h) }
            }) else null,
            onDismiss = { editing = null },
        )
    }
}

private fun rangeText(h: HolidayEntity): String {
    val s = LocalDate.ofEpochDay(h.startDay)
    val e = LocalDate.ofEpochDay(h.endDay)
    return if (s == e) Fmt.dateShort(s) else "${Fmt.dateShort(s)} – ${Fmt.dateShort(e)}"
}

private fun daysText(h: HolidayEntity): String {
    val n = (h.endDay - h.startDay + 1).toInt()
    return if (n == 1) "1 day" else "$n days"
}

@Composable
private fun HolidayRow(h: HolidayEntity, today: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val past = h.endDay < today
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    EduCard(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).border(1.dp, EduTheme.extra.hairline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(LocalDate.ofEpochDay(h.startDay).dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = if (past) muted else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (past) muted else MaterialTheme.colorScheme.onSurface)
                Text("${rangeText(h)} · ${daysText(h)}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (h.covers(today)) Pill("Now", color = EduTheme.extra.accent)
            else if (!past) {
                val inDays = h.startDay - today
                Text(if (inDays == 1L) "Tomorrow" else "in $inDays d", style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
    }
}

@Composable
private fun HolidayEditor(
    initial: HolidayEntity,
    onSave: (HolidayEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var start by rememberSaveable { mutableStateOf(initial.startDay) }
    var end by rememberSaveable { mutableStateOf(initial.endDay) }
    var picking by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = initial.copy(title = title, startDay = start, endDay = end)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Gutter).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (initial.id == 0L) "Add a break" else "Edit break", style = MaterialTheme.typography.titleLarge)
            EduTextField(
                title, { title = it }, "Name", placeholder = "e.g. Diwali break, Mid-sem exams",
                capitalization = KeyboardCapitalization.Words, maxLength = 60,
            )
            PickerField(
                label = "Dates · ${daysText(draft)}",
                value = rangeText(draft),
                icon = Icons.Rounded.DateRange,
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(enabled = title.isNotBlank(), onClick = { onSave(draft.copy(title = title.trim())) }) { Text("Save") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (picking) {
        fun millis(day: Long) = LocalDate.ofEpochDay(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = millis(start), initialSelectedEndDateMillis = millis(end))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis?.let(::day)
                        if (s != null) {
                            start = s
                            end = state.selectedEndDateMillis?.let(::day) ?: s
                        }
                        picking = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(480.dp), title = {
                Text("Select dates", Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.labelLarge)
            }, showModeToggle = false)
        }
    }
}

