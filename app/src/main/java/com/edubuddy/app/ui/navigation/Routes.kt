package com.edubuddy.app.ui.navigation

import kotlinx.serialization.Serializable

// Top level
@Serializable data object OnboardingRoute
@Serializable data object TodayRoute
@Serializable data object ScheduleRoute
@Serializable data object TasksRoute
@Serializable data object MoneyRoute
@Serializable data object MoreRoute

// Editors & details. 0 means "new" / "none".
@Serializable data class TaskEditRoute(val id: Long = 0, val subjectId: Long = 0)
@Serializable data class ClassEditRoute(val id: Long = 0, val subjectId: Long = 0, val day: Int = 0)
@Serializable data class EventEditRoute(val id: Long = 0)
@Serializable data class TxnEditRoute(val id: Long = 0, val income: Boolean = false)
@Serializable data object SubjectsRoute
@Serializable data class SubjectDetailRoute(val id: Long)
@Serializable data class SubjectEditRoute(val id: Long = 0)
@Serializable data object FocusRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
@Serializable data object HolidaysRoute
