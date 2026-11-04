package ru.diary.app.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.diary.app.R
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.TaskRepository
import ru.diary.app.notifications.AlarmScheduler
import ru.diary.app.widget.TasksWidgetProvider
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    repository: TaskRepository,
    alarmScheduler: AlarmScheduler,
    initialDate: String? = null,
    onOpenTrash: (() -> Unit)? = null
) {
    val vm: TasksViewModel = viewModel(factory = TasksViewModel.factory(repository, alarmScheduler))
    val uiState by vm.state.collectAsState()
    val appContext = LocalContext.current.applicationContext

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var movingTask by remember { mutableStateOf<Task?>(null) }
    var copyingTask by remember { mutableStateOf<Task?>(null) }
    var publishingTask by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) {
        vm.onTasksChanged = { TasksWidgetProvider.refresh(appContext) }
    }

    LaunchedEffect(initialDate) {
        if (!initialDate.isNullOrBlank()) vm.selectDate(initialDate)
    }

    // Подпись периода кэшируется во ViewModel и после смены языка осталась бы
    // на прежнем языке — пересчитываем её при каждом изменении локали.
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { vm.refreshRangeLabel() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.tasks_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (uiState.viewMode == TaskViewMode.DRAFTS) {
                                stringResource(R.string.tasks_view_drafts)
                            } else {
                                uiState.rangeLabel
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.prevDay() }) {
                        Icon(Icons.Default.ChevronLeft, stringResource(R.string.tasks_prev_day))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.nextDay() }) {
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.tasks_next_day))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.tasks_add),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 56.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                ViewModeSelector(uiState.viewMode) { vm.setViewMode(it) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        uiState.viewMode == TaskViewMode.DRAFTS -> {
                            if (uiState.tasks.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.tasks_drafts_empty),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                val localDrafts = remember(uiState.tasks) {
                                    mutableStateListOf<Task>().apply { addAll(uiState.tasks) }
                                }
                                val draftsListState = rememberLazyListState()
                                val draftsReorderState = rememberReorderableLazyListState(draftsListState) { from, to ->
                                    localDrafts.add(to.index, localDrafts.removeAt(from.index))
                                }
                                LaunchedEffect(localDrafts.toList()) {
                                    if (localDrafts.toList() == uiState.tasks) return@LaunchedEffect
                                    delay(500)
                                    vm.reorderTasks(localDrafts.toList())
                                }
                                LazyColumn(
                                    state = draftsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(localDrafts, key = { it.id }) { draft ->
                                        ReorderableItem(draftsReorderState, key = draft.id) { isDragging ->
                                            DraftCard(
                                                task = draft,
                                                onEdit = { editingTask = draft },
                                                onPublish = { publishingTask = draft },
                                                onDelete = { vm.deleteTask(draft) },
                                                dragModifier = Modifier.draggableHandle(),
                                                isDragging = isDragging
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        uiState.viewMode == TaskViewMode.DAY -> {
                            val localTasks = remember(uiState.tasks) {
                                mutableStateListOf<Task>().apply { addAll(uiState.tasks) }
                            }
                            val dayListState = rememberLazyListState()
                            val dayReorderState = rememberReorderableLazyListState(dayListState) { from, to ->
                                localTasks.add(to.index, localTasks.removeAt(from.index))
                            }
                            LaunchedEffect(localTasks.toList()) {
                                if (localTasks.toList() == uiState.tasks) return@LaunchedEffect
                                delay(500)
                                vm.reorderTasks(localTasks.toList())
                            }
                            LazyColumn(
                                state = dayListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(localTasks, key = { it.id }) { task ->
                                    ReorderableItem(dayReorderState, key = task.id) { isDragging ->
                                        TaskRow(
                                            task = task,
                                            onToggle = { vm.toggleCompleted(task) },
                                            onToggleImportant = { vm.toggleImportant(task) },
                                            onEdit = { editingTask = task },
                                            onMove = { movingTask = task },
                                            onCopy = { copyingTask = task },
                                            onDelete = { vm.deleteTask(task) },
                                            // Задачи со временем всегда сортируются по времени,
                                            // поэтому перетаскивание для них недоступно.
                                            dragModifier = if (task.time == null) {
                                                Modifier.draggableHandle()
                                            } else {
                                                Modifier
                                            },
                                            isDragging = isDragging
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            if (uiState.dayGroups.all { it.tasks.isEmpty() }) {
                                Text(
                                    text = stringResource(R.string.tasks_empty),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                val rows = remember(uiState.dayGroups) {
                                    uiState.dayGroups.flatMap { group ->
                                        listOf(ListRow.header(group.date)) +
                                            group.tasks.map { ListRow.task(it, group.date) }
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(rows, key = { it.key }) { row ->
                                        val task = row.task
                                        if (task == null) {
                                            DayHeader(row.date) { vm.selectDate(row.date) }
                                        } else {
                                            TaskRow(
                                                task = task,
                                                onToggle = { vm.toggleCompleted(task) },
                                                onToggleImportant = { vm.toggleImportant(task) },
                                                onEdit = { editingTask = task },
                                                onMove = { movingTask = task },
                                                onCopy = { copyingTask = task },
                                                onDelete = { vm.deleteTask(task) },
                                                onMoveUp = { vm.moveTaskUp(task) },
                                                onMoveDown = { vm.moveTaskDown(task) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { onOpenTrash?.invoke() },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).size(40.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.trash_title),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showAddDialog) {
        TaskDialog(
            initial = null,
            defaultDate = uiState.selectedDate,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, date, time, categoryId, color, remind, isImportant, autoMove, repeatType, repeatIntervalDays, repeatDaysOfWeek, repeatEndDate, isDraft ->
                vm.addTask(
                    title = title,
                    description = desc,
                    date = date,
                    time = time,
                    categoryId = categoryId,
                    color = color,
                    remind = remind,
                    isImportant = isImportant,
                    autoMove = autoMove,
                    repeatType = repeatType,
                    repeatIntervalDays = repeatIntervalDays,
                    repeatDaysOfWeek = repeatDaysOfWeek,
                    repeatEndDate = repeatEndDate,
                    isDraft = isDraft
                )
                showAddDialog = false
            },
            defaultIsDraft = uiState.viewMode == TaskViewMode.DRAFTS
        )
    }

    editingTask?.let { task ->
        TaskDialog(
            initial = task,
            defaultDate = task.date,
            onDismiss = { editingTask = null },
            onSave = { title, desc, date, time, categoryId, color, remind, isImportant, autoMove, repeatType, repeatIntervalDays, repeatDaysOfWeek, repeatEndDate, isDraft ->
                vm.updateTask(
                    task = task.copy(
                        title = title,
                        description = desc,
                        date = date,
                        time = time,
                        categoryId = categoryId,
                        color = color,
                        remind = remind,
                        isImportant = isImportant,
                        isDraft = isDraft,
                        autoMove = autoMove,
                        repeatType = repeatType,
                        repeatIntervalDays = repeatIntervalDays,
                        repeatDaysOfWeek = repeatDaysOfWeek,
                        repeatEndDate = repeatEndDate
                    ),
                    original = task
                )
                editingTask = null
            },
            defaultIsDraft = false
        )
    }

    movingTask?.let { task ->
        DatePickerDialogMinimal(
            title = stringResource(R.string.tasks_move),
            initialDateMillis = DateUtils.toUtcMillis(DateUtils.parseDate(task.date)),
            onDismiss = { movingTask = null },
            onConfirm = { millis ->
                vm.moveTask(task.id, isoDateFromUtcMillis(millis))
                movingTask = null
            }
        )
    }

    copyingTask?.let { task ->
        DatePickerDialogMinimal(
            title = stringResource(R.string.tasks_copy),
            initialDateMillis = DateUtils.toUtcMillis(DateUtils.parseDate(task.date)),
            onDismiss = { copyingTask = null },
            onConfirm = { millis ->
                vm.copyTask(task.id, isoDateFromUtcMillis(millis))
                copyingTask = null
            }
        )
    }

    publishingTask?.let { task ->
        DatePickerDialogMinimal(
            title = stringResource(R.string.tasks_draft_publish),
            initialDateMillis = DateUtils.toUtcMillis(DateUtils.parseDate(task.date)),
            onDismiss = { publishingTask = null },
            onConfirm = { millis ->
                vm.publishDraft(task.id, isoDateFromUtcMillis(millis))
                publishingTask = null
            }
        )
    }
}

private fun isoDateFromUtcMillis(millis: Long): String =
    DateUtils.toIsoDate(DateUtils.dateFromUtcMillis(millis))

@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onToggleImportant: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    canReorder: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDragging) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(start = 4.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dragModifier != Modifier) {
            IconButton(
                onClick = {},
                modifier = dragModifier.then(Modifier.size(36.dp))
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.tasks_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onToggleImportant,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (task.isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.task_important),
                tint = if (task.isImportant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                task.color?.let { color ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(color), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    )
                )
                if (task.time != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.time,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (task.repeatType != RepeatType.NONE.value || task.repeatOriginId != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Text("\u22EE")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_move)) },
                    leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMove()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_copy)) },
                    leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = stringResource(R.string.tasks_copy)) },
                    onClick = {
                        menuOpen = false
                        onCopy()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun DraftCard(
    task: Task,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (isDragging) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dragModifier != Modifier) {
                IconButton(
                    onClick = {},
                    modifier = dragModifier.then(Modifier.size(36.dp))
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.tasks_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    task.color?.let { color ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(color), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = task.title.ifBlank { stringResource(R.string.trash_no_title) },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Text("\u22EE")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_draft_publish)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onPublish()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogMinimal(
    title: String,
    initialDateMillis: Long = DateUtils.toUtcMillis(DateUtils.parseDate(DateUtils.today())),
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis ?: initialDateMillis) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.task_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSelector(
    selected: TaskViewMode,
    onSelect: (TaskViewMode) -> Unit
) {
    val modes = listOf(
        TaskViewMode.DAY to stringResource(R.string.tasks_view_day),
        TaskViewMode.WEEK to stringResource(R.string.tasks_view_week),
        TaskViewMode.MONTH to stringResource(R.string.tasks_view_month),
        TaskViewMode.DRAFTS to stringResource(R.string.tasks_view_drafts)
    )
    val borderWidth = 1.dp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val weights = modes.map { (_, label) ->
        with(density) {
            textMeasurer.measure(
                text = label,
                style = MaterialTheme.typography.labelLarge
            ).size.width.toDp().value + 24f
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(-borderWidth),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEachIndexed { index, (mode, label) ->
            val isSelected = mode == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface else Color.Transparent,
                label = "viewModeContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "viewModeContent"
            )
            Surface(
                selected = isSelected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(weights[index].coerceAtLeast(1f)).height(56.dp),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(borderWidth, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    date: String,
    onDateClick: () -> Unit
) {
    val localDate = DateUtils.parseDate(date)
    val isToday = date == DateUtils.today()
    val weekday = localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDateClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isToday) {
                stringResource(R.string.tasks_today) + " \u2022 " + weekday + ", " + DateUtils.monthDay(localDate)
            } else {
                weekday + ", " + DateUtils.monthDay(localDate)
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
