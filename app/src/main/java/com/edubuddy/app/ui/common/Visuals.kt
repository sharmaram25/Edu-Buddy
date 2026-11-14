package com.edubuddy.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.edubuddy.app.data.db.ClassKind
import com.edubuddy.app.data.db.EventKind
import com.edubuddy.app.data.db.TaskKind

data class CategoryVisual(val icon: ImageVector, val color: Color)

object CategoryVisuals {
    private val map = mapOf(
        "food" to CategoryVisual(Icons.Rounded.Restaurant, Color(0xFFD5653E)),
        "transport" to CategoryVisual(Icons.Rounded.DirectionsBus, Color(0xFF3B87C9)),
        "study" to CategoryVisual(Icons.AutoMirrored.Rounded.MenuBook, Color(0xFF5468D4)),
        "rent" to CategoryVisual(Icons.Rounded.Home, Color(0xFF8460C2)),
        "recharge" to CategoryVisual(Icons.Rounded.Smartphone, Color(0xFF22957F)),
        "shopping" to CategoryVisual(Icons.Rounded.Checkroom, Color(0xFFC9547A)),
        "fun" to CategoryVisual(Icons.Rounded.SportsEsports, Color(0xFFB98A25)),
        "health" to CategoryVisual(Icons.Rounded.LocalHospital, Color(0xFFB8463A)),
        "other" to CategoryVisual(Icons.Rounded.MoreHoriz, Color(0xFF8A8882)),
        "pocket_money" to CategoryVisual(Icons.Rounded.AccountBalanceWallet, Color(0xFF1F8A5B)),
        "scholarship" to CategoryVisual(Icons.Rounded.EmojiEvents, Color(0xFF5F8F45)),
        "part_time" to CategoryVisual(Icons.Rounded.Work, Color(0xFF3B87C9)),
        "gift" to CategoryVisual(Icons.Rounded.CardGiftcard, Color(0xFFC9547A)),
        "other_income" to CategoryVisual(Icons.Rounded.Payments, Color(0xFF8A8882)),
    )

    fun of(key: String): CategoryVisual = map[key] ?: CategoryVisual(Icons.Rounded.MoreHoriz, Color(0xFF8A8882))
}

fun TaskKind.icon(): ImageVector = when (this) {
    TaskKind.ASSIGNMENT -> Icons.AutoMirrored.Rounded.Assignment
    TaskKind.PROJECT -> Icons.Rounded.Workspaces
    TaskKind.EXAM -> Icons.Rounded.Quiz
    TaskKind.READING -> Icons.Rounded.AutoStories
    TaskKind.OTHER -> Icons.Rounded.MoreHoriz
}

fun EventKind.icon(): ImageVector = when (this) {
    EventKind.SEMINAR -> Icons.Rounded.Groups
    EventKind.GUEST_LECTURE -> Icons.Rounded.Mic
    EventKind.EXAM -> Icons.Rounded.Quiz
    EventKind.WORKSHOP -> Icons.Rounded.Build
    EventKind.EXTRA_CLASS -> Icons.Rounded.School
    EventKind.OTHER -> Icons.Rounded.Celebration
}

fun ClassKind.icon(): ImageVector = when (this) {
    ClassKind.LECTURE -> Icons.Rounded.School
    ClassKind.LAB -> Icons.Rounded.Science
    ClassKind.TUTORIAL -> Icons.Rounded.Groups
    ClassKind.OTHER -> Icons.Rounded.Event
}
