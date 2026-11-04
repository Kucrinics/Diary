package ru.diary.app.data.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.diary.app.R

data class ThemeConfig(
    val accentColorKey: String = "purple",
    val isDarkMode: Boolean = false,
    val languageCode: String = ""
)

data class ThemeOption(
    val key: String,
    val labelRes: Int,
    val primaryLight: Long,
    val primaryDark: Long
)

object ThemePresets {
    val accentColors = listOf(
        ThemeOption("purple", R.string.theme_purple, 0xFF6750A4, 0xFFD0BCFF),
        ThemeOption("blue", R.string.theme_blue, 0xFF1976D2, 0xFF90CAF9),
        ThemeOption("green", R.string.theme_green, 0xFF388E3C, 0xFFA5D6A7),
        ThemeOption("teal", R.string.theme_teal, 0xFF00796B, 0xFF80CBC4),
        ThemeOption("orange", R.string.theme_orange, 0xFFF57C00, 0xFFFFCC80),
        ThemeOption("red", R.string.theme_red, 0xFFD32F2F, 0xFFEF9A9A),
        ThemeOption("pink", R.string.theme_pink, 0xFFC2185B, 0xFFF48FB1),
        ThemeOption("lime", R.string.theme_lime, 0xFF689F38, 0xFFC5E1A5),
        ThemeOption("indigo", R.string.theme_indigo, 0xFF303F9F, 0xFF9FA8DA),
        ThemeOption("amber", R.string.theme_amber, 0xFFFFA000, 0xFFFFE082),
        ThemeOption("brown", R.string.theme_brown, 0xFF5D4037, 0xFFBCAAA4),
        ThemeOption("grey", R.string.theme_grey, 0xFF546E7A, 0xFFB0BEC5),
    )

    fun findAccent(key: String): ThemeOption =
        accentColors.find { it.key == key } ?: accentColors.first()
}

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private val _reminderSound = MutableStateFlow(loadReminderSound())
    val reminderSound: StateFlow<String> = _reminderSound.asStateFlow()

    private fun loadConfig(): ThemeConfig = ThemeConfig(
        accentColorKey = prefs.getString("accent_color", "purple") ?: "purple",
        isDarkMode = prefs.getBoolean("dark_mode", false),
        languageCode = prefs.getString("language", "") ?: ""
    )

    fun setAccentColor(key: String) {
        prefs.edit().putString("accent_color", key).apply()
        _config.value = config.value.copy(accentColorKey = key)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        _config.value = config.value.copy(isDarkMode = enabled)
    }

    fun setLanguage(code: String) {
        prefs.edit().putString("language", code).apply()
        _config.value = config.value.copy(languageCode = code)
    }

    /** Выбранный звук напоминания: пусто — по умолчанию, [ReminderSounds.SILENT] — без звука. */
    fun setReminderSound(value: String) {
        prefs.edit().putString(KEY_REMINDER_SOUND, value).apply()
        _reminderSound.value = value
    }

    /**
     * Актуальное значение звука без подписки на Flow — нужно фоновым
     * компонентам (будильник, канал уведомлений).
     */
    fun currentReminderSound(): String = loadReminderSound()

    private fun loadReminderSound(): String =
        prefs.getString(KEY_REMINDER_SOUND, ReminderSounds.DEFAULT) ?: ReminderSounds.DEFAULT

    /**
     * Пора ли показать разовое предложение оценить приложение.
     * Показываем один раз и только спустя [REVIEW_PROMPT_AFTER_DAYS] дней после установки.
     */
    fun isReviewPromptDue(): Boolean = shouldShowReviewPrompt(
        alreadyShown = prefs.getBoolean(KEY_REVIEW_PROMPT_SHOWN, false),
        daysSinceInstall = daysSinceInstall()
    )

    /** Помечает предложение показанным — повторно оно не появится. */
    fun markReviewPromptShown() {
        prefs.edit().putBoolean(KEY_REVIEW_PROMPT_SHOWN, true).apply()
    }

    /**
     * Пора ли проверять наличие обновления в RuStore.
     * Проверяем не чаще раза в сутки, чтобы не дёргать пользователя каждый запуск.
     */
    fun isUpdateCheckDue(): Boolean = shouldCheckUpdate(
        lastCheckAt = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L),
        nowAt = System.currentTimeMillis()
    )

    /** Запоминает момент последней проверки обновлений. */
    fun markUpdateCheckDone() {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    /** Сколько дней прошло с момента установки приложения. */
    private fun daysSinceInstall(): Long = try {
        val pm = appContext.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appContext.packageName, 0)
        }
        (System.currentTimeMillis() - info.firstInstallTime) / MILLIS_PER_DAY
    } catch (_: Exception) {
        0L
    }

    internal companion object {
        const val REVIEW_PROMPT_AFTER_DAYS = 3L
        private const val KEY_REVIEW_PROMPT_SHOWN = "review_prompt_shown"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_REMINDER_SOUND = "reminder_sound"
        private const val MILLIS_PER_DAY = 86_400_000L

        /**
         * Правило показа предложения: его ещё не показывали и с установки прошло
         * не меньше [REVIEW_PROMPT_AFTER_DAYS] дней.
         */
        fun shouldShowReviewPrompt(alreadyShown: Boolean, daysSinceInstall: Long): Boolean =
            !alreadyShown && daysSinceInstall >= REVIEW_PROMPT_AFTER_DAYS

        /** Обновление проверяем не чаще раза в сутки. */
        fun shouldCheckUpdate(lastCheckAt: Long, nowAt: Long): Boolean =
            nowAt - lastCheckAt >= MILLIS_PER_DAY
    }
}
