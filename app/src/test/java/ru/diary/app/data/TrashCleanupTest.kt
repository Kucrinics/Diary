package ru.diary.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.diary.app.data.db.AppDatabase
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.Note
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.data.repository.TaskRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TrashCleanupTest {

    private val day = 24L * 60 * 60 * 1000

    private lateinit var db: AppDatabase
    private lateinit var taskRepository: TaskRepository
    private lateinit var noteRepository: NoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskRepository = TaskRepository(db.taskDao())
        noteRepository = NoteRepository(db.noteDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `retention threshold is exactly 30 days before now`() {
        val now = 1_000_000_000_000L
        assertEquals(now - 30 * day, DateUtils.trashRetentionThreshold(now))
    }

    @Test
    fun `purge removes only soft-deleted tasks older than retention`() = runBlocking {
        val now = System.currentTimeMillis()

        val oldTrash = taskRepository.add(deletedTask("old", now - 31 * day))
        val freshTrash = taskRepository.add(deletedTask("fresh", now - 29 * day))
        val active = taskRepository.add(Task(title = "active", date = "2026-10-15"))

        taskRepository.purgeOldTrash()

        assertNull("задача старше 30 дней должна исчезнуть", taskRepository.getById(oldTrash))
        assertNotNull("задача младше 30 дней должна остаться", taskRepository.getById(freshTrash))
        assertNotNull("активную задачу удалять нельзя", taskRepository.getById(active))
    }

    @Test
    fun `purge removes only soft-deleted notes older than retention`() = runBlocking {
        val now = System.currentTimeMillis()

        val oldTrash = noteRepository.add(deletedNote("old", now - 40 * day))
        val freshTrash = noteRepository.add(deletedNote("fresh", now - 1 * day))
        val active = noteRepository.add(Note(title = "active", date = "2026-10-15"))

        noteRepository.purgeOldTrash()

        assertNull("заметка старше 30 дней должна исчезнуть", noteRepository.getById(oldTrash))
        assertNotNull("заметка младше 30 дней должна остаться", noteRepository.getById(freshTrash))
        assertNotNull("активную заметку удалять нельзя", noteRepository.getById(active))
    }

    @Test
    fun `soft-deleted item without deleted_at is kept`() = runBlocking {
        val noTimestamp = taskRepository.add(
            Task(title = "no-ts", date = "2026-01-01", isDeleted = true, deletedAt = null)
        )

        taskRepository.purgeOldTrash()

        assertNotNull(
            "запись с is_deleted=1 без deleted_at не должна удаляться",
            taskRepository.getById(noTimestamp)
        )
    }

    private fun deletedTask(title: String, deletedAt: Long) =
        Task(
            title = title,
            date = "2026-01-01",
            isDeleted = true,
            deletedAt = deletedAt
        )

    private fun deletedNote(title: String, deletedAt: Long) =
        Note(
            title = title,
            date = "2026-01-01",
            isDeleted = true,
            deletedAt = deletedAt
        )
}
