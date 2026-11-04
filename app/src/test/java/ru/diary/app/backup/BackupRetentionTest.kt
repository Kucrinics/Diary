package ru.diary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {

    @Test
    fun `keeps newest files and deletes the excess oldest`() {
        val items = listOf("a" to 1L, "b" to 2L, "c" to 3L, "d" to 4L)

        assertEquals(listOf("a", "b"), BackupRetention.oldestToDelete(items, 2))
    }

    @Test
    fun `deletes nothing when count is below the limit`() {
        assertTrue(BackupRetention.oldestToDelete(listOf("a" to 1L, "b" to 2L), 6).isEmpty())
    }

    @Test
    fun `deletes nothing at the exact limit`() {
        assertTrue(BackupRetention.oldestToDelete(listOf("a" to 1L), 1).isEmpty())
    }

    @Test
    fun `handles unsorted input`() {
        val items = listOf("new" to 100L, "old" to 1L, "mid" to 50L)

        assertEquals(listOf("old"), BackupRetention.oldestToDelete(items, 2))
    }

    @Test
    fun `non positive limit deletes everything`() {
        val items = listOf("a" to 1L, "b" to 2L)

        assertEquals(setOf("a", "b"), BackupRetention.oldestToDelete(items, 0).toSet())
    }
}
