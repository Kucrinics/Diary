package ru.diary.app.data.model

/**
 * Тип повторения задачи. Значение строкового поля равно имени константы.
 */
enum class RepeatType(val value: String) {
    NONE("NONE"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY"),
    INTERVAL("INTERVAL"),
    DAYS_OF_WEEK("DAYS_OF_WEEK");

    companion object {
        fun fromValue(value: String): RepeatType =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}