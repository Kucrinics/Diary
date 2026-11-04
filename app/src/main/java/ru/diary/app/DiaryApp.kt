package ru.diary.app

import android.app.Application
import android.os.Build
import com.my.tracker.MyTracker
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.diary.app.backup.BackupManager
import ru.diary.app.di.AppContainer
import ru.diary.app.notifications.NotificationHelper

/**
 * Точка входа приложения.
 *
 * Здесь инициализируется аналитика VK (MyTracker + AppMetrica), создаётся
 * контейнер зависимостей и при необходимости восстанавливаются данные
 * из скачанного ранее бэкапа.
 */
class DiaryApp : Application() {

    /** Ручной DI: все зависимости приложения (Room, репозитории, планировщик). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Аналитика НЕ инициализируется здесь: обработка данных (MyTracker,
        // AppMetrica) начинается только после того, как пользователь принял
        // политику конфиденциальности. Вызывается из UI через initAnalyticsIfNeeded().

        // Сначала база данных приложения получает бэкап, потом открывается контейнер.
        val backupManager = BackupManager(this)
        backupManager.restoreIfNeeded()

        container = AppContainer(this, backupManager)

        // Разовая автоочистка корзины: удаляем задачи/заметки, пролежавшие в удалённых
        // дольше срока хранения. Выполняем в фоне, чтобы не блокировать старт процесса.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                container.taskRepository.purgeOldTrash()
                container.noteRepository.purgeOldTrash()
            }
        }

        // Канал уведомлений создаём сразу при старте процесса, иначе на Android 8+
        // уведомление о задаче молча отбрасывается (нет канала -> нет показа и звука).
        NotificationHelper.createChannel(this)
    }

    @Volatile
    private var analyticsInitialized = false

    /**
     * Инициализирует аналитику один раз, после получения согласия пользователя
     * на обработку данных. Повторные вызовы игнорируются.
     */
    fun initAnalyticsIfNeeded() {
        if (analyticsInitialized) return
        analyticsInitialized = true
        initAnalytics()
    }

    private fun initAnalytics() {
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return

        // MyTracker (VK) — аналитика и отслеживание установок.
        runCatching {
            MyTracker.initTracker(BuildConfig.MYTRACKER_SDK_KEY, this)
            if (BuildConfig.DEBUG) {
                MyTracker.setDebugMode(true)
            }
        }

        // AppMetrica (VK) — аналитика.
        runCatching {
            AppMetrica.activate(
                this,
                AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY).build()
            )
        }
    }

    companion object {
        /** Ссылка на экземпляр приложения (нужна ресиверам и виджету). */
        lateinit var instance: DiaryApp
            private set
    }
}