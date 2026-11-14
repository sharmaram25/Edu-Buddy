package com.edubuddy.app.ui.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.BuildConfig
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.backup.BackupException
import com.edubuddy.app.data.backup.BackupFile
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.data.prefs.ThemeMode
import com.edubuddy.app.ui.common.AppLogo
import com.edubuddy.app.ui.common.ConfirmDialog
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduTimePicker
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.ListRow
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.ReminderOptions
import com.edubuddy.app.ui.common.Segmented
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(c: AppContainer) : EduViewModel(c) {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val backup = c.backup
    /** For actions that rebuild the UI (restore, erase) and must not be cancelled by it. */
    val appScope = c.appScope

    fun update(transform: (Settings) -> Settings) = write {
        val before = c.settings.current()
        c.settings.update(transform)
        val after = c.settings.current()
        if (before.dailyDigest != after.dailyDigest || before.dailyDigestMinute != after.dailyDigestMinute) c.reminders.syncDigest()
        if (before.use24h != after.use24h) c.widgets.refresh()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = eduViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = rememberPermissionState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var editProfile by remember { mutableStateOf(false) }
    var pickDigest by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupFile?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var wipeText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val stats = vm.backup.exportTo(uri)
                messenger.show("Backup saved · ${stats.tasks} tasks, ${stats.classes} classes, ${stats.transactions} entries")
            } catch (e: Exception) {
                messenger.show(e.message ?: "Backup failed")
            } finally { busy = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { pendingRestore = vm.backup.readFrom(uri) }
            catch (e: BackupException) { messenger.show(e.message ?: "Couldn't read backup") }
            catch (e: Exception) { messenger.show("Couldn't open the chosen file") }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val n = vm.backup.exportTransactionsCsv(uri)
                messenger.show("Exported $n entries")
            } catch (e: Exception) { messenger.show(e.message ?: "Export failed") }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                GroupLabel("Profile")
                ListRow(
                    title = s.name.ifBlank { "Add your name" },
                    subtitle = listOf(s.course, s.institution).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Course and college" },
                    icon = Icons.Rounded.School,
                    onClick = { editProfile = true },
                )
            }

            item {
                GroupLabel("Reminders")
                if (!perms.notificationsGranted) {
                    ListRow(
                        "Notifications are off", subtitle = "Turn them on to get class, deadline and event reminders",
                        icon = Icons.Rounded.Notifications, iconTint = EduTheme.extra.danger, onClick = perms.requestNotifications,
                        trailing = { FilledTonalButton(onClick = perms.requestNotifications) { Text("Allow") } },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !perms.exactAlarmsGranted) {
                    ListRow(
                        "Precise timing is off", subtitle = "Reminders may arrive a few minutes late. Allow alarms & reminders for exact timing.",
                        icon = Icons.Rounded.Alarm, iconTint = EduTheme.extra.warning, onClick = perms.openExactAlarmSettings,
                        trailing = { FilledTonalButton(onClick = perms.openExactAlarmSettings) { Text("Allow") } },
                    )
                }
                ListRow("Classes", subtitle = "New classes: " + Fmt.reminderLabel(s.defaultClassReminder).lowercase(), icon = Icons.Rounded.School, onClick = { reminderDialog = "class" })
                ListRow("Tasks", subtitle = "New tasks: " + Fmt.reminderLabel(s.defaultTaskReminder).lowercase(), icon = Icons.Rounded.TaskAlt, onClick = { reminderDialog = "task" })
                ListRow("Events", subtitle = "New events: " + Fmt.reminderLabel(s.defaultEventReminder).lowercase(), icon = Icons.Rounded.Event, onClick = { reminderDialog = "event" })
                ListRow(
                    "Morning summary", subtitle = if (s.dailyDigest) "Every day at ${Fmt.time(s.dailyDigestMinute, s.use24h)}" else "Off",
                    icon = Icons.Rounded.WbSunny,
                    onClick = { if (s.dailyDigest) pickDigest = true else vm.update { it.copy(dailyDigest = true) } },
                    trailing = { Switch(checked = s.dailyDigest, onCheckedChange = { v -> vm.update { it.copy(dailyDigest = v) } }) },
                )
            }

            item {
                GroupLabel("Academics")
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HowToReg, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 18.dp).weight(1f)) {
                            Text("Attendance target", style = MaterialTheme.typography.bodyLarge)
                            Text("${s.attendanceTarget}% · used by every subject unless it has its own", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    var t by remember(s.attendanceTarget) { mutableStateOf(s.attendanceTarget.toFloat()) }
                    Slider(
                        value = t, onValueChange = { t = it }, valueRange = 50f..95f, steps = 8,
                        onValueChangeFinished = { vm.update { it.copy(attendanceTarget = t.toInt()) } },
                        modifier = Modifier.padding(start = 52.dp),
                    )
                }
                ListRow(
                    "Show Saturday", subtitle = "In the timetable week strip", icon = Icons.Rounded.CalendarViewWeek,
                    onClick = { vm.update { it.copy(showSaturday = !it.showSaturday) } },
                    trailing = { Switch(checked = s.showSaturday, onCheckedChange = { v -> vm.update { it.copy(showSaturday = v) } }) },
                )
            }

            item {
                GroupLabel("Focus timer")
                StepperRow("Focus length", s.focusMinutes, 5, 180, 5, Icons.Rounded.Timer) { v -> vm.update { it.copy(focusMinutes = v) } }
                StepperRow("Break length", s.breakMinutes, 1, 60, 1, Icons.Rounded.Coffee) { v -> vm.update { it.copy(breakMinutes = v) } }
            }

            item {
                GroupLabel("Appearance")
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    Segmented(ThemeMode.entries, s.themeMode, { when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } }, { m -> vm.update { it.copy(themeMode = m) } })
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListRow(
                        "Match wallpaper colours", subtitle = "Use Material You instead of EduBuddy's palette", icon = Icons.Rounded.Palette,
                        onClick = { vm.update { it.copy(dynamicColor = !it.dynamicColor) } },
                        trailing = { Switch(checked = s.dynamicColor, onCheckedChange = { v -> vm.update { it.copy(dynamicColor = v) } }) },
                    )
                }
                ListRow(
                    "24-hour time", icon = Icons.Rounded.AccessTime,
                    onClick = { vm.update { it.copy(use24h = !it.use24h) } },
                    trailing = { Switch(checked = s.use24h, onCheckedChange = { v -> vm.update { it.copy(use24h = v) } }) },
                )
            }

            item {
                GroupLabel("Privacy & security")
                val canLock = remember {
                    BiometricManager.from(context).canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == BiometricManager.BIOMETRIC_SUCCESS
                }
                ListRow(
                    "App lock", subtitle = if (canLock) "Require fingerprint, face or screen lock to open" else "Set up a screen lock on your phone first",
                    icon = Icons.Rounded.Fingerprint,
                    onClick = if (canLock) ({ vm.update { it.copy(appLock = !it.appLock) } }) else null,
                    trailing = { Switch(checked = s.appLock && canLock, enabled = canLock, onCheckedChange = { v -> vm.update { it.copy(appLock = v) } }) },
                )
                ListRow(
                    "Your data stays on this phone",
                    subtitle = "EduBuddy has no internet permission. Nothing is uploaded, tracked or shared. Use backups to move to a new phone.",
                    icon = Icons.Rounded.Shield, iconTint = EduTheme.extra.success,
                )
            }

            item {
                GroupLabel("Backup & data")
                ListRow("Back up now", subtitle = "Save everything to a file you choose", icon = Icons.Rounded.Backup, onClick = {
                    if (!busy) runCatching { exportLauncher.launch("EduBuddy-backup-${LocalDate.now()}.json") }
                        .onFailure { messenger.show("No file picker available on this device") }
                })
                ListRow("Restore from backup", subtitle = "Replace current data with a backup file", icon = Icons.Rounded.Restore, onClick = {
                    runCatching { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                        .onFailure { messenger.show("No file picker available on this device") }
                })
                ListRow("Export money to CSV", subtitle = "Open in Excel or Google Sheets", icon = Icons.Rounded.TableChart, onClick = {
                    runCatching { csvLauncher.launch("EduBuddy-money-${LocalDate.now()}.csv") }
                        .onFailure { messenger.show("No file picker available on this device") }
                })
                ListRow("Erase all data", subtitle = "Start over. This can't be undone.", icon = Icons.Rounded.DeleteForever, iconTint = MaterialTheme.colorScheme.error, onClick = { confirmWipe = true })
            }

            item {
                GroupLabel("About")
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(size = 52.dp)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("EduBuddy", style = MaterialTheme.typography.titleMedium)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Plus Jakarta Sans font, SIL Open Font License", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ListRow("Your student companion", subtitle = "Timetable, attendance, deadlines, money and focus, all offline.", icon = Icons.Rounded.Info)
            }
        }
    }

    // ---- Dialogs ----
    if (editProfile) {
        var name by remember { mutableStateOf(s.name) }
        var course by remember { mutableStateOf(s.course) }
        var inst by remember { mutableStateOf(s.institution) }
        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text("Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EduTextField(name, { name = it }, "Your name", capitalization = KeyboardCapitalization.Words, maxLength = 50)
                    EduTextField(course, { course = it }, "Course & year", placeholder = "e.g. B.Tech CSE, 2nd year", capitalization = KeyboardCapitalization.Words, maxLength = 60)
                    EduTextField(inst, { inst = it }, "College / university", capitalization = KeyboardCapitalization.Words, maxLength = 80)
                }
            },
            confirmButton = { TextButton(onClick = { vm.update { it.copy(name = name, course = course, institution = inst) }; editProfile = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Cancel") } },
        )
    }
    if (pickDigest) {
        EduTimePicker(TimeMath.timeOf(s.dailyDigestMinute), s.use24h, { t -> vm.update { it.copy(dailyDigestMinute = TimeMath.minuteOfDay(t)) } }, { pickDigest = false }, "Morning summary time")
    }
    reminderDialog?.let { kind ->
        val (current, options) = when (kind) {
            "class" -> s.defaultClassReminder to listOf(-1, 0, 5, 10, 15, 20, 30, 45, 60)
            "task" -> s.defaultTaskReminder to ReminderOptions
            else -> s.defaultEventReminder to ReminderOptions
        }
        AlertDialog(
            onDismissRequest = { reminderDialog = null },
            title = { Text("Default reminder") },
            text = {
                LazyColumn {
                    items(options.size) { i ->
                        val m = options[i]
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = m == current, onClick = {
                                vm.update { when (kind) { "class" -> it.copy(defaultClassReminder = m); "task" -> it.copy(defaultTaskReminder = m); else -> it.copy(defaultEventReminder = m) } }
                                reminderDialog = null
                            })
                            Text(Fmt.reminderLabel(m))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reminderDialog = null }) { Text("Close") } },
        )
    }
    pendingRestore?.let { b ->
        val stats = vm.backup.stats(b)
        ConfirmDialog(
            title = "Restore this backup?",
            message = "From ${Fmt.dateMedium(java.time.Instant.ofEpochMilli(b.exportedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}: " +
                "${stats.subjects} subjects, ${stats.classes} classes, ${stats.tasks} tasks, ${stats.events} events, ${stats.transactions} money entries.\n\n" +
                "This replaces everything currently in EduBuddy.",
            confirmLabel = "Restore", destructive = true,
            onConfirm = {
                vm.appScope.launch {
                    try { vm.backup.restore(b); messenger.show("Backup restored") }
                    catch (e: BackupException) { messenger.show(e.message ?: "Restore failed") }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false; wipeText = "" },
            title = { Text("Erase everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("All subjects, timetable, attendance, tasks, events, money and settings will be permanently deleted. Consider making a backup first.")
                    OutlinedTextField(wipeText, { wipeText = it }, label = { Text("Type ERASE to confirm") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = wipeText.trim().equals("ERASE", ignoreCase = true),
                    onClick = { vm.appScope.launch { vm.backup.wipeEverything() }; confirmWipe = false },
                ) { Text("Erase", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false; wipeText = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepperRow(title: String, value: Int, min: Int, max: Int, step: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: (Int) -> Unit) {
    ListRow(
        title, subtitle = "$value minutes", icon = icon,
        trailing = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.padding(horizontal = 4.dp))
                androidx.compose.material3.OutlinedIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        },
    )
}
