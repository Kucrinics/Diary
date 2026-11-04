package ru.diary.app.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Скрывает уведомление, когда закончилось звуковое сопровождение напоминания
 * (та же трёхминутная выдержка, что и у мелодии).
 */
class NotificationAutoCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
        if (id < 0) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
        ReminderSoundPlayer.stop(id)
    }
}
