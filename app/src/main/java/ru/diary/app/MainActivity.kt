package ru.diary.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import ru.diary.app.ui.DiaryApp as DiaryAppRoot

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("diary_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "") ?: ""
        if (lang.isNotEmpty()) {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            // Язык «как в системе»: сбрасываем JVM-локаль на системную, иначе код,
            // завязанный на Locale.getDefault() (виджет, голосовой ввод), продолжит
            // использовать язык, выбранный пользователем ранее.
            Locale.setDefault(newBase.resources.configuration.locales[0])
            super.attachBaseContext(newBase)
        }
    }

    // Лаунчер запроса разрешения на микрофон (для голосового ввода).
    val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermissionCallback?.invoke(granted) }

    var audioPermissionCallback: ((Boolean) -> Unit)? = null

    fun requestAudioPermissionIfNeeded(onResult: (Boolean) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult(true)
        } else {
            audioPermissionCallback = onResult
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: контент рисуется под системными панелями,
        // отступы учитывают Scaffold/TopAppBar/NavigationBar.
        enableEdgeToEdge()

        // Запрос разрешений (микрофон / уведомления) выполняется не здесь,
        // а в UI — уже после принятия политики конфиденциальности.

        val container = (application as DiaryApp).container

        // Автоматическое резервное копирование при сворачивании (не чаще 1 раза в 12ч).
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                CoroutineScope(Dispatchers.IO).launch {
                    container.backupManager.backup(container.database)
                }
            }
        })

        setContent {
            val config by container.settingsManager.config.collectAsState()
            ru.diary.app.ui.theme.DiaryTheme(config = config) {
                DiaryAppRoot(container = container, settingsManager = container.settingsManager)
            }
        }
    }
}
