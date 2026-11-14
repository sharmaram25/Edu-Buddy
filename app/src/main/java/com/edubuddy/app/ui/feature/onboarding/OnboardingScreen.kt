package com.edubuddy.app.ui.feature.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edubuddy.app.data.backup.BackupException
import com.edubuddy.app.ui.common.Wordmark
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.IconBadge
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.appContainer
import com.edubuddy.app.ui.feature.settings.rememberPermissionState
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var college by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableFloatStateOf(75f) }

    BackHandler(enabled = step > 0) { step-- }

    // Both actions flip settings.onboarded, which rebuilds the UI and would cancel a screen-scoped
    // coroutine half-way, so they run on the app scope.
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) container.appScope.launch {
            try {
                val b = container.backup.readFrom(uri)
                container.backup.restore(b) // sets onboarded = true → app switches to Today
            } catch (e: BackupException) {
                messenger.show(e.message ?: "Couldn't restore")
            } catch (e: Exception) {
                // e.g. the file provider revoked access or the file vanished.
                messenger.show("Couldn't open the chosen file")
            }
        }
    }

    fun finish() = container.appScope.launch {
        container.settings.update {
            it.copy(onboarded = true, name = name, course = course, institution = college, attendanceTarget = target.toInt())
        }
        container.reminders.rescheduleAll()
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 4 } + fadeOut())
        },
        label = "onboarding",
    ) { s ->
        when (s) {
            0 -> Welcome(onStart = { step = 1 }, onRestore = {
                runCatching { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                    .onFailure { messenger.show("No file picker available on this device") }
            })
            1 -> StepScaffold(
                stepIndex = 1, title = "About you", subtitle = "Used for greetings only. It stays on this phone.",
                primary = "Continue", onPrimary = { step = 2 }, secondary = "Skip", onSecondary = { step = 2 },
            ) {
                EduTextField(name, { name = it }, "Your name", placeholder = "e.g. Ananya", capitalization = KeyboardCapitalization.Words, maxLength = 50)
                EduTextField(course, { course = it }, "Course & year", placeholder = "e.g. B.Sc Physics, 1st year", capitalization = KeyboardCapitalization.Words, maxLength = 60)
                EduTextField(college, { college = it }, "College / university", placeholder = "Optional", capitalization = KeyboardCapitalization.Words, maxLength = 80)
            }
            else -> {
                val perms = rememberPermissionState()
                StepScaffold(
                    stepIndex = 2, title = "Set up reminders", subtitle = "EduBuddy reminds you before classes, deadlines and events.",
                    primary = "Start using EduBuddy", onPrimary = { finish() },
                ) {
                    EduCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.HowToReg, MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Minimum attendance", style = MaterialTheme.typography.titleSmall)
                                Text("Most colleges require ${target.toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${target.toInt()}%", style = MaterialTheme.typography.titleLarge)
                        }
                        Slider(value = target, onValueChange = { target = it }, valueRange = 50f..95f, steps = 8)
                    }
                    PermissionRow(
                        Icons.Rounded.Notifications, "Notifications", "So reminders can reach you",
                        granted = perms.notificationsGranted, onGrant = perms.requestNotifications,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow(
                            Icons.Rounded.Alarm, "Alarms & reminders", "So reminders arrive exactly on time",
                            granted = perms.exactAlarmsGranted, onGrant = perms.openExactAlarmSettings,
                        )
                    }
                    Text(
                        "You can change these any time in Settings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    // One-time staggered entrance: mark, headline, then the list. Cheap (alpha/offset only).
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    fun Modifier.stagger(i: Int) = this.graphicsLayer {
        val t = ((appear.value - i * 0.12f) / 0.5f).coerceIn(0f, 1f)
        alpha = t
        translationY = (1f - t) * 24.dp.toPx()
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Wordmark(Modifier.stagger(0), markSize = 26.dp)
        Spacer(Modifier.height(56.dp))
        Text(
            "Your semester,\nin order.",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.stagger(1),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Classes, attendance, deadlines and money in one calm place. Private and offline. No account.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.stagger(2),
        )
        Spacer(Modifier.height(40.dp))
        Column(Modifier.stagger(3)) {
            Feature(Icons.Rounded.CalendarMonth, "Timetable", "A reminder before every class")
            Feature(Icons.Rounded.HowToReg, "Attendance", "Know exactly how many you can miss")
            Feature(Icons.Rounded.TaskAlt, "Deadlines", "Assignments, projects and exams")
            Feature(Icons.Rounded.Payments, "Money", "A budget with a daily allowance")
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp).stagger(4),
            shape = MaterialTheme.shapes.large,
        ) { Text("Get started", style = MaterialTheme.typography.titleSmall) }
        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Restore from a backup", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepScaffold(
    stepIndex: Int,
    title: String,
    subtitle: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i ->
                    Box(
                        Modifier.height(3.dp).width(if (i + 1 == stepIndex) 28.dp else 14.dp).clip(CircleShape)
                            .background(if (i + 1 <= stepIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            content()
        }
        Row(Modifier.fillMaxWidth().padding(Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secondary != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.height(56.dp)) { Text(secondary) }
            }
            Button(onClick = onPrimary, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.large) { Text(primary, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    EduCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (granted) EduTheme.extra.success else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) Icon(Icons.Rounded.CheckCircle, "Allowed", tint = EduTheme.extra.success)
            else androidx.compose.material3.OutlinedButton(onClick = onGrant) { Text("Allow") }
        }
    }
}
