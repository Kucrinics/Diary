package ru.diary.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Star
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.diary.app.data.db.DateUtils
import androidx.compose.ui.res.stringResource
import ru.diary.app.R
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.data.repository.TaskRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: TaskRepository,
    noteRepository: NoteRepository,
    onDateSelected: (String) -> Unit
) {
    val vm: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(repository, noteRepository))
    val state by vm.state.collectAsState()
    var showYearPicker by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "${state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())} ${state.month.year}"
                            .replaceFirstChar { it.uppercase() },
                        modifier = Modifier.clickable { showYearPicker = true }
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { vm.prevYear() }) {
                            Icon(Icons.Default.KeyboardDoubleArrowLeft, contentDescription = stringResource(R.string.calendar_prev_year))
                        }
                        IconButton(onClick = { vm.prevMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.calendar_prev_month))
                        }
                    }
                },
                actions = {
                    Row {
                        IconButton(onClick = { vm.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
                        }
                        IconButton(onClick = { vm.nextYear() }) {
                            Icon(Icons.Default.KeyboardDoubleArrowRight, contentDescription = stringResource(R.string.calendar_next_year))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            val dayHeaders = remember {
                DayOfWeek.entries.toList().let { days ->
                    days.sortedBy { it.value }.map {
                        it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).lowercase(Locale.getDefault())
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                dayHeaders.forEach { d ->
                    Text(
                        text = d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MonthGrid(
                month = state.month,
                busyDates = state.busyDates,
                importantDates = state.importantDates,
                dayMarkers = state.dayMarkers,
                dayColors = state.dayColors,
                today = LocalDate.now(),
                onDateClick = { date -> onDateSelected(DateUtils.toIsoDate(date)) }
            )
        }
    }

    if (showYearPicker) {
        YearMonthPickerDialog(
            current = state.month,
            onSelect = { ym ->
                vm.selectYearMonth(ym)
                showYearPicker = false
            },
            onDismiss = { showYearPicker = false }
        )
    }
}

@Composable
private fun YearMonthPickerDialog(
    current: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableStateOf(current.year) }
    val years = (selectedYear - 5..selectedYear + 5).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selectedYear.toString(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedYear-- }) { Text("<<") }
                    TextButton(onClick = { selectedYear++ }) { Text(">>") }
                }
                val months = (1..12).toList().chunked(3)
                months.forEach { rowMonths ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowMonths.forEach { m ->
                            val ym = YearMonth.of(selectedYear, m)
                            val isSelected = ym == current
                            TextButton(onClick = { onSelect(ym) }) {
                                Text(
                                    text = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                        .replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) } }
    )
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    busyDates: Set<String>,
    importantDates: Set<String>,
    dayMarkers: Map<String, List<Boolean>>,
    dayColors: Map<String, Int>,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value - 1)
    val daysInMonth = month.lengthOfMonth()

    val totalSlots = ((offset + daysInMonth + 6) / 7) * 7
    val rows = totalSlots / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val slot = row * 7 + col
                    val dayNum = slot - offset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val iso = DateUtils.toIsoDate(date)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isBusy = iso in busyDates,
                                isImportant = iso in importantDates,
                                markers = dayMarkers[iso].orEmpty(),
                                dayColor = dayColors[iso],
                                onClick = { onDateClick(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isBusy: Boolean,
    isImportant: Boolean,
    markers: List<Boolean>,
    dayColor: Int?,
    onClick: () -> Unit
) {
    val todayLabel = stringResource(R.string.tasks_today)
    val importantLabel = stringResource(R.string.calendar_important)
    val busyLabel = stringResource(R.string.calendar_has_tasks)
    val dateFormatter = remember(Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())
    }
    val a11yLabel = buildString {
        append(date.format(dateFormatter))
        if (isToday) append(", ").append(todayLabel)
        if (isImportant) append(", ").append(importantLabel)
        else if (isBusy) append(", ").append(busyLabel)
    }

    // Маркеры дня: звезда — важная задача, точка — обычная.
    // Если агрегатов нет (старые данные), откатываемся к прежнему поведению.
    val flags = markers.ifEmpty {
        buildList {
            when {
                isImportant -> add(true)
                isBusy -> add(false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11yLabel }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val circleColor = dayColor?.let { Color(it) }
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = when {
                        circleColor != null -> circleColor
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    },
                    shape = CircleShape
                )
                .then(
                    // Сегодняшний день выделяем рамкой, если он перекрашен задачами.
                    if (circleColor != null && isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = when {
                    // Число контрастно фону круга, залитого цветом задачи.
                    circleColor != null ->
                        if (circleColor.luminance() > 0.5f) Color.Black else Color.White
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(2.dp))
        if (flags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                flags.forEach { important ->
                    if (important) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
