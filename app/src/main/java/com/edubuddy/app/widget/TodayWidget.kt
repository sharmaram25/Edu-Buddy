package com.edubuddy.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.edubuddy.app.EduBuddyApp
import com.edubuddy.app.MainActivity
import com.edubuddy.app.R
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.SystemClock
import com.edubuddy.app.domain.AgendaMath
import com.edubuddy.app.ui.theme.SubjectPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Refreshes the home-screen widget after data changes. Never throws. */
class WidgetUpdater(private val context: Context, private val scope: CoroutineScope) {
    fun refresh() {
        scope.launch { runCatching { TodayWidget().updateAll(context) } }
    }
}

private data class WidgetRow(val color: Int, val title: String, val subtitle: String, val trailing: String)

private data class WidgetData(
    val heading: String,
    val rows: List<WidgetRow>,
    val dueCount: Int,
    val empty: String,
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as EduBuddyApp).container
        val clock = SystemClock
        val s = c.settings.current()
        val now = clock.now()
        val today = clock.today()
        val subjects = c.db.subjects().all().associateBy { it.id }
        val slots = c.db.classSlots().all()
        var date = today
        val holidays = c.db.holidays().all()
        fun on(d: java.time.LocalDate) =
            if (AgendaMath.holidayOn(d, holidays) != null) emptyList() else AgendaMath.classesOn(d, slots, subjects, emptyMap(), clock)
        var classes = on(today).filter { !it.isOver(now) }
        if (classes.isEmpty()) {
            date = today.plusDays(1)
            classes = on(date)
        }
        val holidayToday = AgendaMath.holidayOn(today, holidays)
        val zone = clock.zone()
        val endOfTomorrow = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val due = c.db.tasks().pendingBetween(0, endOfTomorrow)
        val rows = classes.take(3).map {
            WidgetRow(
                color = SubjectPalette.colorArgb(it.subject.colorIndex),
                title = it.subject.name,
                subtitle = listOf(it.slot.kind.label, it.slot.room).filter { x -> x.isNotBlank() }.joinToString(" · "),
                trailing = if (it.isOngoing(now)) "Now" else Fmt.time(it.slot.startMinute, s.use24h),
            )
        }
        return WidgetData(
            heading = if (date == today) "Today · ${Fmt.dateShort(today)}" else "Tomorrow · ${Fmt.dateShort(date)}",
            rows = rows,
            dueCount = due.size,
            empty = when {
                slots.isEmpty() -> "Add your timetable in EduBuddy"
                holidayToday != null -> holidayToday.title
                else -> "No more classes today"
            },
        )
    }

    @Composable
    private fun Content(d: WidgetData) {
        val onSurface = GlanceTheme.colors.onSurface
        val muted = GlanceTheme.colors.onSurfaceVariant
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_edubuddy),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(18.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(d.heading, style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(10.dp))
            if (d.rows.isEmpty()) {
                Text(d.empty, style = TextStyle(color = muted, fontSize = 13.sp))
            } else {
                d.rows.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(GlanceModifier.width(4.dp).height(30.dp).cornerRadius(2.dp).background(ColorProvider(Color(r.color)))) {}
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(r.title, maxLines = 1, style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                            if (r.subtitle.isNotBlank()) Text(r.subtitle, maxLines = 1, style = TextStyle(color = muted, fontSize = 12.sp))
                        }
                        Text(r.trailing, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (d.dueCount > 0) {
                Text(
                    "${d.dueCount} ${if (d.dueCount == 1) "task" else "tasks"} due by tomorrow",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
