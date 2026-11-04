package ru.diary.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.diary.app.DiaryApp
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.RecurrenceUtils
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.TaskRepository

/**
 * После перезагрузки устройства AlarmManager сбрасывается.
 * Перепланируем все будущие напоминания.
 *
 * Для повторяющихся задач напоминание ставится на ближайшее будущее вхождение серии
 * (даже если базовая дата уже прошла), чтобы цепочка уведомлений сохранялась.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = DiaryApp.instance.container
                val scheduler = AlarmScheduler(context)
                val now = System.currentTimeMillis()
                container.taskRepository.getPendingReminders().forEach { task ->
                    armReminder(container.taskRepository, scheduler, task, now)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun armReminder(
        repository: TaskRepository,
        scheduler: AlarmScheduler,
        task: Task,
        now: Long
    ) {
        val isTemplate =
            task.repeatType != RepeatType.NONE.value && task.repeatOriginId == null
        if (isTemplate) {
            if (!task.remind) return
            val next = RecurrenceUtils.nextUpcomingOccurrence(task, now)
            if (next != null) scheduler.schedule(task.id.toInt(), task.title, next, task.time)
            return
        }

        if (task.repeatOriginId != null && task.remind) {
            val template = repository.getById(task.repeatOriginId) ?: return
            if (!template.isDeleted && !template.isDraft &&
                RepeatType.fromValue(template.repeatType) != RepeatType.NONE && template.remind
            ) {
                val next = RecurrenceUtils.nextUpcomingOccurrence(template, now)
                if (next != null) {
                    scheduler.schedule(template.id.toInt(), template.title, next, template.time)
                    return
                }
            }
        }

        // Обычная (не повторяющаяся или без активного шаблона) задача — ставим как раньше.
        val trigger = DateUtils.toEpochMillis(task.date, task.time ?: AlarmScheduler.DEFAULT_REMINDER_TIME)
            ?: return
        if (trigger > now) {
            scheduler.schedule(task.id.toInt(), task.title, task.date, task.time)
        }
    }
}