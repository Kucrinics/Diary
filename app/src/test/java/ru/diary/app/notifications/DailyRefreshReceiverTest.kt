package ru.diary.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DailyRefreshReceiverTest {

    /** Зона без перехода на летнее/зимнее время — расчёт детерминирован. */
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `next trigger is tomorrow midnight plus buffer`() {
        val now = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        // Полночь 17 ноября в Москве = 2026-11-16T21:00:00Z.
        val expected = Instant.parse("2026-11-16T21:00:00Z").toEpochMilli() +
            DailyRefreshReceiver.AFTER_MIDNIGHT_MS
        assertEquals(expected, DailyRefreshReceiver.nextTriggerMillis(now, zone))
    }

    @Test
    fun `next trigger is always strictly in the future`() {
        // Прямо перед полуночью: триггер всё равно назначается на следующую полночь.
        val now = Instant.parse("2026-11-16T20:59:59Z").toEpochMilli()
        val trigger = DailyRefreshReceiver.nextTriggerMillis(now, zone)
        assertTrue(trigger > now)
    }

    @Test
    fun `trigger lands on the first seconds of the new day`() {
        val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val trigger = DailyRefreshReceiver.nextTriggerMillis(now, zone)
        val local = Instant.ofEpochMilli(trigger).atZone(zone)
        assertEquals(17, local.dayOfMonth)
        assertEquals(0, local.hour)
        assertTrue(local.minute * 60 + local.second <= DailyRefreshReceiver.AFTER_MIDNIGHT_MS / 1000)
    }
}
