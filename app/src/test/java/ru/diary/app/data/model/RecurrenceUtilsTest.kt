package ru.diary.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.diary.app.data.db.DateUtils
import java.time.LocalDate

class RecurrenceUtilsTest {

    private fun weeklyTask(date: String, mask: Int?) = Task(
        title = "task",
        date = date,
        repeatType = RepeatType.WEEKLY.value,
        repeatDaysOfWeek = mask
    )

    @Test
    fun weeklyWithoutMask_keepsOriginWeekday() {
        val task = weeklyTask("2026-10-12", null)
        assertEquals("2026-10-19", RecurrenceUtils.nextOccurrenceDate(task, "2026-10-12"))
    }

    @Test
    fun weeklyWithMask_jumpsToNextSelectedDay() {
        val mask = RecurrenceUtils.dayOfWeekMask(listOf(1, 3))
        val task = weeklyTask("2026-10-12", mask)
        assertEquals("2026-10-14", RecurrenceUtils.nextOccurrenceDate(task, "2026-10-12"))
    }

    @Test
    fun `monthly on 31st clamps to last day and returns to anchor`() {
        val task = Task(
            title = "monthly",
            date = "2026-01-31",
            repeatType = RepeatType.MONTHLY.value
        )

        assertEquals("2026-02-28", RecurrenceUtils.nextOccurrenceDate(task, "2026-01-31"))
        assertEquals("2026-03-31", RecurrenceUtils.nextOccurrenceDate(task, "2026-02-28"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-02-28"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-03-28"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-03-31"))
    }

    @Test
    fun `yearly on 29 February clamps in non-leap years`() {
        val task = Task(
            title = "yearly",
            date = "2024-02-29",
            repeatType = RepeatType.YEARLY.value
        )

        assertEquals("2025-02-28", RecurrenceUtils.nextOccurrenceDate(task, "2024-02-29"))
        assertEquals("2026-02-28", RecurrenceUtils.nextOccurrenceDate(task, "2025-02-28"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-02-28"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-02-27"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2028-02-29"))
    }

    @Test
    fun `isActiveOnDate daily respects origin and end date`() {
        val task = Task(
            title = "daily",
            date = "2026-10-12",
            repeatType = RepeatType.DAILY.value,
            repeatEndDate = "2026-10-14"
        )
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-11"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-10-12"))
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-10-14"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-15"))
    }

    @Test
    fun `isActiveOnDate weekly respects selected weekdays`() {
        val mask = RecurrenceUtils.dayOfWeekMask(listOf(1, 3))
        val task = weeklyTask("2026-10-12", mask)
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-10-14"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-15"))
    }

    @Test
    fun `isActiveOnDate interval matches every Nth day`() {
        val task = Task(
            title = "interval",
            date = "2026-10-12",
            repeatType = RepeatType.INTERVAL.value,
            repeatIntervalDays = 3
        )
        assertTrue(RecurrenceUtils.isActiveOnDate(task, "2026-10-15"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-16"))
    }

    @Test
    fun `isActiveOnDate none is never active`() {
        val task = Task(title = "none", date = "2026-10-12")
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-12"))
        assertFalse(RecurrenceUtils.isActiveOnDate(task, "2026-10-13"))
    }

    @Test
    fun weeklyWithMask_matchesOnlySelectedDays() {
        val mask = RecurrenceUtils.dayOfWeekMask(listOf(1, 3))
        val task = weeklyTask("2026-10-12", mask)
        assertTrue(RecurrenceUtils.matchesDaysOfWeek(task, LocalDate.of(2026, 10, 12)))
        assertTrue(RecurrenceUtils.matchesDaysOfWeek(task, LocalDate.of(2026, 10, 14)))
        assertFalse(RecurrenceUtils.matchesDaysOfWeek(task, LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun nextUpcomingOccurrence_weeklyMaskSameDayLaterTime() {
        val mask = RecurrenceUtils.dayOfWeekMask(listOf(1))
        val task = weeklyTask("2026-10-12", mask).copy(time = "10:00")
        val now = DateUtils.toEpochMillis("2026-10-12", "00:00")!!
        assertEquals("2026-10-12", RecurrenceUtils.nextUpcomingOccurrence(task, now))
    }

    @Test
    fun nextUpcomingOccurrence_weeklyMaskSkipsToSelectedDay() {
        val mask = RecurrenceUtils.dayOfWeekMask(listOf(3))
        val task = weeklyTask("2026-10-12", mask).copy(time = "10:00")
        val now = DateUtils.toEpochMillis("2026-10-12", "00:00")!!
        assertEquals("2026-10-14", RecurrenceUtils.nextUpcomingOccurrence(task, now))
    }
}
