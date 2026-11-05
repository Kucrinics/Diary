package ru.diary.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RecurrenceUtils
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.data.repository.TaskRepository
import java.time.YearMonth

/** Максимум маркеров (точек/звёзд) на один день календаря. */
private const val MAX_MARKERS = 3

/**
 * Маркер дня в календаре: точка (обычная задача) или звезда (важная),
 * окрашенный в цвет своей задачи; null — стандартный цвет темы.
 */
data class CalendarMarker(
    val isImportant: Boolean,
    val color: Int?
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val busyDates: Set<String> = emptySet(),
    val importantDates: Set<String> = emptySet(),
    /**
     * Маркеры дня: до [MAX_MARKERS] элементов на дату.
     * Важные идут первыми, чтобы звёзды не терялись при обрезке до 3.
     */
    val dayMarkers: Map<String, List<CalendarMarker>> = emptyMap()
)

/** Данные, не зависящие от выбранного месяца (SQL сам фильтрует удалённые и черновики). */
private data class CalendarData(
    val busyDates: List<String>,
    val importantTaskDates: List<String>,
    val importantNoteDates: List<String>,
    val templates: List<Task>,
    val allTasks: List<Task>
)

class CalendarViewModel(
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())

    private val data = combine(
        taskRepository.observeDatesWithTasks(),
        taskRepository.observeDatesWithImportantTasks(),
        noteRepository.observeDatesWithImportantNotes(),
        taskRepository.observeRecurringTemplates(),
        taskRepository.observeAll()
    ) { busy, importantTasks, importantNotes, templates, tasks ->
        CalendarData(busy, importantTasks, importantNotes, templates, tasks)
    }

    val state: StateFlow<CalendarUiState> = combine(_month, data) { month, d ->
        val allBusy = d.busyDates.toMutableSet()
        val allImportant = (d.importantTaskDates.toSet() + d.importantNoteDates.toSet()).toMutableSet()

        // Задачи по дням: каждый маркер хранит важность и цвет своей задачи.
        val markers = HashMap<String, MutableList<CalendarMarker>>()
        d.allTasks.forEach { task ->
            markers.getOrPut(task.date) { mutableListOf() }
                .add(CalendarMarker(task.isImportant, task.color))
        }

        // Повторяющиеся шаблоны: добавляем вхождения в отображаемом месяце.
        d.templates.forEach { template ->
            val dates = recurringDatesInMonth(template, month)
            allBusy.addAll(dates)
            if (template.isImportant) allImportant.addAll(dates)
            dates.forEach { iso ->
                markers.getOrPut(iso) { mutableListOf() }
                    .add(CalendarMarker(template.isImportant, template.color))
            }
        }

        // Важные заметки тоже дают звезду, как и раньше.
        d.importantNoteDates.forEach { iso ->
            markers.getOrPut(iso) { mutableListOf() }.add(CalendarMarker(true, null))
        }

        // Важные первыми, не больше 3 маркеров на день:
        // 1 задача — 1 маркер, 2 — 2, 3 и больше — 3.
        val cappedMarkers = markers.mapValues { (_, items) ->
            (items.filter { it.isImportant } + items.filter { !it.isImportant }).take(MAX_MARKERS)
        }

        CalendarUiState(month, allBusy, allImportant, cappedMarkers)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        CalendarUiState()
    )

    /** ISO-даты вхождений серии внутри отображаемого месяца. */
    private fun recurringDatesInMonth(template: Task, month: YearMonth): Set<String> {
        val result = LinkedHashSet<String>()
        var day = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        while (day <= lastDay) {
            val iso = DateUtils.toIsoDate(day)
            if (RecurrenceUtils.isActiveOnDate(template, iso)) result.add(iso)
            day = day.plusDays(1)
        }
        return result
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextYear() {
        _month.value = _month.value.plusYears(1)
    }

    fun prevYear() {
        _month.value = _month.value.minusYears(1)
    }

    fun selectYearMonth(yearMonth: YearMonth) {
        _month.value = yearMonth
    }

    companion object {
        fun factory(taskRepo: TaskRepository, noteRepo: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CalendarViewModel(taskRepo, noteRepo) as T
            }
    }
}
