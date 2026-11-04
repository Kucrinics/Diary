package ru.diary.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {

    @Test
    fun `accent keys are unique`() {
        val keys = ThemePresets.accentColors.map { it.key }

        assertTrue("ключи палитры не должны повторяться", keys.size == keys.toSet().size)
    }

    @Test
    fun `findAccent returns the matching preset`() {
        val preset = ThemePresets.findAccent("indigo")

        assertEquals("indigo", preset.key)
    }

    @Test
    fun `findAccent falls back to the first preset for an unknown key`() {
        assertSame(ThemePresets.accentColors.first(), ThemePresets.findAccent("does-not-exist"))
    }
}
