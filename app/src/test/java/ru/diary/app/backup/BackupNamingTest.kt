package ru.diary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class BackupNamingTest {

    @Test
    fun `file name embeds date and time to the minute`() {
        val date = GregorianCalendar(2026, Calendar.OCTOBER, 18, 7, 5, 0).time

        assertEquals(
            "DiaryBackup_2026-10-18_07-05.diarybak",
            BackupNaming.backupFileName(date, Locale.ROOT)
        )
    }

    @Test
    fun `file name keeps prefix and suffix contract used by rotation and restore`() {
        val name = BackupNaming.backupFileName(Date(0), Locale.ROOT)

        assertTrue(name.startsWith(BackupConstants.PREFIX))
        assertTrue(name.endsWith(BackupConstants.SUFFIX))
    }
}
