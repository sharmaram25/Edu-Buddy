package com.edubuddy.app.ui.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.edubuddy.app.reminders.Notifier
import com.edubuddy.app.ui.common.appContainer

/** Live status of the permissions reminders depend on, refreshed whenever the screen resumes. */
class PermissionState(
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val requestNotifications: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
) {
    val allGood get() = notificationsGranted && exactAlarmsGranted
}

@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val container = appContainer()
    var notif by remember { mutableStateOf(Notifier(context).canPost()) }
    var exact by remember { mutableStateOf(container.reminders.canScheduleExact()) }

    LifecycleResumeEffect(Unit) {
        notif = Notifier(context).canPost()
        exact = container.reminders.canScheduleExact()
        onPauseOrDispose {}
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notif = granted && Notifier(context).canPost()
        // Only send the user to system settings when Android will no longer show the dialog
        // ("Don't allow" twice, or blocked by policy). A first refusal is respected as-is.
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (!canAskAgain) openAppNotificationSettings(context)
        }
    }

    return PermissionState(
        notificationsGranted = notif,
        exactAlarmsGranted = exact,
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && !Notifier(context).hasRuntimePermission()) {
                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    .onFailure { openAppNotificationSettings(context) }
            } else {
                // Permission already held (or not needed): notifications are blocked in system settings.
                openAppNotificationSettings(context)
            }
        },
        openExactAlarmSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }.onFailure { openAppDetails(context) }
            }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppDetails(context) }
}

/** Last-resort fallback for OEM builds that strip the more specific settings screens. */
private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
