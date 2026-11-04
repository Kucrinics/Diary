package ru.diary.app.data.settings

/**
 * Значение настройки звука напоминания.
 *
 * Хранится строка: пустая — системный звук будильника по умолчанию,
 * [SILENT] — без звука, иначе URI выбранной мелодии.
 */
object ReminderSounds {

    const val DEFAULT = ""
    const val SILENT = "silent"

    enum class Kind { DEFAULT, SILENT, CUSTOM }

    fun kindOf(value: String?): Kind = when {
        value.isNullOrBlank() -> Kind.DEFAULT
        value == SILENT -> Kind.SILENT
        else -> Kind.CUSTOM
    }

    /** URI из системного выбора мелодии; null означает «без звука». */
    fun storedValue(pickedUri: String?): String =
        pickedUri?.takeIf { it.isNotBlank() } ?: SILENT
}
