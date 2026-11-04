package ru.diary.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RecurrenceUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.Task
import ru.diary.app.data.model.TaskOccurrences
import ru.diary.app.data.repository.TaskRepository
import ru.diary.app.notifications.AlarmScheduler
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Режимы отображения списка задач. */
enum class TaskViewMode { DAY, WEEK, MONTH, DRAFTS }

/** Задачи одного дня (используется в недельном/месячном представлении). */
data class DayTasks(
    val date: String,
    val tasks: List<Task>
)

/** Состояние экрана задач. */
data class TasksUiState(
    val selectedDate: String = DateUtils.today(),
    val viewMode: TaskViewMode = TaskViewMode.DAY,
    val tasks: List<Task> = emptyList(),
    val dayGroups: List<DayTasks> = emptyList(),
    val rangeLabel: String = "",
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

/** Строка плоского списка: либо заголовок дня, либо задача. */
internal data class ListRow(
    val key: String,
    val date: String,
    val task: Task?
) {
    val isHeader: Boolean get() = task == null

    companion object {
        fun header(date: String): ListRow = ListRow("header_$date", date, null)

        fun task(task: Task, date: String): ListRow = ListRow("task_${date}_${task.id}", date, task)
    }
}

/**
 * ViewModel экрана задач.
 *
 * Наблюдает за задачами в выбранном диапазоне и за шаблонами повторяющихся
 * задач, материализует «виртуальные» вхождения повторов для отображения и
 * выполняет операции над задачами (CRUD, отметки, повторы, сортировка).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    /** Колбэк, вызываемый при изменениях задач (обновление виджета). */
    var onTasksChanged: (() -> Unit)? = null

    private fun notifyWidget() {
        onTasksChanged?.invoke()
    }

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    private val _viewMode = MutableStateFlow(TaskViewMode.DAY)
    private val _searchQuery = MutableStateFlow("")
    private val _state = MutableStateFlow(TasksUiState())

    val state: StateFlow<TasksUiState> = _state

    init {
        viewModelScope.launch {
            performAutoMove()
            combine(
                _selectedDate,
                _viewMode
            ) { date, mode ->
                val (start, end) = rangeFor(date, mode)
                repository.observeBetween(start, end)
            }.flatMapLatest { it }.collect {
                forceRefresh()
            }
        }
        viewModelScope.launch {
            repository.observeRecurringTemplates().collect {
                forceRefresh()
            }
        }
    }

    /** Возвращает ISO-границы диапазона для даты и режима отображения. */
    private fun rangeFor(dateStr: String, mode: TaskViewMode): Pair<String, String> {
        val d = DateUtils.parseDate(dateStr)
        return when (mode) {
            TaskViewMode.DAY -> DateUtils.toIsoDate(d) to DateUtils.toIsoDate(d)
            TaskViewMode.WEEK -> DateUtils.toIsoDate(DateUtils.startOfWeek(d)) to
                DateUtils.toIsoDate(DateUtils.endOfWeek(d))
            TaskViewMode.MONTH -> DateUtils.toIsoDate(DateUtils.startOfMonth(d)) to
                DateUtils.toIsoDate(DateUtils.endOfMonth(d))
            TaskViewMode.DRAFTS -> DateUtils.toIsoDate(d) to DateUtils.toIsoDate(d)
        }
    }

    private suspend fun forceRefresh() {
        val date = _selectedDate.value
        val mode = _viewMode.value
        val (start, end) = rangeFor(date, mode)
        val startDate = DateUtils.parseDate(start)
        val endDate = DateUtils.parseDate(end)
        val templates = repository.observeRecurringTemplates().first()
        val rangeTasks = repository.observeBetween(start, end).first()
        val days = (0..ChronoUnit.DAYS.between(startDate, endDate)).map { startDate.plusDays(it) }
        val groups = days.map { day ->
            val dateStr = DateUtils.toIsoDate(day)
            val dayTasks = rangeTasks.filter { it.date == dateStr }
            DayTasks(dateStr, TaskOccurrences.expandForDate(dayTasks, templates, dateStr))
        }
        val visibleGroups = if (mode == TaskViewMode.DAY) groups
        else groups.filter { it.tasks.isNotEmpty() }
        val q = _searchQuery.value.lowercase(Locale.ROOT)
        val finalGroups = if (q.isBlank()) {
            visibleGroups
        } else {
            visibleGroups
                .map { g ->
                    DayTasks(
                        g.date,
                        g.tasks.filter {
                            it.title.lowercase(Locale.ROOT).contains(q) ||
                                it.description.lowercase(Locale.ROOT).contains(q)
                        }
                    )
                }
                .filter { it.tasks.isNotEmpty() }
        }
        val flatTasks = when (mode) {
            TaskViewMode.DAY -> finalGroups.firstOrNull()?.tasks ?: emptyList()
            TaskViewMode.DRAFTS -> {
                val drafts = repository.observeDrafts().first()
                if (q.isBlank()) drafts
                else drafts.filter {
                    it.title.lowercase(Locale.ROOT).contains(q) ||
                        it.description.lowercase(Locale.ROOT).contains(q)
                }
            }
            else -> emptyList()
        }
        _state.value = TasksUiState(
            selectedDate = date,
            viewMode = mode,
            tasks = flatTasks,
            dayGroups = finalGroups,
            rangeLabel = computeRangeLabel(mode, startDate, endDate),
            isLoading = false,
            searchQuery = _searchQuery.value
        )
    }

    private fun computeRangeLabel(mode: TaskViewMode, start: LocalDate, end: LocalDate): String =
        when (mode) {
            TaskViewMode.DAY ->
                DateUtils.dayOfWeek(start) + ", " + DateUtils.formatDate(DateUtils.toIsoDate(start))
            TaskViewMode.WEEK -> {
                val s = DateUtils.monthDay(start)
                val e = DateUtils.monthDay(end)
                if (start.month == end.month) s + " – " + e else s + " – " + e
            }
            TaskViewMode.MONTH -> DateUtils.monthYear(start)
            TaskViewMode.DRAFTS -> ""
        }

    /**
     * Пересчитывает подпись периода. Подпись зависит от языка интерфейса
     * (`DateUtils` берёт локаль из JVM), поэтому после смены языка её нужно
     * обновить, не дожидаясь перезагрузки списка.
     */
    fun refreshRangeLabel() {
        val mode = _viewMode.value
        val (start, end) = rangeFor(_selectedDate.value, mode)
        val label = computeRangeLabel(mode, DateUtils.parseDate(start), DateUtils.parseDate(end))
        val current = _state.value
        if (current.rangeLabel != label) {
            _state.value = current.copy(rangeLabel = label)
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun setViewMode(mode: TaskViewMode) {
        _viewMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { forceRefresh() }
    }

    fun nextDay() {
        val d = DateUtils.parseDate(_selectedDate.value)
        _selectedDate.value = when (_viewMode.value) {
            TaskViewMode.DAY -> DateUtils.toIsoDate(d.plusDays(1))
            TaskViewMode.WEEK -> DateUtils.toIsoDate(d.plusWeeks(1))
            TaskViewMode.MONTH -> DateUtils.toIsoDate(d.plusMonths(1))
            TaskViewMode.DRAFTS -> DateUtils.toIsoDate(d)
        }
    }

    fun prevDay() {
        val d = DateUtils.parseDate(_selectedDate.value)
        _selectedDate.value = when (_viewMode.value) {
            TaskViewMode.DAY -> DateUtils.toIsoDate(d.minusDays(1))
            TaskViewMode.WEEK -> DateUtils.toIsoDate(d.minusWeeks(1))
            TaskViewMode.MONTH -> DateUtils.toIsoDate(d.minusMonths(1))
            TaskViewMode.DRAFTS -> DateUtils.toIsoDate(d)
        }
    }

    fun goToToday() {
        _selectedDate.value = DateUtils.today()
    }

    fun toggleImportant(task: Task) {
        viewModelScope.launch {
            if (task.id < 0) {
                val currentDate = task.date
                val templateId = TaskOccurrences.templateIdOf(task.id) ?: return@launch
                val template = repository.getById(templateId) ?: return@launch
                repository.deleteGeneratedInstanceAt(templateId, currentDate)
                repository.add(
                    template.copy(
                        id = 0,
                        date = currentDate,
                        isImportant = !template.isImportant,
                        repeatType = RepeatType.NONE.value,
                        repeatOriginId = templateId
                    )
                )
            } else {
                val real = repository.getById(task.id) ?: return@launch
                if (real.repeatType != RepeatType.NONE.value && real.repeatOriginId == null) {
                    repository.deleteGeneratedInstanceAt(real.id, real.date)
                    repository.add(
                        real.copy(
                            id = 0,
                            date = real.date,
                            isImportant = !real.isImportant,
                            repeatType = RepeatType.NONE.value,
                            repeatOriginId = real.id
                        )
                    )
                } else {
                    repository.update(real.copy(isImportant = !real.isImportant))
                }
            }
            notifyWidget()
            forceRefresh()
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            if (task.id < 0) {
                val currentDate = task.date
                val templateId = TaskOccurrences.templateIdOf(task.id) ?: return@launch
                val template = repository.getById(templateId) ?: return@launch
                repository.deleteGeneratedInstanceAt(templateId, currentDate)
                repository.add(
                    template.copy(
                        id = 0,
                        date = currentDate,
                        isCompleted = true,
                        repeatType = RepeatType.NONE.value,
                        repeatOriginId = templateId
                    )
                )
                var nextId: Long? = null
                val nextDate = RecurrenceUtils.nextOccurrenceDate(template, currentDate)
                if (nextDate != null) {
                    repository.deleteGeneratedInstanceAt(templateId, nextDate)
                    nextId = repository.add(
                        template.copy(
                            id = 0,
                            date = nextDate,
                            isCompleted = false,
                            repeatType = RepeatType.NONE.value,
                            repeatOriginId = templateId
                        )
                    )
                }
                alarmScheduler.cancel(templateId.toInt())
                if (template.remind && nextId != null && nextDate != null) {
                    alarmScheduler.schedule(nextId.toInt(), template.title, nextDate, template.time)
                }
            } else {
                val taskInDb = repository.getById(task.id) ?: return@launch
                if (taskInDb.repeatType != RepeatType.NONE.value && taskInDb.repeatOriginId == null) {
                    repository.deleteGeneratedInstanceAt(taskInDb.id, taskInDb.date)
                    repository.add(
                        taskInDb.copy(
                            id = 0,
                            date = taskInDb.date,
                            isCompleted = !taskInDb.isCompleted,
                            repeatType = RepeatType.NONE.value,
                            repeatOriginId = taskInDb.id
                        )
                    )
                    var nextId: Long? = null
                    val nextDate = RecurrenceUtils.nextOccurrenceDate(taskInDb, taskInDb.date)
                    if (nextDate != null) {
                        repository.deleteGeneratedInstanceAt(taskInDb.id, nextDate)
                        nextId = repository.add(
                            taskInDb.copy(
                                id = 0,
                                date = nextDate,
                                isCompleted = false,
                                repeatType = RepeatType.NONE.value,
                                repeatOriginId = taskInDb.id
                            )
                        )
                    }
                    alarmScheduler.cancel(task.id.toInt())
                    if (taskInDb.remind && nextId != null && nextDate != null) {
                        alarmScheduler.schedule(nextId.toInt(), taskInDb.title, nextDate, taskInDb.time)
                    }
                } else {
                    repository.setCompleted(task.id, !task.isCompleted)
                    alarmScheduler.cancel(task.id.toInt())
                    if (task.isCompleted && taskInDb.remind) {
                        alarmScheduler.schedule(task.id.toInt(), taskInDb.title, taskInDb.date, taskInDb.time)
                    }
                }
            }
            notifyWidget()
            forceRefresh()
        }
    }

    private fun scheduleReminder(task: Task) {
        if (task.repeatType != RepeatType.NONE.value) {
            val next = RecurrenceUtils.nextUpcomingOccurrence(task, System.currentTimeMillis())
            if (next != null) {
                alarmScheduler.schedule(task.id.toInt(), task.title, next, task.time)
            }
        } else {
            alarmScheduler.schedule(task.id.toInt(), task.title, task.date, task.time)
        }
    }

    fun addTask(
        title: String,
        description: String,
        date: String,
        time: String?,
        categoryId: Long?,
        color: Int? = null,
        remind: Boolean,
        isImportant: Boolean = false,
        autoMove: Boolean = false,
        repeatType: String = RepeatType.NONE.value,
        repeatIntervalDays: Int? = null,
        repeatDaysOfWeek: Int? = null,
        repeatEndDate: String? = null,
        isDraft: Boolean = false
    ) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
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
            )
            val id = repository.add(task)
            if (remind && !isDraft) {
                scheduleReminder(task.copy(id = id))
            }
            notifyWidget()
            forceRefresh()
        }
    }

    fun getById(id: Long, onResult: (Task?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getById(id))
        }
    }

    fun publishDraft(id: Long, date: String) {
        viewModelScope.launch {
            val task = repository.getById(id) ?: return@launch
            repository.update(task.copy(isDraft = false, date = date))
            notifyWidget()
            forceRefresh()
        }
    }

    /**
     * Сохраняет изменения задачи.
     *
     * Для виртуального вхождения повторяющейся серии ([Task.id] < 0) меняется
     * сам шаблон серии: вхождение не существует в БД, поэтому обычный `@Update`
     * по отрицательному id молча ничего не делал.
     *
     * @param original исходное вхождение (до правки). Нужно, чтобы отличить
     * изменение даты пользователем от обычной правки вхождения на другую дату.
     */
    fun updateTask(task: Task, original: Task? = null) {
        viewModelScope.launch {
            if (task.id < 0) {
                updateSeriesFromOccurrence(task, original) ?: return@launch
            } else {
                updateRealTask(task) ?: return@launch
            }
            notifyWidget()
            forceRefresh()
        }
    }

    /**
     * Обновляет шаблон серии по отредактированному виртуальному вхождению:
     * сохраняет дату-якорь серии, если пользователь не перенёс вхождение,
     * и удаляет только незавершённые материализованные копии.
     */
    private suspend fun updateSeriesFromOccurrence(edited: Task, original: Task?): Unit? {
        val templateId = TaskOccurrences.templateIdOf(edited.id) ?: return null
        val template = repository.getById(templateId) ?: return null
        val dateChanged = original != null && edited.date != original.date
        // Выключение повтора на вхождении должно оставить задачу на этом дне,
        // а не «уехать» в прошлое к исходной дате шаблона.
        val anchorDate = if (dateChanged || edited.repeatType == RepeatType.NONE.value) {
            edited.date
        } else {
            template.date
        }
        val updated = template.copy(
            title = edited.title,
            description = edited.description,
            date = anchorDate,
            time = edited.time,
            categoryId = edited.categoryId,
            color = edited.color,
            remind = edited.remind,
            isImportant = edited.isImportant,
            autoMove = edited.autoMove,
            repeatType = edited.repeatType,
            repeatIntervalDays = edited.repeatIntervalDays,
            repeatDaysOfWeek = edited.repeatDaysOfWeek,
            repeatEndDate = edited.repeatEndDate
        )
        repository.update(updated)
        cancelPendingGenerated(updated.id)
        alarmScheduler.cancel(updated.id.toInt())
        if (updated.remind && !updated.isDraft) {
            scheduleReminder(updated)
        }
        return Unit
    }

    /** Обновляет реальную задачу/шаблон. Завершённые вхождения остаются в истории. */
    private suspend fun updateRealTask(task: Task): Unit? {
        val old = repository.getById(task.id) ?: return null
        val repeating = old.repeatType != RepeatType.NONE.value ||
            task.repeatType != RepeatType.NONE.value
        repository.update(task)
        if (repeating) cancelPendingGenerated(task.id)
        alarmScheduler.cancel(task.id.toInt())
        if (task.remind && !task.isDraft) {
            scheduleReminder(task)
        }
        return Unit
    }

    /** Отменяет будильники и удаляет незавершённые материализованные вхождения. */
    private suspend fun cancelPendingGenerated(templateId: Long) {
        repository.getGeneratedInstances(templateId)
            .filter { !it.isCompleted }
            .forEach { alarmScheduler.cancel(it.id.toInt()) }
        repository.deletePendingGeneratedInstances(templateId)
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            if (task.id < 0) {
                val templateId = TaskOccurrences.templateIdOf(task.id) ?: return@launch
                val template = repository.getById(templateId) ?: return@launch
                repository.deleteGeneratedInstances(templateId)
                repository.update(template.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
                alarmScheduler.cancel(templateId.toInt())
            } else {
                val real = repository.getById(task.id) ?: return@launch
                if (real.repeatType == RepeatType.NONE.value && real.repeatOriginId == null) {
                    repository.update(real.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
                    alarmScheduler.cancel(task.id.toInt())
                } else {
                    val originId = real.repeatOriginId ?: real.id
                    repository.deleteGeneratedInstances(originId)
                    val origin = repository.getById(originId) ?: return@launch
                    repository.update(origin.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
                    alarmScheduler.cancel(originId.toInt())
                }
            }
            notifyWidget()
            forceRefresh()
        }
    }

    fun observeTrash(): Flow<List<Task>> = repository.observeTrash()

    fun restoreTask(id: Long) {
        viewModelScope.launch {
            repository.restore(id)
            notifyWidget()
            forceRefresh()
        }
    }

    fun moveTask(taskId: Long, newDate: String) {
        viewModelScope.launch {
            val realId = TaskOccurrences.templateIdOf(taskId) ?: taskId
            repository.moveToDate(realId, newDate)
            notifyWidget()
            forceRefresh()
        }
    }

    fun copyTask(taskId: Long, newDate: String) {
        viewModelScope.launch {
            val realId = TaskOccurrences.templateIdOf(taskId) ?: taskId
            val original = repository.getById(realId) ?: return@launch
            val copy = original.copy(
                id = 0,
                date = newDate,
                isCompleted = false,
                isDeleted = false,
                deletedAt = null
            )
            repository.add(copy)
            notifyWidget()
            forceRefresh()
        }
    }

    fun moveTaskUp(task: Task) = moveInBundle(task, -1)

    fun moveTaskDown(task: Task) = moveInBundle(task, 1)

    fun reorderTasks(ordered: List<Task>) {
        viewModelScope.launch {
            val mode = _viewMode.value
            val daySeparated = mode != TaskViewMode.DRAFTS
            val byDate: Map<String?, List<Task>> = if (!daySeparated) {
                mapOf(null to ordered)
            } else {
                ordered.filter { it.time == null }.groupBy { it.date }
            }
            val updates = mutableListOf<Pair<Long, Int>>()
            var running = 0
            byDate.values.forEach { tasks ->
                tasks.forEach { t ->
                    val realId = TaskOccurrences.templateIdOf(t.id) ?: t.id
                    updates.add(realId to running)
                    running++
                }
            }
            updates.forEach { (id, order) -> repository.updateSortOrder(id, order) }
            notifyWidget()
            forceRefresh()
        }
    }

    private fun moveInBundle(task: Task, direction: Int) {
        viewModelScope.launch {
            val mode = _viewMode.value
            val base = if (mode == TaskViewMode.DRAFTS) {
                _state.value.tasks
            } else {
                _state.value.dayGroups.firstOrNull { it.date == task.date }?.tasks
                    ?: _state.value.tasks
            }
            val bundle = if (mode == TaskViewMode.DRAFTS) base else base.filter { it.time == null }
            val index = bundle.indexOfFirst { it.id == task.id }
            if (index < 0) return@launch
            val target = index + direction
            if (target < 0 || target >= bundle.size) return@launch
            val mutable = bundle.toMutableList()
            mutable.add(target, mutable.removeAt(index))
            mutable.forEachIndexed { i, t ->
                val realId = TaskOccurrences.templateIdOf(t.id) ?: t.id
                repository.updateSortOrder(realId, i)
            }
            notifyWidget()
            forceRefresh()
        }
    }

    private suspend fun performAutoMove() {
        val today = DateUtils.today()
        // Шаблон повторяющейся серии нельзя переносить: его дата — якорь,
        // от которого считаются все вхождения.
        val overdue = repository.getOverdueAutoMoveTasks(today)
            .filter { it.repeatType == RepeatType.NONE.value }
        overdue.forEach { task ->
            repository.moveToDate(task.id, today)
            alarmScheduler.cancel(task.id.toInt())
            if (task.remind) {
                alarmScheduler.schedule(task.id.toInt(), task.title, today, task.time)
            }
        }
        if (overdue.isNotEmpty()) {
            notifyWidget()
            forceRefresh()
        }
    }

    companion object {
        fun factory(repo: TaskRepository, scheduler: AlarmScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TasksViewModel(repo, scheduler) as T
            }
    }
}
