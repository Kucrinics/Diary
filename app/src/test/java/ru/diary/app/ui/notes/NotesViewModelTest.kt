package ru.diary.app.ui.notes

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.Note
import ru.diary.app.data.repository.NoteRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: NoteRepository
    private lateinit var vm: NotesViewModel

    private val db = mutableMapOf<Long, Note>()
    private var nextId = 1000L
    private val today = DateUtils.today()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = false)

        coEvery { repository.observeAll() } answers {
            MutableStateFlow(
                db.values.filter { !it.isDeleted }
                    .sortedWith(compareBy<Note> { it.sortOrder }.thenBy { it.id })
            )
        }
        coEvery { repository.getById(any()) } answers { db[arg<Long>(0)] }
        coEvery { repository.add(any()) } answers {
            val note = arg<Note>(0)
            val id = nextId++
            db[id] = note.copy(id = id)
            id
        }
        coEvery { repository.update(any()) } answers {
            val note = arg<Note>(0)
            db[note.id] = note
        }
        coEvery { repository.deleteById(any()) } answers { db.remove(arg<Long>(0)) }
        coEvery { repository.restore(any()) } answers {
            val id = arg<Long>(0)
            db[id]?.let { db[id] = it.copy(isDeleted = false, deletedAt = null) }
        }
        coEvery { repository.updateSortOrder(any(), any()) } answers {
            val id = arg<Long>(0)
            val order = arg<Int>(1)
            db[id]?.let { db[id] = it.copy(sortOrder = order) }
        }
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel() {
        vm = NotesViewModel(repository)
    }

    private fun TestScope.collectNotes(): Job = backgroundScope.launch {
        vm.notes.collect { }
    }

    private fun addNoteToDb(
        title: String,
        content: String = "",
        important: Boolean = false,
        sortOrder: Int = 0
    ): Note {
        val note = Note(
            id = nextId++,
            title = title,
            content = content,
            date = today,
            updatedAt = DateUtils.now(),
            isImportant = important,
            sortOrder = sortOrder
        )
        db[note.id] = note
        return note
    }

    private fun titles(list: List<Note>) = list.map { it.title }

    @Test
    fun `add inserts note with trim-safe fields`() = runTest {
        createViewModel()
        vm.add("My note", "body", isImportant = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, db.size)
        val saved = db.values.first()
        assertEquals("My note", saved.title)
        assertEquals("body", saved.content)
        assertTrue(saved.isImportant)
        assertFalse(saved.isDeleted)
    }

    @Test
    fun `toggleImportant flips the flag`() = runTest {
        val n = addNoteToDb(title = "A", important = false)
        createViewModel()

        vm.toggleImportant(n)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(db[n.id]!!.isImportant)

        vm.toggleImportant(db[n.id]!!)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(db[n.id]!!.isImportant)
    }

    @Test
    fun `toggleCompleted flips the flag`() = runTest {
        val n = addNoteToDb(title = "A")
        createViewModel()

        vm.toggleCompleted(n)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(db[n.id]!!.isCompleted)
    }

    @Test
    fun `softDelete marks note deleted and restore brings it back`() = runTest {
        val n = addNoteToDb(title = "A")
        createViewModel()

        vm.softDelete(n.id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(db[n.id]!!.isDeleted)

        vm.restoreNote(n.id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(db[n.id]!!.isDeleted)
    }

    @Test
    fun `reorderNotes persists sort_order by index`() = runTest {
        val a = addNoteToDb(title = "A", sortOrder = 0)
        val b = addNoteToDb(title = "B", sortOrder = 1)
        val c = addNoteToDb(title = "C", sortOrder = 2)
        createViewModel()

        vm.reorderNotes(listOf(c, a, b))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.updateSortOrder(c.id, 0) }
        coVerify { repository.updateSortOrder(a.id, 1) }
        coVerify { repository.updateSortOrder(b.id, 2) }
        assertEquals(0, db[c.id]!!.sortOrder)
        assertEquals(1, db[a.id]!!.sortOrder)
        assertEquals(2, db[b.id]!!.sortOrder)
    }

    @Test
    fun `search filters notes by title and content`() = runTest {
        addNoteToDb(title = "Apple", content = "red fruit")
        addNoteToDb(title = "Banana", content = "yellow")
        createViewModel()
        collectNotes()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setSearchQuery("app")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Apple"), titles(vm.notes.value))

        vm.setSearchQuery("yellow")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Banana"), titles(vm.notes.value))

        vm.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.notes.value.size)
    }

    @Test
    fun `updatedAt is refreshed on update`() = runTest {
        val n = addNoteToDb(title = "A")
        createViewModel()

        vm.update(n.copy(title = "A2"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("A2", db[n.id]!!.title)
    }
}
