package com.edubuddy.app.ui.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.BuildConfig
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.ui.common.BrandMark
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.GroupLabel
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.Hairline
import com.edubuddy.app.ui.common.ListRow
import com.edubuddy.app.ui.common.PageHeader
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.navigation.EventEditRoute
import com.edubuddy.app.ui.navigation.FocusRoute
import com.edubuddy.app.ui.navigation.HolidaysRoute
import com.edubuddy.app.ui.navigation.SearchRoute
import com.edubuddy.app.ui.navigation.SettingsRoute
import com.edubuddy.app.ui.navigation.SubjectsRoute
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MoreState(val settings: Settings = Settings(), val subjects: Int = 0, val tasksDone: Int = 0, val focusMinutes: Int = 0)

class MoreViewModel(c: AppContainer) : EduViewModel(c) {
    val state = combine(c.settings.settings, c.subjects.subjects, c.tasks.tasks, c.db.focus().observeSince(0)) { s, subs, tasks, focus ->
        MoreState(s, subs.count { !it.archived }, tasks.count { it.completedAt != null }, focus.sumOf { it.minutes })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())
}

@Composable
fun MoreScreen(nav: NavHostController) {
    val vm = eduViewModel { MoreViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st.settings

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars)) }
        item { PageHeader("More") }
        item {
            EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter), onClick = { nav.navigate(SettingsRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOf(s.course, s.institution).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(sub.ifBlank { "Add your course and college" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(st.subjects.toString(), "Subjects", Modifier.weight(1f))
                    Stat(st.tasksDone.toString(), "Tasks done", Modifier.weight(1f))
                    Stat(if (st.focusMinutes >= 60) "${st.focusMinutes / 60}h" else "${st.focusMinutes}m", "Focused", Modifier.weight(1f))
                }
            }
        }

        item { GroupLabel("Study") }
        item {
            Group {
                Entry(Icons.Outlined.HowToReg, "Subjects & attendance", "Percentages and how many you can miss") { nav.navigate(SubjectsRoute) }
                Hairline(start = 60.dp)
                Entry(Icons.Outlined.Timer, "Focus timer", "Timed sessions, tracked per subject") { nav.navigate(FocusRoute) }
                Hairline(start = 60.dp)
                Entry(Icons.Outlined.Event, "Add an event", "Seminar, guest lecture, workshop or exam") { nav.navigate(EventEditRoute()) }
                Hairline(start = 60.dp)
                Entry(Icons.Outlined.BeachAccess, "Holidays & breaks", "Pause class reminders on days off") { nav.navigate(HolidaysRoute) }
            }
        }
        item { GroupLabel("App") }
        item {
            Group {
                Entry(Icons.Outlined.Search, "Search", "Find any task, subject, event or expense") { nav.navigate(SearchRoute) }
                Hairline(start = 60.dp)
                Entry(Icons.Outlined.Settings, "Settings", "Reminders, appearance, backup, app lock") { nav.navigate(SettingsRoute) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(size = 18.dp, color = MaterialTheme.colorScheme.outline, dot = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("EduBuddy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(EduTheme.extra.hero),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) BrandMark(size = 20.dp, color = EduTheme.extra.onHero)
        else Text(initials, style = MaterialTheme.typography.titleMedium, color = EduTheme.extra.onHero)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter), contentPadding = PaddingValues(0.dp)) { content() }
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListRow(
        title = title, subtitle = subtitle, icon = icon, onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) },
    )
}
