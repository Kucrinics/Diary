package ru.diary.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка правила разового показа предложения оценить приложение.
 */
class ReviewPromptLogicTest {

    @Test
    fun `not shown and fewer than three days - not due`() {
        assertFalse(SettingsManager.shouldShowReviewPrompt(alreadyShown = false, daysSinceInstall = 0))
        assertFalse(SettingsManager.shouldShowReviewPrompt(alreadyShown = false, daysSinceInstall = 1))
        assertFalse(SettingsManager.shouldShowReviewPrompt(alreadyShown = false, daysSinceInstall = 2))
    }

    @Test
    fun `not shown and three or more days - due`() {
        assertTrue(SettingsManager.shouldShowReviewPrompt(alreadyShown = false, daysSinceInstall = 3))
        assertTrue(SettingsManager.shouldShowReviewPrompt(alreadyShown = false, daysSinceInstall = 30))
    }

    @Test
    fun `already shown - never due again`() {
        assertFalse(SettingsManager.shouldShowReviewPrompt(alreadyShown = true, daysSinceInstall = 3))
        assertFalse(SettingsManager.shouldShowReviewPrompt(alreadyShown = true, daysSinceInstall = 3650))
    }
}
