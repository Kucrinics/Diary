package ru.diary.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSoundsTest {

    @Test
    fun `blank value means default alarm`() {
        assertEquals(ReminderSounds.Kind.DEFAULT, ReminderSounds.kindOf(""))
        assertEquals(ReminderSounds.Kind.DEFAULT, ReminderSounds.kindOf(null))
        assertEquals(ReminderSounds.Kind.DEFAULT, ReminderSounds.kindOf("   "))
    }

    @Test
    fun `silent marker means no sound`() {
        assertEquals(ReminderSounds.Kind.SILENT, ReminderSounds.kindOf(ReminderSounds.SILENT))
    }

    @Test
    fun `any other value is a custom melody`() {
        assertEquals(
            ReminderSounds.Kind.CUSTOM,
            ReminderSounds.kindOf("content://media/internal/audio/media/42")
        )
    }

    @Test
    fun `picked uri is stored as is`() {
        val uri = "content://media/internal/audio/media/42"

        assertEquals(uri, ReminderSounds.storedValue(uri))
    }

    @Test
    fun `empty selection becomes silent`() {
        assertEquals(ReminderSounds.SILENT, ReminderSounds.storedValue(null))
        assertEquals(ReminderSounds.SILENT, ReminderSounds.storedValue(""))
        assertEquals(ReminderSounds.SILENT, ReminderSounds.storedValue("   "))
    }
}
