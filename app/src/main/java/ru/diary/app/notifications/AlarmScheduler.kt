package ru.diary.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.diary.app.data.db.DateUtils

/**
 * Планировщик напоминаний через AlarmManager.
 * Для конкретного момента времени AlarmManager точнее, чем WorkManager.
 *
 * На Android 12+ точные будильники требуют разрешения SCHEDULE_EXACT_ALARM
 * (или USE_EXACT_ALARM, добавлено в манифесте).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Планирует напоминание о задаче.
     * Если время не указано — напоминание сработает в начале дня
     * (время подъёма по умолчанию).
     * @param notificationId уникальный int (используем id задачи).
     * @param title заголовок задачи для уведомления.
     * @param dateIso "yyyy-MM-dd"
     * @param timeIso "HH:mm" или null (тогда DEFAULT_REMINDER_TIME).
     */
    fun schedule(notificationId: Int, title: String, dateIso: String, timeIso: String?) {
        val effectiveTime = timeIso ?: DEFAULT_REMINDER_TIME
        val triggerAtMillis = DateUtils.toEpochMillis(dateIso, effectiveTime) ?: return
        if (triggerAtMillis <= System.currentTimeMillis()) return // не будим в прошлом

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, notificationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DATE, dateIso)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setExactAndAllowWhileIdle — сработает даже в Doze (ценой разового выхода из спячки).
        // На API 31+ нужно проверить, есть ли разрешение на точные будильники.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Разрешения нет — используем неточный будильник. Хуже для Doze, но не падает.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pending
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /** Отменяет запланированное напоминание. */
    fun cancel(notificationId: Int) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DATE = "extra_date"

        /** Время напоминания по умолчанию для задач без указанного времени. */
        const val DEFAULT_REMINDER_TIME = DateUtils.DEFAULT_REMINDER_TIME
    }
}
