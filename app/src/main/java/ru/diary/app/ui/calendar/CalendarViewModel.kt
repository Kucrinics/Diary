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

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val busyDates: Set<String> = emptySet(),
    val importantDates: Set<String> = emptySet()
)

class CalendarViewModel(
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<CalendarUiState> = combine(
        _month,
        taskRepository.observeDatesWithTasks(),
        taskRepository.observeDatesWithImportantTasks(),
        noteRepository.observeDatesWithImportantNotes(),
        taskRepository.observeRecurringTemplates()
    ) { month, busy, importantTasks, importantNotes, templates ->
        val allBusy = busy.toMutableSet()
        val allImportant = (importantTasks.toSet() + importantNotes.toSet()).toMutableSet()
        templates.forEach { template ->
            val dates = recurringDatesInMonth(template, month)
            allBusy.addAll(dates)
            if (template.isImportant) allImportant.addAll(dates)
        }
        CalendarUiState(month, allBusy, allImportant)
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
