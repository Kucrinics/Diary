package ru.diary.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Полностью перезапускает приложение: завершает текущий процесс и планирует
 * повторный запуск launcher-активности через [AlarmManager].
 *
 * Нужно после импорта данных, когда файл базы данных подменяется на диске —
 * чтобы Room открыл уже новую БД в чистом процессе.
 */
fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, flags)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + 300L,
            pendingIntent
        )
    }
    exitProcess(0)
}
