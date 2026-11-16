package ru.diary.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.diary.app.DiaryApp
import ru.diary.app.data.TaskAutoMove
import ru.diary.app.widget.TasksWidgetProvider
import java.time.ZoneId

/**
 * Полуночное фоновое обновление дня.
 *
 * Виджет «Задачи на сегодня» перерисовывается только по `updatePeriodMillis`
 * (система может задерживать его на часы, особенно ночью в Doze) и при работе
 * с задачами в приложении, поэтому после полуночи он иногда продолжал
 * показывать вчерашний список. Кроме того, автоперенос просроченных задач
 * раньше выполнялся только при создании экрана задач — пока приложение не
 * открывали, задачи с «Переносить на след. день» оставались на старой дате.
 *
 * Ресивер срабатывает вскоре после полуночи: переносит просроченные задачи на
 * новый день, перерисовывает виджет и планирует следующий такой же запуск.
 */
class DailyRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Перепланируем следующий полуночный запуск сразу — onReceive должен
        // успеть это сделать до возврата, иначе цепочка полуночных обновлений прервётся.
        scheduleNext(context.applicationContext)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as? DiaryApp)?.container ?: return@launch
                TaskAutoMove.moveOverdue(container.taskRepository, container.alarmScheduler)
                TasksWidgetProvider.refresh(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Небольшой запас после полуночи, чтобы день уже точно сменился. */
        internal const val AFTER_MIDNIGHT_MS = 90_000L

        private const val REQUEST_CODE = 42

        /**
         * Момент ближайшей полуночи (+ запас) в миллисекундах UTC-шкалы.
         * Отдельная чистая функция — для юнит-теста.
         */
        internal fun nextTriggerMillis(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
            val tomorrowMidnight = java.time.Instant.ofEpochMilli(nowMillis)
                .atZone(zone).toLocalDate().plusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            return tomorrowMidnight + AFTER_MIDNIGHT_MS
        }

        /** Ставит будильник на ближайшую полночь. Вызывается при старте приложения, после перезагрузки и после каждого срабатывания. */
        fun scheduleNext(context: Context) {
            val triggerAt = nextTriggerMillis(System.currentTimeMillis())
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, DailyRefreshReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }
}
