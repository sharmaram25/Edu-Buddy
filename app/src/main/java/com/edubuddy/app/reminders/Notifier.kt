package com.edubuddy.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.edubuddy.app.MainActivity
import com.edubuddy.app.R

/** Builds and posts all notifications. Channels are created once at app start. */
class Notifier(private val context: Context) {

    enum class Channel(val id: String, val title: String, val description: String, val importance: Int) {
        CLASSES("classes", "Class reminders", "Before each class in your timetable", NotificationManager.IMPORTANCE_HIGH),
        TASKS("tasks", "Deadlines", "Before assignments and other tasks are due", NotificationManager.IMPORTANCE_HIGH),
        EVENTS("events", "Events", "Seminars, guest lectures, exams", NotificationManager.IMPORTANCE_HIGH),
        DIGEST("digest", "Morning summary", "Your day at a glance", NotificationManager.IMPORTANCE_DEFAULT),
        FOCUS("focus", "Focus timer", "When a focus session or break ends", NotificationManager.IMPORTANCE_HIGH),
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(Channel.entries.map {
            NotificationChannel(it.id, it.title, it.importance).apply { description = it.description }
        })
    }

    fun canPost(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() && hasRuntimePermission()

    /** True when the Android 13+ POST_NOTIFICATIONS runtime permission is held (always true below 13). */
    fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(
        notificationId: Int,
        channel: Channel,
        title: String,
        text: String,
        bigText: String? = null,
        openRoute: String? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canPost()) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openRoute != null) putExtra(MainActivity.EXTRA_ROUTE, openRoute)
        }
        val contentIntent = PendingIntent.getActivity(
            context, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_edubuddy)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(if (channel == Channel.DIGEST) NotificationCompat.CATEGORY_RECOMMENDATION else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (channel == Channel.DIGEST) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .apply { actions.forEach { addAction(it) } }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    fun cancel(notificationId: Int) = NotificationManagerCompat.from(context).cancel(notificationId)
}
