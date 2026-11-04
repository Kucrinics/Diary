package ru.diary.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {

    @Test
    fun `utc millis round-trip for Material3 DatePicker`() {
        val date = LocalDate.of(2026, 10, 26)

        val millis = DateUtils.toUtcMillis(date)

        assertEquals("DatePicker expects UTC midnight", 0L, Math.floorMod(millis, 86_400_000L))
        assertEquals(date, DateUtils.dateFromUtcMillis(millis))
    }

    @Test
    fun `utc millis round-trip for another date`() {
        val date = LocalDate.of(2027, 1, 1)

        assertEquals(date, DateUtils.dateFromUtcMillis(DateUtils.toUtcMillis(date)))
    }
}
