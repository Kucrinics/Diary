package ru.diary.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.diary.app.data.settings.ReminderSounds
import ru.diary.app.data.settings.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        SettingsManager(context).setReminderSound(ReminderSounds.DEFAULT)
        manager.notificationChannels.forEach { manager.deleteNotificationChannel(it.id) }
    }

    @Test
    fun `default setting keeps the stable default channel id`() {
        NotificationHelper.createChannel(context)

        assertEquals(
            "id канала по умолчанию не должен меняться между запусками",
            NotificationHelper.CHANNEL_ID,
            NotificationHelper.channelId(context)
        )
    }

    @Test
    fun `silent setting creates a channel without sound`() {
        SettingsManager(context).setReminderSound(ReminderSounds.SILENT)

        NotificationHelper.createChannel(context)

        val channel = manager.getNotificationChannel(NotificationHelper.channelId(context))
        assertNotNull("канал для режима тишины должен существовать", channel)
        assertNull("в режиме тишины у канала не должно быть звука", channel!!.sound)
        assertTrue("вибрация остаётся включённой", channel.shouldVibrate())
    }

    @Test
    fun `custom melody creates a separate channel with that sound`() {
        val uri = "content://media/internal/audio/media/42"
        SettingsManager(context).setReminderSound(uri)

        NotificationHelper.createChannel(context)

        val id = NotificationHelper.channelId(context)
        val channel = manager.getNotificationChannel(id)
        assertNotNull("канал для выбранной мелодии должен существовать", channel)
        assertEquals(Uri.parse(uri), channel!!.sound)
    }

    @Test
    fun `changing sound removes channels of previous melodies`() {
        NotificationHelper.createChannel(context)
        val defaultId = NotificationHelper.channelId(context)
        assertNotNull(manager.getNotificationChannel(defaultId))

        SettingsManager(context).setReminderSound(ReminderSounds.SILENT)
        NotificationHelper.createChannel(context)

        assertNull(
            "прежний канал нужно удалить, чтобы в настройках не копились дубли",
            manager.getNotificationChannel(defaultId)
        )
        assertNotNull(manager.getNotificationChannel(NotificationHelper.channelId(context)))
    }

    @Test
    fun `player reports silent mode from settings`() {
        SettingsManager(context).setReminderSound(ReminderSounds.SILENT)
        assertTrue(ReminderSoundPlayer.isSilent(context))

        SettingsManager(context).setReminderSound(ReminderSounds.DEFAULT)
        assertFalse(ReminderSoundPlayer.isSilent(context))
    }

    @Test
    fun `createChannel registers alarm channel with max importance and sound`() {
        NotificationHelper.createChannel(context)

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull("канал напоминаний должен существовать после createChannel", channel)
        assertEquals(NotificationManager.IMPORTANCE_MAX, channel!!.importance)
        assertNotNull("у канала должен быть задан звук", channel.sound)
        assertTrue("должна быть включена вибрация", channel.shouldVibrate())
    }

    @Test
    fun `createChannel is idempotent`() {
        NotificationHelper.createChannel(context)
        NotificationHelper.createChannel(context)

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_MAX, channel!!.importance)
    }

    @Test
    fun `createChannel removes legacy channel`() {
        val legacy = NotificationChannel(
            "tasks_reminders",
            "legacy",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(legacy)
        assertNotNull(manager.getNotificationChannel("tasks_reminders"))

        NotificationHelper.createChannel(context)

        assertNull("старый канал tasks_reminders должен быть удалён",
            manager.getNotificationChannel("tasks_reminders"))
        assertNotNull(manager.getNotificationChannel(NotificationHelper.CHANNEL_ID))
    }

    @Test
    fun `build produces notification in alarm channel with title and actions`() {
        NotificationHelper.createChannel(context)

        val notification = NotificationHelper.build(context, taskId = 42, title = "Позвонить маме")

        assertEquals("уведомление должно идти в канал напоминаний",
            NotificationHelper.CHANNEL_ID, notification.channelId)
        assertEquals("Позвонить маме",
            notification.extras.getString(Notification.EXTRA_TITLE))
        assertNotNull("должен быть content intent (открыть приложение)",
            notification.contentIntent)
        assertNotNull("должен быть delete intent (скрыть)",
            notification.deleteIntent)
        assertTrue("должна быть action-кнопка (Отклонить)",
            notification.actions != null && notification.actions.isNotEmpty())
    }

    @Test
    fun `build sets full screen intent so reminder appears on locked screen`() {
        NotificationHelper.createChannel(context)

        val notification = NotificationHelper.build(context, taskId = 7, title = "Встреча")

        assertNotNull("full screen intent нужен для показа окна напоминания",
            notification.fullScreenIntent)
    }

    @Test
    fun `notification can be posted only after channel is created`() {
        // Эмулируем прежний баг: без createChannel уведомление в несуществующий канал
        // на Android 8+ отбрасывается. После создания канала оно публикуется.
        manager.cancelAll()

        NotificationHelper.createChannel(context)
        val notification = NotificationHelper.build(context, taskId = 1, title = "Задача")

        manager.notify(1, notification)

        val posted = manager.activeNotifications
        assertTrue("после создания канала уведомление должно появиться в шторке",
            posted.any { it.id == 1 })
    }

    @Test
    fun `auto cancel delay equals reminder sound limit`() {
        // Уведомление при включённом экране живёт ровно столько, сколько звучит
        // мелодия — 3 минуты, чтобы пропасть вместе с концом звука.
        assertEquals(ReminderSoundPlayer.MAX_PLAY_MS, NotificationHelper.AUTO_CANCEL_DELAY_MS)
        assertEquals(3 * 60 * 1000L, ReminderSoundPlayer.MAX_PLAY_MS)
    }

    @Test
    fun `reminder sound fade is shorter than the play limit`() {
        assertTrue("затухание должно укладываться в лимит звучания",
            ReminderSoundPlayer.FADE_MS < ReminderSoundPlayer.MAX_PLAY_MS)
    }
}
