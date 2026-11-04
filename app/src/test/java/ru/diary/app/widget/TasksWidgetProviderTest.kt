package ru.diary.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TasksWidgetProviderTest {

    private fun fit(
        lines: IntArray,
        linePx: Int = 20,
        marginPx: Int = 2,
        overheadPx: Int = 60,
        availPx: Int = 200
    ): Int = TasksWidgetProvider.fitCount(lines, linePx, marginPx, overheadPx, availPx)

    @Test
    fun `fitCount fits all single-line tasks when height is enough`() {
        assertEquals(3, fit(intArrayOf(1, 1, 1)))
        assertEquals(6, fit(intArrayOf(1, 1, 1, 1, 1, 1)))
    }

    @Test
    fun `fitCount takes multi-line tasks into account`() {
        // одна задача на 3 строки занимает столько же, сколько три строки
        assertEquals(1, fit(intArrayOf(3), overheadPx = 60, availPx = 130))
        assertEquals(2, fit(intArrayOf(3, 2), overheadPx = 60, availPx = 180))
        // вторая (длинная) задача уже не помещается целиком и не обрезается
        assertEquals(1, fit(intArrayOf(1, 5), overheadPx = 60, availPx = 130))
    }

    @Test
    fun `fitCount counts only whole tasks and stops after max rows`() {
        // 4 строки + первая задача: вторая не помещается полностью -> показываем 1
        assertEquals(1, fit(intArrayOf(1, 1), overheadPx = 60, availPx = 85))
        // максимум строк ограничен
        assertEquals(TasksWidgetProvider.MAX_ROWS, fit((1..20).map { 1 }.toIntArray(), availPx = 2000))
    }

    @Test
    fun `fitCount is zero when height does not fit overhead`() {
        assertEquals(0, fit(intArrayOf(1), overheadPx = 60, availPx = 59))
        assertEquals(0, fit(intArrayOf(1), overheadPx = 60, availPx = 0))
    }
}