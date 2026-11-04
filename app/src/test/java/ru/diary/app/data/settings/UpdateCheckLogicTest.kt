package ru.diary.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка правила частоты проверки обновлений: не чаще раза в сутки.
 */
class UpdateCheckLogicTest {

    @Test
    fun `never checked - due`() {
        assertTrue(SettingsManager.shouldCheckUpdate(lastCheckAt = 0L, nowAt = 86_400_000L))
    }

    @Test
    fun `checked less than a day ago - not due`() {
        assertFalse(
            SettingsManager.shouldCheckUpdate(
                lastCheckAt = 1_000L,
                nowAt = 1_000L + 86_399_999L
            )
        )
    }

    @Test
    fun `checked a day ago - due`() {
        assertTrue(
            SettingsManager.shouldCheckUpdate(
                lastCheckAt = 1_000L,
                nowAt = 1_000L + 86_400_000L
            )
        )
    }

    @Test
    fun `checked long ago - due`() {
        assertTrue(
            SettingsManager.shouldCheckUpdate(
                lastCheckAt = 1_000L,
                nowAt = 1_000L + 7L * 86_400_000L
            )
        )
    }
}