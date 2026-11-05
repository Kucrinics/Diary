package ru.diary.app.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.diary.app.R
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.Task
import ru.diary.app.ui.VoiceInputTextField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDialog(
    initial: Task? = null,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        desc: String,
        date: String,
        time: String?,
        categoryId: Long?,
        color: Int?,
        remind: Boolean,
        isImportant: Boolean,
        autoMove: Boolean,
        repeatType: String,
        repeatIntervalDays: Int?,
        repeatDaysOfWeek: Int?,
        repeatEndDate: String?,
        isDraft: Boolean
    ) -> Unit,
    defaultIsDraft: Boolean = false
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var date by rememberSaveable { mutableStateOf(initial?.date ?: defaultDate) }
    var time by rememberSaveable { mutableStateOf(initial?.time ?: "") }

    var color by rememberSaveable { mutableStateOf(initial?.color) }
    var isImportant by rememberSaveable { mutableStateOf(initial?.isImportant ?: false) }
    var isDraft by rememberSaveable { mutableStateOf(initial?.isDraft ?: defaultIsDraft) }
    var autoMove by rememberSaveable { mutableStateOf(initial?.autoMove ?: false) }
    var repeatType by rememberSaveable { mutableStateOf(initial?.repeatType ?: RepeatType.NONE.value) }
    var repeatIntervalDays by rememberSaveable { mutableStateOf(initial?.repeatIntervalDays?.toString() ?: "") }
    var repeatDaysOfWeek by rememberSaveable { mutableStateOf(initial?.repeatDaysOfWeek ?: 0) }
    var repeatEndDate by rememberSaveable { mutableStateOf(initial?.repeatEndDate ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }
    var repeatExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf("task") }

    val context = LocalContext.current

    fun performSave() {
        val finalTime = time.trim().ifBlank { null }
        val endDate = repeatEndDate.trim().ifBlank { null }
        val intervalDays = if (repeatType == RepeatType.INTERVAL.value) {
            repeatIntervalDays.toIntOrNull()?.takeIf { it > 0 } ?: 1
        } else {
            null
        }
        val daysMask = if (
            repeatType == RepeatType.WEEKLY.value ||
            repeatType == RepeatType.DAYS_OF_WEEK.value
        ) {
            repeatDaysOfWeek.takeIf { it != 0 } ?: weekdayBit(currentWeekday(date))
        } else {
            null
        }
        onSave(
            title.trim(),
            description.trim(),
            date.trim(),
            finalTime,
            initial?.categoryId,
            color,
            finalTime != null,
            isImportant,
            autoMove,
            repeatType,
            intervalDays,
            daysMask,
            endDate,
            isDraft
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Задачу сохраняем в любом случае: запрет уведомлений не должен
        // блокировать сохранение или уводить в настройки с потерей изменений.
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.notification_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
        performSave()
    }

    fun requestNotificationPermissionAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Всегда показываем системный запрос, если разрешение ещё не выдано.
                // При постоянном отказе система сразу вернёт отказ, и задача сохранится.
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        performSave()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val finalTime = time.trim().ifBlank { null }
                    val hasReminder = finalTime != null
                    if (hasReminder) {
                        requestNotificationPermissionAndSave()
                    } else {
                        performSave()
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.task_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.task_cancel))
            }
        },
        title = {
            Text(stringResource(if (initial == null) R.string.tasks_add else R.string.tasks_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceInputTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_field_title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                VoiceInputTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = runCatching { DateUtils.formatDate(date) }.getOrDefault(date),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.task_field_date)) },
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                datePickerTarget = "task"
                                showDatePicker = true
                            }
                    )
                }

                if (showDatePicker) {
                    val pickerIso = if (datePickerTarget == "task") date else repeatEndDate
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = runCatching {
                            DateUtils.toUtcMillis(DateUtils.parseDate(pickerIso))
                        }.getOrNull()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val pickedDate = DateUtils.dateFromUtcMillis(millis)
                                        if (datePickerTarget == "task") {
                                            date = DateUtils.toIsoDate(pickedDate)
                                        } else {
                                            repeatEndDate = DateUtils.toIsoDate(pickedDate)
                                        }
                                    }
                                    showDatePicker = false
                                }
                            ) {
                                Text(stringResource(R.string.common_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(stringResource(R.string.task_cancel))
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (time.isBlank()) stringResource(R.string.task_time_none) else time,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.task_field_time)) },
                        trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true }
                    )
                }

                if (showTimePicker) {
                    val initialHour = time.take(2).toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val initialMinute = time.takeLast(2).toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val timePickerState = rememberTimePickerState(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                                    showTimePicker = false
                                }
                            ) {
                                Text(stringResource(R.string.common_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    time = ""
                                    showTimePicker = false
                                }
                            ) {
                                Text(stringResource(R.string.task_time_none))
                            }
                        },
                        title = { Text(stringResource(R.string.task_field_time)) },
                        text = { TimePicker(state = timePickerState) }
                    )
                }

Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.task_auto_move))
                    Switch(checked = autoMove, onCheckedChange = { autoMove = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.task_important))
                    IconButton(
                        onClick = { isImportant = !isImportant },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(R.string.task_important),
                            tint = if (isImportant) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.task_draft))
                    Switch(checked = isDraft, onCheckedChange = { isDraft = it })
                }

                ExposedDropdownMenuBox(
                    expanded = repeatExpanded,
                    onExpandedChange = { repeatExpanded = it }
                ) {
                    OutlinedTextField(
                        value = repeatLabel(RepeatType.fromValue(repeatType)),
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.task_repeat_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded)
                        },
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = repeatExpanded,
                        onDismissRequest = { repeatExpanded = false }
                    ) {
                        RepeatType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(repeatLabel(type)) },
                                onClick = {
                                    repeatType = type.value
                                    if (
                                        (type == RepeatType.DAYS_OF_WEEK || type == RepeatType.WEEKLY) &&
                                        repeatDaysOfWeek == 0
                                    ) {
                                        repeatDaysOfWeek = weekdayBit(currentWeekday(date))
                                    }
                                    repeatExpanded = false
                                }
                            )
                        }
                    }
                }

                if (repeatType == RepeatType.INTERVAL.value) {
                    OutlinedTextField(
                        value = repeatIntervalDays,
                        onValueChange = { input ->
                            repeatIntervalDays = input.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_repeat_interval_label)) },
                        placeholder = { Text("1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (
                    repeatType == RepeatType.WEEKLY.value ||
                    repeatType == RepeatType.DAYS_OF_WEEK.value
                ) {
                    val weekdayLabels = remember {
                        (1..7).map {
                            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.task_repeat_days_of_week_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            maxItemsInEachRow = 4
                        ) {
                            for (i in 0 until 7) {
                                val selected = (repeatDaysOfWeek and (1 shl i)) != 0
                                FilterChip(
                                    selected = selected,
                                    onClick = { repeatDaysOfWeek = repeatDaysOfWeek xor (1 shl i) },
                                    label = { Text(weekdayLabels[i]) }
                                )
                            }
                        }
                    }
                }

                if (repeatType != RepeatType.NONE.value) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = runCatching { DateUtils.formatDate(repeatEndDate) }.getOrDefault(repeatEndDate),
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            label = { Text(stringResource(R.string.task_repeat_end_label)) },
                            placeholder = { Text(stringResource(R.string.task_repeat_end_placeholder)) },
                            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    datePickerTarget = "repeatEnd"
                                    showDatePicker = true
                                }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.task_color),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskColors.presets.forEach { preset ->
                            val selected = color == preset
                            val swatch = Color(preset)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable { color = if (selected) null else preset },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        FilterChip(
                            selected = color == null,
                            onClick = { color = null },
                            label = { Text(stringResource(R.string.task_color_none)) }
                        )
                    }
                }
            }
        }
    )
}

private fun weekdayBit(isoDayOfWeek: Int): Int = 1 shl (isoDayOfWeek - 1)

private fun currentWeekday(dateIso: String): Int =
    runCatching { DateUtils.parseDate(dateIso).dayOfWeek.value }
        .getOrDefault(LocalDate.now().dayOfWeek.value)

@Composable
private fun repeatLabel(type: RepeatType): String = when (type) {
    RepeatType.NONE -> stringResource(R.string.task_repeat_none)
    RepeatType.DAILY -> stringResource(R.string.task_repeat_daily)
    RepeatType.WEEKLY -> stringResource(R.string.task_repeat_weekly)
    RepeatType.MONTHLY -> stringResource(R.string.task_repeat_monthly)
    RepeatType.YEARLY -> stringResource(R.string.task_repeat_yearly)
    RepeatType.INTERVAL -> stringResource(R.string.task_repeat_interval)
    RepeatType.DAYS_OF_WEEK -> stringResource(R.string.task_repeat_days_of_week)
}
