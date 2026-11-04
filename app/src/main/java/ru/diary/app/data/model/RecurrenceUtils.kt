package ru.diary.app.data.model

import ru.diary.app.data.db.DateUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Расчёт вхождений повторяющихся задач.
 *
 * Изначальная дата повтора — `task.date` (шаблон). Следующее вхождение считается
 * от произвольной даты (`nextOccurrenceDate`). Дни недели для DAYS_OF_WEEK
 * хранятся битовой маской (1=пн … 7=вс).
 */
object RecurrenceUtils {

    /** Верхняя граница итераций при поиске ближайшего будущего вхождения. */
    private const val MAX_OCCURRENCE_STEPS = 100000

    /**
     * Битовую маску из набора дней недели (1..7), где каждый день — бит `1 shl (day-1)`.
     */
    fun dayOfWeekMask(days: Collection<Int>): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day - 1)) }

    /** Совпадает ли дата с переданной маской дней недели. */
    fun matchesMask(date: LocalDate, mask: Int): Boolean =
        mask != 0 && (mask and (1 shl (date.dayOfWeek.value - 1))) != 0

    /** Совпадает ли дата с днями недели, заданными в задаче. */
    fun matchesDaysOfWeek(task: Task, date: LocalDate): Boolean =
        matchesMask(date, task.repeatDaysOfWeek ?: 0)

    /**
     * Активен ли повтор задачи на указанную дату (с учётом даты окончания).
     * Используется и экраном задач, и виджетом, чтобы вхождения считались одинаково.
     */
    fun isActiveOnDate(task: Task, dateStr: String): Boolean {
        val type = RepeatType.fromValue(task.repeatType)
        if (type == RepeatType.NONE) return false
        if (task.repeatEndDate != null && dateStr > task.repeatEndDate) return false
        val date = DateUtils.parseDate(dateStr)
        val origin = DateUtils.parseDate(task.date)
        if (origin > date) return false
        return when (type) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> {
                val mask = task.repeatDaysOfWeek ?: 0
                if (mask != 0) matchesDaysOfWeek(task, date)
                else ChronoUnit.DAYS.between(origin, date) % 7 == 0L
            }
            // Для 29/30/31-го числа в коротком месяце вхождение приходится
            // на последний день, но в следующем месяце возвращается к якорю.
            RepeatType.MONTHLY -> {
                val expectedDay = origin.dayOfMonth.coerceAtMost(date.lengthOfMonth())
                date.dayOfMonth == expectedDay
            }
            RepeatType.YEARLY -> {
                val month = YearMonth.of(date.year, origin.monthValue)
                date.monthValue == origin.monthValue &&
                    date.dayOfMonth == origin.dayOfMonth.coerceAtMost(month.lengthOfMonth())
            }
            RepeatType.INTERVAL -> {
                val interval = task.repeatIntervalDays?.takeIf { it > 0 } ?: return false
                ChronoUnit.DAYS.between(origin, date) % interval == 0L
            }
            RepeatType.DAYS_OF_WEEK -> matchesDaysOfWeek(task, date)
            RepeatType.NONE -> false
        }
    }

    /**
     * Дата следующего вхождения относительно fromDate (не включая саму fromDate).
     * Учитывает дату окончания повторения; для невалидных параметров — null.
     */
    fun nextOccurrenceDate(task: Task, fromDate: String): String? {
        val type = RepeatType.fromValue(task.repeatType)
        if (type == RepeatType.NONE) return null
        val base = DateUtils.parseDate(fromDate)
        val interval = if ((task.repeatIntervalDays ?: 0) > 0) task.repeatIntervalDays else 1
        val next: LocalDate = when (type) {
            RepeatType.DAILY -> base.plusDays(1)
            RepeatType.WEEKLY -> {
                val mask = task.repeatDaysOfWeek ?: 0
                if (mask != 0) nextDayOfWeekAfter(base, mask) ?: return null
                else base.plusWeeks(1)
            }
            RepeatType.MONTHLY -> nextMonthlyOccurrence(task.date, base)
            RepeatType.YEARLY -> nextYearlyOccurrence(task.date, base)
            RepeatType.INTERVAL -> base.plusDays(interval!!.toLong())
            RepeatType.DAYS_OF_WEEK ->
                nextDayOfWeekAfter(base, task.repeatDaysOfWeek ?: 0) ?: return null
            RepeatType.NONE -> return null
        }
        val nextIso = DateUtils.toIsoDate(next)
        return if (task.repeatEndDate != null && nextIso.compareTo(task.repeatEndDate) > 0) null
        else nextIso
    }

    /**
     * Ближайшее месячное вхождение строго после [after]. Считается от якоря
     * серии ([anchorIso]), поэтому 31-е не «съезжает» после февраля.
     */
    private fun nextMonthlyOccurrence(anchorIso: String, after: LocalDate): LocalDate {
        val anchor = DateUtils.parseDate(anchorIso)
        if (anchor > after) return anchor
        var month = YearMonth.from(after)
        var candidate = occurrenceInMonth(anchor, month)
        if (candidate <= after) {
            month = month.plusMonths(1)
            candidate = occurrenceInMonth(anchor, month)
        }
        return candidate
    }

    /** Ближайшее годовое вхождение строго после [after] (с учётом 29 февраля). */
    private fun nextYearlyOccurrence(anchorIso: String, after: LocalDate): LocalDate {
        val anchor = DateUtils.parseDate(anchorIso)
        if (anchor > after) return anchor
        var candidate = occurrenceInYear(anchor, after.year)
        if (candidate <= after) candidate = occurrenceInYear(anchor, after.year + 1)
        return candidate
    }

    private fun occurrenceInMonth(anchor: LocalDate, month: YearMonth): LocalDate =
        month.atDay(anchor.dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    private fun occurrenceInYear(anchor: LocalDate, year: Int): LocalDate =
        occurrenceInMonth(anchor, YearMonth.of(year, anchor.monthValue))

    /** Ближайший день из маски после переданной даты (строго позже). */
    private fun nextDayOfWeekAfter(date: LocalDate, mask: Int): LocalDate? {
        if (mask == 0) return null
        var d = date.plusDays(1)
        repeat(7) {
            if (matchesMask(d, mask)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /**
     * Ближайшее будущее вхождение (date+time строго позже nowMillis),
     * начиная с task.date и до repeatEndDate/MAX_OCCURRENCE_STEPS.
     */
    fun nextUpcomingOccurrence(task: Task, nowMillis: Long): String? {
        val type = RepeatType.fromValue(task.repeatType)
        if (type == RepeatType.NONE) return null
        val time = task.time ?: DateUtils.DEFAULT_REMINDER_TIME
        var date = task.date
        var steps = 0
        while (steps < MAX_OCCURRENCE_STEPS) {
            if (task.repeatEndDate != null && date.compareTo(task.repeatEndDate) > 0) return null
            val millis = DateUtils.toEpochMillis(date, time) ?: return null
            if (millis > nowMillis && isOccurrenceForType(task, type, date)) return date
            date = nextOccurrenceDate(task, date) ?: return null
            steps++
        }
        return null
    }

    /** Для DAYS_OF_WEEK и WEEKLY с выбранными днями вхождение валидно только на них. */
    private fun isOccurrenceForType(task: Task, type: RepeatType, date: String): Boolean =
        if ((type == RepeatType.DAYS_OF_WEEK || type == RepeatType.WEEKLY) &&
            (task.repeatDaysOfWeek ?: 0) != 0
        ) {
            matchesDaysOfWeek(task, DateUtils.parseDate(date))
        } else {
            true
        }
}