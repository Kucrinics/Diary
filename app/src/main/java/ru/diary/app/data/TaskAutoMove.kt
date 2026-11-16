package ru.diary.app.data

import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.repository.TaskRepository
import ru.diary.app.notifications.AlarmScheduler

/**
 * Автоперенос невыполненных задач с включённым «Переносить на след. день».
 *
 * Логика общая для экрана задач ([ru.diary.app.ui.tasks.TasksViewModel]) и
 * фонового полуночного обновления ([ru.diary.app.notifications.DailyRefreshReceiver]):
 * раньше перенос выполнялся только при создании экрана задач, поэтому пока
 * приложение не открывали, просроченные задачи оставались на старой дате —
 * их не было видно в виджете и календаре «сегодня».
 */
object TaskAutoMove {

    /**
     * Переносит просроченные задачи на сегодня и перепланирует их напоминания.
     * Шаблоны повторяющихся серий не трогаются: их дата — якорь расчёта вхождений.
     *
     * @return true, если хотя бы одна задача была перенесена.
     */
    suspend fun moveOverdue(repository: TaskRepository, scheduler: AlarmScheduler): Boolean {
        val today = DateUtils.today()
        val overdue = repository.getOverdueAutoMoveTasks(today)
            .filter { it.repeatType == RepeatType.NONE.value }
        overdue.forEach { task ->
            repository.moveToDate(task.id, today)
            scheduler.cancel(task.id.toInt())
            if (task.remind) {
                scheduler.schedule(task.id.toInt(), task.title, today, task.time)
            }
        }
        return overdue.isNotEmpty()
    }
}
