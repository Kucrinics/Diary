package ru.diary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSchedulerTest {

    @Test
    fun `interval is twelve hours`() {
        assertEquals(12L * 60 * 60 * 1000, BackupScheduler.INTERVAL_MS)
    }

    @Test
    fun `backs up when the interval has elapsed`() {
        assertTrue(BackupScheduler.shouldBackup(0L, BackupScheduler.INTERVAL_MS))
    }

    @Test
    fun `does not back up before the interval elapses`() {
        assertFalse(BackupScheduler.shouldBackup(0L, BackupScheduler.INTERVAL_MS - 1))
    }

    @Test
    fun `first run with zero timestamp backs up once interval has passed`() {
        assertTrue(BackupScheduler.shouldBackup(0L, 13L * 60 * 60 * 1000))
    }
}
