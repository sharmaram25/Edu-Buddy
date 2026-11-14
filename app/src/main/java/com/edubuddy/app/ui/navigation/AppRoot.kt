package com.edubuddy.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import com.edubuddy.app.ui.feature.search.SearchScreen
import com.edubuddy.app.ui.feature.schedule.HolidaysScreen
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.Messenger
import com.edubuddy.app.ui.feature.focus.FocusScreen
import com.edubuddy.app.ui.feature.money.MoneyScreen
import com.edubuddy.app.ui.feature.money.TransactionEditScreen
import com.edubuddy.app.ui.feature.more.MoreScreen
import com.edubuddy.app.ui.feature.onboarding.OnboardingScreen
import com.edubuddy.app.ui.feature.schedule.ClassEditScreen
import com.edubuddy.app.ui.feature.schedule.EventEditScreen
import com.edubuddy.app.ui.feature.schedule.ScheduleScreen
import com.edubuddy.app.ui.feature.settings.SettingsScreen
import com.edubuddy.app.ui.feature.subjects.SubjectDetailScreen
import com.edubuddy.app.ui.feature.subjects.SubjectEditScreen
import com.edubuddy.app.ui.feature.subjects.SubjectsScreen
import com.edubuddy.app.ui.feature.tasks.TaskEditScreen
import com.edubuddy.app.ui.feature.tasks.TasksScreen
import com.edubuddy.app.ui.feature.today.TodayScreen
import kotlinx.coroutines.flow.StateFlow

private data class Tab(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, "Today", Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    Tab(ScheduleRoute, "Schedule", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    Tab(TasksRoute, "Tasks", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    Tab(MoneyRoute, "Money", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    Tab(MoreRoute, "More", Icons.Outlined.GridView, Icons.Rounded.GridView),
)

@Composable
fun AppRoot(onboarded: Boolean, pendingRoute: StateFlow<String?>, consumeRoute: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbar, scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val showBar = tabs.any { t -> dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true }

    // Deep links from notifications.
    val route by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route, onboarded) {
        val r = route ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        when {
            r == "today" -> nav.switchTab(TodayRoute)
            r == "schedule" -> nav.switchTab(ScheduleRoute)
            r == "focus" -> nav.navigate(FocusRoute) { launchSingleTop = true }
            r.startsWith("task/") -> r.removePrefix("task/").toLongOrNull()?.let {
                nav.switchTab(TasksRoute)
                nav.navigate(TaskEditRoute(id = it))
            }
        }
        consumeRoute()
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.then(if (!showBar) Modifier.navigationBarsPadding() else Modifier)) },
            bottomBar = {
                if (showBar) {
                    androidx.compose.foundation.layout.Column {
                    com.edubuddy.app.ui.common.Hairline()
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            val selected = dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag("tab_${t.label}"),
                            )
                        }
                    }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                NavHost(
                    navController = nav,
                    startDestination = if (onboarded) TodayRoute else OnboardingRoute,
                    enterTransition = { enter() },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { popExit() },
                ) {
                    // Finishing onboarding flips settings.onboarded; MainActivity re-keys AppRoot on that,
                    // which rebuilds the graph with Today as the start destination.
                    composable<OnboardingRoute> { OnboardingScreen() }
                    composable<TodayRoute> { TodayScreen(nav) }
                    composable<ScheduleRoute> { ScheduleScreen(nav) }
                    composable<TasksRoute> { TasksScreen(nav) }
                    composable<MoneyRoute> { MoneyScreen(nav) }
                    composable<MoreRoute> { MoreScreen(nav) }

                    composable<TaskEditRoute> { TaskEditScreen(it.toRoute(), onBack = { nav.popBackStack() }) }
                    composable<ClassEditRoute> { ClassEditScreen(it.toRoute(), onBack = { nav.popBackStack() }) }
                    composable<EventEditRoute> { EventEditScreen(it.toRoute(), onBack = { nav.popBackStack() }) }
                    composable<TxnEditRoute> { TransactionEditScreen(it.toRoute(), onBack = { nav.popBackStack() }) }
                    composable<SubjectsRoute> { SubjectsScreen(nav) }
                    composable<SubjectDetailRoute> { SubjectDetailScreen(it.toRoute<SubjectDetailRoute>().id, nav) }
                    composable<SubjectEditRoute> { SubjectEditScreen(it.toRoute<SubjectEditRoute>().id, onBack = { nav.popBackStack() }, onDeleted = {
                        // The detail screen underneath closes itself once its subject is gone.
                        nav.popBackStack()
                    }) }
                    composable<FocusRoute> { FocusScreen(onBack = { nav.popBackStack() }) }
                    composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable<SearchRoute> { SearchScreen(nav) }
                    composable<HolidaysRoute> { HolidaysScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTab(e: NavBackStackEntry) =
    tabs.any { t -> e.destination.hasRoute(t.route::class) }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter() =
    if (isTab(targetState) && isTab(initialState)) fadeIn(tween(220))
    else fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 14 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit() =
    if (isTab(initialState)) fadeOut(tween(180))
    else fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 14 }

/** Bottom-bar navigation: one copy of each tab, state restored when returning to it. */
fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
