package ru.diary.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOccurrencesTest {

    private val date = "2026-10-26"
    private val yesterday = "2026-10-25"
    private val tomorrow = "2026-10-27"

    private fun recurringTemplate(
        id: Long = 7L,
        title: String = "Daily",
        date: String = yesterday,
        time: String? = null
    ) = Task(
        id = id,
        title = title,
        date = date,
        time = time,
        repeatType = RepeatType.DAILY.value
    )

    @Test
    fun `daily template created yesterday appears today as virtual occurrence`() {
        val template = recurringTemplate()

        val result = TaskOccurrences.expandForDate(emptyList(), listOf(template), date)

        assertEquals(1, result.size)
        val virtual = result.single()
        assertEquals(TaskOccurrences.virtualId(template.id), virtual.id)
        assertEquals(date, virtual.date)
        assertEquals("Daily", virtual.title)
        assertTrue(virtual.id < 0)
    }

    @Test
    fun `materialized copy suppresses virtual occurrence`() {
        val template = recurringTemplate()
        val copy = Task(
            id = 11L,
            title = "Daily",
            date = date,
            repeatOriginId = template.id
        )

        val result = TaskOccurrences.expandForDate(listOf(copy), listOf(template), date)

        assertEquals(1, result.size)
        assertEquals(11L, result.single().id)
        assertFalse(result.single().id < 0)
    }

    @Test
    fun `completed materialized copy stays visible as history`() {
        val template = recurringTemplate()
        val completed = Task(
            id = 11L,
            title = "Daily",
            date = date,
            repeatOriginId = template.id,
            isCompleted = true
        )

        val result = TaskOccurrences.expandForDate(listOf(completed), listOf(template), date)

        assertEquals(1, result.size)
        assertTrue(result.single().isCompleted)
    }

    @Test
    fun `template on its own date is not duplicated`() {
        val template = recurringTemplate(date = date)

        val result = TaskOccurrences.expandForDate(listOf(template), listOf(template), date)

        assertEquals(1, result.size)
        assertEquals(template.id, result.single().id)
    }

    @Test
    fun `virtual occurrence joined with real tasks is sorted by time`() {
        val template = recurringTemplate(time = "09:00")
        val early = Task(id = 1L, title = "Early", date = date, time = "08:00")
        val late = Task(id = 2L, title = "Late", date = date, time = "10:00")

        val result = TaskOccurrences.expandForDate(
            listOf(late, early),
            listOf(template),
            date
        )

        assertEquals(listOf("Early", "Daily", "Late"), result.map { it.title })
    }
}
