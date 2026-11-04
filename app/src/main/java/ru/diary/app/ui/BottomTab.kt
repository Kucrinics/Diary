package ru.diary.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector, val iconSelected: ImageVector) {
    data object Tasks   : BottomTab("tasks",   ru.diary.app.R.string.tab_tasks,   Icons.Outlined.Checklist,     Icons.Filled.Checklist)
    data object Calendar: BottomTab("calendar",ru.diary.app.R.string.tab_calendar,Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)
    data object Notes   : BottomTab("notes",   ru.diary.app.R.string.tab_notes,   Icons.AutoMirrored.Outlined.Note, Icons.AutoMirrored.Filled.Note)
    data object Settings: BottomTab("settings",ru.diary.app.R.string.tab_settings,Icons.Outlined.Settings,      Icons.Filled.Settings)
}

val bottomTabs: List<BottomTab> = listOf(
    BottomTab.Tasks,
    BottomTab.Calendar,
    BottomTab.Notes,
    BottomTab.Settings
)
