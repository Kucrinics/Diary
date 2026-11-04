package ru.diary.app.notifications

import android.app.Notification
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import ru.diary.app.MainActivity
import ru.diary.app.R
import ru.diary.app.data.settings.ReminderSounds
import ru.diary.app.data.settings.SettingsManager

object NotificationHelper {

    /** Канал для звука по умолчанию. Для других звуков id зависит от выбора. */
    const val CHANNEL_ID = "tasks_reminders_alarm_default"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_TITLE = "extra_title"

    private const val CHANNEL_ID_PREFIX = "tasks_reminders_alarm"

    /** Через сколько самоубирается уведомление, если экран включён. */
    const val AUTO_CANCEL_DELAY_MS = ReminderSoundPlayer.MAX_PLAY_MS

    private const val LEGACY_CHANNEL_ID = "tasks_reminders"
    private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 1000)

    /**
     * Id канала зависит от выбранного звука: Android не даёт изменить звук
     * уже созданного канала, поэтому при смене мелодии создаётся новый канал,
     * а прежние каналы приложения удаляются.
     */
    fun channelId(context: Context): String {
        val value = SettingsManager(context).currentReminderSound()
        val suffix = when (ReminderSounds.kindOf(value)) {
            ReminderSounds.Kind.DEFAULT -> "default"
            ReminderSounds.Kind.SILENT -> "silent"
            ReminderSounds.Kind.CUSTOM -> "custom_" + Integer.toHexString(value.hashCode())
        }
        return "${CHANNEL_ID_PREFIX}_$suffix"
    }

    @SuppressLint("WrongConstant")
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val currentId = channelId(context)

            if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }

            val soundValue = SettingsManager(context).currentReminderSound()
            val soundUri: Uri? = when (ReminderSounds.kindOf(soundValue)) {
                ReminderSounds.Kind.DEFAULT -> Settings.System.DEFAULT_ALARM_ALERT_URI
                ReminderSounds.Kind.SILENT -> null
                ReminderSounds.Kind.CUSTOM -> runCatching { Uri.parse(soundValue) }.getOrNull()
            }

            val channel = NotificationChannel(
                currentId,
                context.getString(R.string.notif_channel_tasks),
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = context.getString(R.string.notif_channel_tasks_desc)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableLights(true)
                lightColor = 0xFF4F378B.toInt()
            }
            manager.createNotificationChannel(channel)

            // Убираем каналы от прежних выбранных мелодий, чтобы в настройках
            // Android не копился список одинаковых «Напоминаний».
            manager.notificationChannels
                .filter { it.id != currentId && it.id.startsWith(CHANNEL_ID_PREFIX) }
                .forEach { manager.deleteNotificationChannel(it.id) }
        }
    }

    fun build(context: Context, taskId: Int, title: String): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            taskId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)

        @Suppress("DEPRECATION")
        builder.setVibrate(VIBRATION_PATTERN)
        @Suppress("DEPRECATION")
        builder.setLights(0xFF4F378B.toInt(), 500, 500)

        val fullScreen = PendingIntent.getActivity(
            context,
            taskId,
            ReminderFullScreenActivity.newIntent(context, taskId, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setFullScreenIntent(fullScreen, true)

        val dismiss = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent(context, NotificationDismissReceiver::class.java).putExtra(EXTRA_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notif_dismiss),
                dismiss
            ).build()
        )
        builder.setDeleteIntent(dismiss)

        return builder.build()
    }
}