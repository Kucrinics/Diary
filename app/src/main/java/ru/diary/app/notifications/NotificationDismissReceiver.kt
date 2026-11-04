package ru.diary.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Останавливает звук напоминания при нажатии «Скрыть» (в шторке) и при свайпе.
 * Уведомление при этом не отменяем: запись остаётся в строке уведомлений —
 * её можно открыть тапом или убрать свайпом.
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
        if (id >= 0) {
            ReminderSoundPlayer.stop(id)
        }
    }
}