package ru.diary.app.data.db

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Утилиты для работы с датой/временем.
 * В БД дата хранится как ISO "yyyy-MM-dd", время как "HH:mm".
 */
object DateUtils {

    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE      // yyyy-MM-dd
    private val isoTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val isoDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Время напоминания по умолчанию для задач без указанного времени. */
    const val DEFAULT_REMINDER_TIME = "06:00"

    /** Срок хранения удалённых записей (задач/заметок) в корзине, в днях. */
    const val TRASH_RETENTION_DAYS = 30L

    /** Миллисекунд в сутках (для UTC-конвертации дат Material3 DatePicker). */
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun today(): String = LocalDate.now().format(isoDate)

    fun now(): String = LocalDateTime.now().format(isoDateTime)

    fun formatDateTime(dateTime: String): String {
        val local = runCatching {
            LocalDateTime.parse(dateTime, isoDateTime)
        }.getOrElse {
            runCatching {
                LocalDate.parse(dateTime, isoDate).atStartOfDay()
            }.getOrElse { return dateTime }
        }
        return local.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }

    fun formatDate(date: String): String {
        val local = LocalDate.parse(date, isoDate)
        return local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))
    }

    fun formatTime(time: String): String {
        val local = LocalTime.parse(time, isoTime)
        return local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }

    /**
     * Локализованное представление «дата + время» по epochMillis.
     * Для 0 и отрицательных значений возвращает пустую строку.
     */
    fun parseDate(date: String): LocalDate = LocalDate.parse(date, isoDate)

    fun toIsoDate(date: LocalDate): String = date.format(isoDate)

    /** Понедельник недели, содержащей date. */
    fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** Воскресенье недели, содержащей date. */
    fun endOfWeek(date: LocalDate): LocalDate =
        date.plusDays((7 - date.dayOfWeek.value).toLong())

    /** Первый день месяца. */
    fun startOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    /** Последний день месяца. */
    fun endOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(date.lengthOfMonth())

    fun toIsoTime(time: LocalTime): String = time.format(isoTime)

    /**
     * Дата в миллисекундах UTC-полуночи — именно такой формат ожидает
     * Material3 `DatePicker` (`selectedDateMillis`).
     */
    fun toUtcMillis(date: LocalDate): Long = date.toEpochDay() * MILLIS_PER_DAY

    /** Обратное преобразование миллисекунд из Material3 `DatePicker` в дату. */
    fun dateFromUtcMillis(millis: Long): LocalDate =
        LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))

    /** Короткое представление дня: "7 июля". */
    fun monthDay(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))

    /** Название месяца и год: "июль 2026". */
    fun monthYear(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

    /** Название дня недели: "четверг". */
    fun dayOfWeek(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))

    /** Порог, раньше которого записи корзины считаются устаревшими (для автоочистки). */
    fun trashRetentionThreshold(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis - TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L

    /**
     * Преобразует дату+время задачи в epochMillis для AlarmManager.
     * Возвращает null, если время не задано.
     */
    fun toEpochMillis(dateIso: String, timeIso: String?): Long? {
        if (timeIso == null) return null
        val date = LocalDate.parse(dateIso, isoDate)
        val time = LocalTime.parse(timeIso, isoTime)
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
