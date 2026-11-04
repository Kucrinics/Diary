package ru.diary.app.notifications

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.runBlocking
import ru.diary.app.DiaryApp
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.RecurrenceUtils
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.TaskRepository

/**
 * Срабатывает, когда наступает время напоминания.
 * Показывает уведомление о задаче, если она ещё не выполнена и не удалена.
 *
 * Если задача повторяющаяся — автоматически планирует напоминание на следующее
 * вхождение серии, поэтому уведомление приходит на каждый день повтора.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0)
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: return
        val alarmDate = intent.getStringExtra(AlarmScheduler.EXTRA_DATE)

        val repository = DiaryApp.instance.container.taskRepository
        val task = runBlocking { repository.getById(id.toLong()) }
        if (task == null || task.isCompleted || task.isDeleted) return

        // Цепочка повтора: для экземпляра (repeat_origin_id) ведём от его шаблона,
        // для самого шаблона — от него.
        val chain = runBlocking { resolveChain(repository, task, alarmDate) }
        if (chain != null) {
            val completed = runBlocking { repository.isOccurrenceCompleted(chain.id, chain.anchorDate) }
            if (completed) return // день уже выполнен — не напоминаем и не продлеваем цепочку
        }

        // Канал должен существовать, иначе на Android 8+ уведомление не показывается.
        NotificationHelper.createChannel(context)

        val notification = NotificationHelper.build(
            context = context,
            taskId = id,
            title = title
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(id, notification)

        // Экран включён и разблокирован — телефон в работе: озвучиваем напоминание
        // сами (как и при выключенном экране), но с теми же ограничениями —
        // не дольше 3 минут с плавным окончанием. Уведомление скрываем по концу звука.
        if (isUserActive(context) && !ReminderSoundPlayer.isSilent(context)) {
            ReminderSoundPlayer.startFromOffset(
                context,
                id,
                startOffsetMs = CHANNEL_SOUND_OVERLAP_MS
            )
            scheduleAutoCancel(context, id)
        }

        if (chain != null) {
            val nextDate = RecurrenceUtils.nextOccurrenceDate(chain.task, chain.anchorDate)
            if (nextDate != null) {
                AlarmScheduler(context).schedule(chain.id.toInt(), chain.task.title, nextDate, chain.task.time)
            }
        }
    }

    /**
     * Телефон реально в работе — экран включён и не заблокирован:
     * пользователь может сам ответить на напоминание, поэтому уведомление
     * и звук ограничены по времени. Иначе (экран выключен/заблокирован)
     * действует полноэкранное напоминание, а уведомление ждёт действий.
     */
    private fun isUserActive(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        if (!power.isInteractive) return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isKeyguardLocked != true
    }

    private fun scheduleAutoCancel(context: Context, taskId: Int) {
        val pending = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent(context, NotificationAutoCancelReceiver::class.java)
                .putExtra(NotificationHelper.EXTRA_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + NotificationHelper.AUTO_CANCEL_DELAY_MS,
            pending
        )
    }

    private class Chain(val task: Task, val anchorDate: String) {
        val id: Long get() = task.id
    }

    private suspend fun resolveChain(
        repository: TaskRepository,
        task: Task,
        alarmDate: String?
    ): Chain? {
        val anchor = alarmDate ?: task.date
        if (task.repeatType != RepeatType.NONE.value && task.repeatOriginId == null) {
            return if (task.remind) Chain(task, anchor) else null
        }
        if (task.repeatOriginId != null) {
            val template = repository.getById(task.repeatOriginId) ?: return null
            if (template.isCompleted || template.isDeleted || template.isDraft) return null
            if (RepeatType.fromValue(template.repeatType) == RepeatType.NONE) return null
            if (!template.remind) return null
            return Chain(template, anchor)
        }
        return null
    }

    companion object {
        /** Примерный перехлёст: сколько первой секунды мелодии уже отыграл канал. */
        private const val CHANNEL_SOUND_OVERLAP_MS = 2_000L
    }
}