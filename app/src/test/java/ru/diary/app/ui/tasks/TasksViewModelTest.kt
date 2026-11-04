package ru.diary.app.ui.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.RepeatType
import ru.diary.app.data.model.Task
import ru.diary.app.data.model.TaskOccurrences
import ru.diary.app.data.repository.TaskRepository
import ru.diary.app.notifications.AlarmScheduler
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TaskRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var vm: TasksViewModel

    private val db = mutableMapOf<Long, Task>()
    private var nextId = 1000L

    private val today = DateUtils.today()
    private val yesterday = DateUtils.toIsoDate(DateUtils.parseDate(today).minusDays(1))
    private val tomorrow = DateUtils.toIsoDate(DateUtils.parseDate(today).plusDays(1))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = false)
        alarmScheduler = mockk(relaxed = true)

        coEvery { repository.observeForDate(any()) } answers {
            val date = arg<String>(0)
            MutableStateFlow(db.values.filter { it.date == date && !it.isDeleted }.toList())
        }
        coEvery { repository.observeBetween(any(), any()) } answers {
            val start = arg<String>(0)
            val end = arg<String>(1)
            MutableStateFlow(db.values.filter { !it.isDeleted && it.date >= start && it.date <= end }.toList())
        }
        coEvery { repository.observeRecurringTemplates() } answers {
            MutableStateFlow(db.values.filter {
                !it.isDeleted && !it.isCompleted && it.repeatType != RepeatType.NONE.value
            }.toList())
        }
        coEvery { repository.getById(any()) } answers { db[arg<Long>(0)] }
        coEvery { repository.add(any()) } answers {
            val task = arg<Task>(0)
            val id = nextId++
            db[id] = task.copy(id = id)
            id
        }
        coEvery { repository.update(any()) } answers {
            val task = arg<Task>(0)
            db[task.id] = task
        }
        coEvery { repository.setCompleted(any(), any()) } answers {
            val id = arg<Long>(0)
            val completed = arg<Boolean>(1)
            db[id]?.let { db[id] = it.copy(isCompleted = completed) }
        }
        coEvery { repository.moveToDate(any(), any()) } answers {
            val id = arg<Long>(0)
            val newDate = arg<String>(1)
            db[id]?.let { db[id] = it.copy(date = newDate) }
        }
        coEvery { repository.deleteGeneratedInstances(any()) } answers {
            val templateId = arg<Long>(0)
            db.entries.removeAll { it.value.repeatOriginId == templateId }
        }
        coEvery { repository.getGeneratedInstances(any()) } answers {
            val templateId = arg<Long>(0)
            db.values.filter { it.repeatOriginId == templateId && !it.isDeleted }
        }
        coEvery { repository.deletePendingGeneratedInstances(any()) } answers {
            val templateId = arg<Long>(0)
            db.entries.removeAll {
                it.value.repeatOriginId == templateId && !it.value.isCompleted
            }
        }
        coEvery { repository.deleteGeneratedInstanceAt(any(), any()) } answers {
            val templateId = arg<Long>(0)
            val date = arg<String>(1)
            db.entries.removeAll { it.value.repeatOriginId == templateId && it.value.date == date }
        }
        coEvery { repository.restore(any()) } answers {
            val id = arg<Long>(0)
            db[id]?.let { db[id] = it.copy(isDeleted = false, deletedAt = null) }
        }
        coEvery { repository.updateSortOrder(any(), any()) } answers {
            val id = arg<Long>(0)
            val order = arg<Int>(1)
            db[id]?.let { db[id] = it.copy(sortOrder = order) }
        }
        coEvery { repository.observeTrash() } answers {
            MutableStateFlow(db.values.filter { it.isDeleted }.toList())
        }
        coEvery { repository.observeDrafts() } answers {
            MutableStateFlow(
                db.values.filter { it.isDraft && !it.isDeleted }
                    .sortedWith(compareBy<Task> { it.sortOrder }.thenBy { it.id })
            )
        }
        coEvery { repository.getOverdueAutoMoveTasks(any()) } returns emptyList()
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel() {
        vm = TasksViewModel(repository, alarmScheduler)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun addTaskToDb(
        title: String = "Test", date: String = today,
        repeatType: String = RepeatType.NONE.value, repeatOriginId: Long? = null,
        isCompleted: Boolean = false, isImportant: Boolean = false,
        autoMove: Boolean = true, repeatEndDate: String? = null,
        repeatIntervalDays: Int? = null, time: String? = null, remind: Boolean = false
    ): Task {
        val task = Task(title = title, description = "", date = date,
            repeatType = repeatType, repeatOriginId = repeatOriginId,
            isCompleted = isCompleted, isImportant = isImportant, autoMove = autoMove,
            repeatEndDate = repeatEndDate, repeatIntervalDays = repeatIntervalDays,
            time = time, remind = remind)
        val id = nextId++; db[id] = task.copy(id = id)
        return db[id] ?: error("Task not stored")
    }

    private fun addDraftToDb(title: String): Task {
        val task = Task(title = title, description = "", date = today, isDraft = true)
        val id = nextId++; db[id] = task.copy(id = id)
        return db[id] ?: error("Task not stored")
    }

    // ======================== toggleCompleted ========================

    @Test
    fun `toggleCompleted on virtual task materializes copy and marks completed`() = runTest {
        val template = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        createViewModel()

        val virtualTask = vm.state.value.tasks.find { it.id < 0 && abs(it.id) - 1000 == template.id }
        assertNotNull("Virtual task should be visible", virtualTask)

        vm.toggleCompleted(virtualTask!!)
        testDispatcher.scheduler.advanceUntilIdle()

        val m = db.values.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull("Materialized copy for today", m)
        assertTrue("Completed", m?.isCompleted == true)
        assertEquals(RepeatType.NONE.value, m?.repeatType)

        val n = db.values.find { it.repeatOriginId == template.id && it.date == tomorrow }
        assertNotNull("Next occurrence for tomorrow", n)
        assertFalse("Next not completed", n?.isCompleted == true)

        val st = vm.state.value.tasks.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull("State has materialized copy", st)
        assertTrue("State copy completed", st?.isCompleted == true)
    }

    @Test
    fun `toggleCompleted on template task materializes copy`() = runTest {
        val template = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        createViewModel()

        vm.toggleCompleted(template)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Template unchanged", db[template.id]?.isCompleted == true)
        val m = db.values.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull("Materialized copy exists", m)
        assertTrue("Completed", m?.isCompleted == true)
    }

    @Test
    fun `toggleCompleted on materialized copy toggles directly`() = runTest {
        val copy = addTaskToDb(title = "C", date = today, repeatOriginId = 500L)
        createViewModel()

        vm.toggleCompleted(copy)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Toggled to completed", db[copy.id]?.isCompleted == true)

        vm.toggleCompleted(db[copy.id]!!)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Toggled back", db[copy.id]?.isCompleted == true)
    }

    @Test
    fun `toggleCompleted on regular task toggles via setCompleted`() = runTest {
        val task = addTaskToDb(title = "N", date = today)
        createViewModel()

        vm.toggleCompleted(task)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setCompleted(task.id, true) }
    }

    @Test
    fun `completing regular task with reminder cancels its alarm`() = runTest {
        val task = addTaskToDb(title = "R", date = today, time = "10:00", remind = true)
        createViewModel()

        vm.toggleCompleted(task)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { alarmScheduler.cancel(task.id.toInt()) }
    }

    @Test
    fun `uncompleting regular task reschedules reminder`() = runTest {
        val task = addTaskToDb(title = "R", date = today, time = "10:00", remind = true, isCompleted = true)
        createViewModel()

        vm.toggleCompleted(task)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { alarmScheduler.cancel(task.id.toInt()) }
        verify { alarmScheduler.schedule(task.id.toInt(), "R", today, "10:00") }
    }

    @Test
    fun `completing recurring template reschedules next occurrence alarm`() = runTest {
        val template = addTaskToDb(
            title = "R", date = today, time = "10:00", remind = true,
            repeatType = RepeatType.DAILY.value
        )
        createViewModel()

        vm.toggleCompleted(template)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { alarmScheduler.cancel(template.id.toInt()) }
        val next = db.values.first { it.repeatOriginId == template.id && it.date == tomorrow }
        verify { alarmScheduler.schedule(next.id.toInt(), "R", tomorrow, "10:00") }
    }

    @Test
    fun `completing repeating template without remind cancels but does not schedule`() = runTest {
        val template = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        createViewModel()

        vm.toggleCompleted(template)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { alarmScheduler.cancel(template.id.toInt()) }
        verify(exactly = 0) { alarmScheduler.schedule(any(), any(), any(), any()) }
    }

    // ======================== toggleImportant ========================

    @Test
    fun `toggleImportant on virtual task materializes copy`() = runTest {
        val template = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        createViewModel()

        val virtualTask = vm.state.value.tasks.find { it.id < 0 && abs(it.id) - 1000 == template.id }
        assertNotNull(virtualTask)

        vm.toggleImportant(virtualTask!!)
        testDispatcher.scheduler.advanceUntilIdle()

        val m = db.values.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull("Materialized copy for today", m)
        assertTrue("Important", m?.isImportant == true)
        assertFalse("Template unchanged", db[template.id]?.isImportant == true)
        assertEquals("Copy stays linked to the series", template.id, m?.repeatOriginId)
        assertEquals("Copy repeat type reset to NONE", RepeatType.NONE.value, m?.repeatType)

        val st = vm.state.value.tasks.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull("State has copy", st)
        assertTrue("State copy important", st?.isImportant == true)
    }

    @Test
    fun `toggleImportant on template task materializes copy`() = runTest {
        val template = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        createViewModel()

        vm.toggleImportant(template)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Template unchanged", db[template.id]?.isImportant == true)
        val m = db.values.find { it.repeatOriginId == template.id && it.date == today }
        assertNotNull(m); assertTrue(m?.isImportant == true)
    }

    @Test
    fun `toggleImportant on materialized copy toggles directly`() = runTest {
        val copy = addTaskToDb(title = "C", date = today, repeatOriginId = 500L)
        createViewModel()

        vm.toggleImportant(copy)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(db[copy.id]?.isImportant == true)

        vm.toggleImportant(db[copy.id]!!)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(db[copy.id]?.isImportant == true)
    }

    @Test
    fun `toggleImportant on regular task calls update`() = runTest {
        val task = addTaskToDb(title = "N", date = today)
        createViewModel()

        vm.toggleImportant(task)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.update(match { it.id == task.id && it.isImportant }) }
    }

    // ======================== deleteTask ========================

    @Test
    fun `deleteTask on virtual task deletes all generated and template`() = runTest {
        val t = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        addTaskToDb(date = tomorrow, repeatOriginId = t.id)
        createViewModel()

        val virt = t.copy(id = -(t.id + 1000), date = today)
        vm.deleteTask(virt)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, db.values.count { it.repeatOriginId == t.id })
        assertTrue(db[t.id]?.isDeleted == true)
    }

    @Test
    fun `deleteTask on template task deletes all generated and template`() = runTest {
        val t = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        addTaskToDb(date = today, repeatOriginId = t.id)
        addTaskToDb(date = tomorrow, repeatOriginId = t.id)
        createViewModel()

        vm.deleteTask(t)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, db.values.count { it.repeatOriginId == t.id })
        assertTrue(db[t.id]?.isDeleted == true)
    }

    @Test
    fun `deleteTask on materialized copy deletes all generated and template`() = runTest {
        val t = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        addTaskToDb(date = today, repeatOriginId = t.id)
        addTaskToDb(date = tomorrow, repeatOriginId = t.id)
        createViewModel()

        val copy = db.values.first { it.repeatOriginId == t.id }
        vm.deleteTask(copy)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, db.values.count { it.repeatOriginId == t.id })
        assertTrue(db[t.id]?.isDeleted == true)
    }

    @Test
    fun `deleteTask on regular task soft-deletes only that task`() = runTest {
        val task = addTaskToDb(title = "A")
        val other = addTaskToDb(title = "B")
        createViewModel()

        vm.deleteTask(task)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(db[task.id]?.isDeleted == true)
        assertFalse(db[other.id]?.isDeleted == true)
    }

    // ======================== addTask ========================

    @Test
    fun `addTask creates task in db`() = runTest {
        createViewModel()
        vm.addTask(title = "New", description = "Desc", date = today, time = null, categoryId = null, remind = false)
        testDispatcher.scheduler.advanceUntilIdle()
        val added = db.values.find { it.title == "New" }
        assertNotNull(added)
        assertEquals("Desc", added?.description)
        assertEquals(today, added?.date)
    }

    @Test
    fun `addTask stores selected color`() = runTest {
        createViewModel()
        val color = 0xFFE53935.toInt()
        vm.addTask(
            title = "Colored", description = "", date = today, time = null,
            categoryId = null, color = color, remind = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(color, db.values.find { it.title == "Colored" }?.color)
    }

    @Test
    fun `addTask without color keeps it empty`() = runTest {
        createViewModel()
        vm.addTask(
            title = "Plain", description = "", date = today, time = null,
            categoryId = null, remind = false
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, db.values.find { it.title == "Plain" }?.color)
    }

    @Test
    fun `addTask with remind schedules alarm`() = runTest {
        createViewModel()
        vm.addTask(title = "Reminder", description = "", date = today, time = "10:00", categoryId = null, remind = true)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { alarmScheduler.schedule(any(), "Reminder", today, "10:00") }
    }

    @Test
    fun `addTask with remind and no time schedules default morning alarm`() = runTest {
        createViewModel()
        vm.addTask(title = "Morning", description = "", date = today, time = null, categoryId = null, remind = true)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { alarmScheduler.schedule(any(), "Morning", today, null) }
    }

    @Test
    fun `addTask without remind does not schedule alarm`() = runTest {
        createViewModel()
        vm.addTask(title = "Quiet", description = "", date = today, time = "10:00", categoryId = null, remind = false)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { alarmScheduler.schedule(any(), any(), any(), any()) }
    }

    @Test
    fun `addTask with isDraft stores draft flag and does not schedule alarm`() = runTest {
        createViewModel()
        vm.addTask(title = "DraftTask", description = "", date = today, time = "10:00", categoryId = null, remind = true, isDraft = true)
        testDispatcher.scheduler.advanceUntilIdle()
        val added = db.values.find { it.title == "DraftTask" }
        assertNotNull(added)
        assertTrue(added?.isDraft == true)
        verify(exactly = 0) { alarmScheduler.schedule(any(), any(), any(), any()) }
    }

    @Test
    fun `updateTask to draft clears alarm`() = runTest {
        val t = addTaskToDb(title = "BecomeDraft", time = "10:00", remind = true)
        createViewModel()
        vm.updateTask(t.copy(isDraft = true))
        testDispatcher.scheduler.advanceUntilIdle()
        verify { alarmScheduler.cancel(t.id.toInt()) }
        verify(exactly = 0) { alarmScheduler.schedule(any(), any(), any(), any()) }
    }

    @Test
    fun `updateTask non-draft schedules alarm`() = runTest {
        val t = addTaskToDb(title = "StayReal", time = "10:00", remind = true)
        createViewModel()
        vm.updateTask(t)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { alarmScheduler.schedule(t.id.toInt(), "StayReal", today, "10:00") }
    }

    // ======================== updateTask ========================

    @Test
    fun `updateTask changes title and description`() = runTest {
        val task = addTaskToDb(title = "Old"); createViewModel()
        vm.updateTask(task.copy(title = "New", description = "New Desc"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("New", db[task.id]?.title)
        assertEquals("New Desc", db[task.id]?.description)
    }

    @Test
    fun `updateTask deletes pending generated instances when repeat type changes`() = runTest {
        val t = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        addTaskToDb(date = tomorrow, repeatOriginId = t.id); createViewModel()
        vm.updateTask(t.copy(repeatType = RepeatType.NONE.value))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, db.values.count { it.repeatOriginId == t.id })
    }

    @Test
    fun `updateTask on virtual occurrence updates series template`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, repeatType = RepeatType.DAILY.value,
            autoMove = false
        )
        createViewModel()
        val virtual = vm.state.value.tasks.first { it.id == TaskOccurrences.virtualId(template.id) }

        vm.updateTask(
            virtual.copy(title = "Updated", description = "New desc", time = "12:00"),
            original = virtual
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = db[template.id]
        assertNotNull("Template must still exist", stored)
        assertEquals("Updated", stored?.title)
        assertEquals("New desc", stored?.description)
        assertEquals("12:00", stored?.time)
        // Обычная правка вхождения не должна переносить дату-якорь серии.
        assertEquals(yesterday, stored?.date)
        assertEquals("No row may be written with a virtual id", null, db[-1L])
    }

    @Test
    fun `updateTask on virtual occurrence copies color to series template`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, repeatType = RepeatType.DAILY.value,
            autoMove = false
        )
        createViewModel()
        val virtual = vm.state.value.tasks.first { it.id == TaskOccurrences.virtualId(template.id) }
        val color = 0xFF1E88E5.toInt()

        vm.updateTask(virtual.copy(color = color), original = virtual)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(color, db[template.id]?.color)
    }

    @Test
    fun `updateTask on virtual occurrence moves series anchor when date changed`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, repeatType = RepeatType.DAILY.value,
            autoMove = false
        )
        createViewModel()
        val virtual = vm.state.value.tasks.first { it.id == TaskOccurrences.virtualId(template.id) }

        vm.updateTask(virtual.copy(date = tomorrow, title = "Moved"), original = virtual)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(tomorrow, db[template.id]?.date)
        assertEquals("Moved", db[template.id]?.title)
    }

    @Test
    fun `updateTask on virtual occurrence keeps completed copies as history`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, repeatType = RepeatType.DAILY.value,
            autoMove = false
        )
        val completed = addTaskToDb(
            title = "R", date = today, repeatOriginId = template.id, isCompleted = true
        )
        val dayAfterTomorrow = DateUtils.toIsoDate(DateUtils.parseDate(today).plusDays(2))
        val pending = addTaskToDb(
            title = "R", date = tomorrow, repeatOriginId = template.id
        )
        createViewModel()
        vm.selectDate(dayAfterTomorrow)
        testDispatcher.scheduler.advanceUntilIdle()
        val virtual = vm.state.value.tasks.first {
            it.id == TaskOccurrences.virtualId(template.id) && it.date == dayAfterTomorrow
        }

        vm.updateTask(virtual.copy(title = "Updated"), original = virtual)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Completed copy is history and must stay", true, db[completed.id]?.isCompleted)
        assertEquals("Pending copy was regenerated", null, db[pending.id])
        assertEquals("Updated", db[template.id]?.title)
    }

    @Test
    fun `disabling repeat on virtual occurrence keeps task on occurrence date`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, repeatType = RepeatType.DAILY.value,
            autoMove = false
        )
        createViewModel()
        val virtual = vm.state.value.tasks.first { it.id == TaskOccurrences.virtualId(template.id) }

        vm.updateTask(
            virtual.copy(title = "One-off", repeatType = RepeatType.NONE.value),
            original = virtual
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(today, db[template.id]?.date)
        assertEquals(RepeatType.NONE.value, db[template.id]?.repeatType)
    }

    // ======================== moveTask ========================

    @Test
    fun `moveTask changes task date`() = runTest {
        val task = addTaskToDb(title = "M"); createViewModel()
        vm.moveTask(task.id, tomorrow)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(tomorrow, db[task.id]?.date)
    }

    @Test
    fun `moveTask on virtual task moves template`() = runTest {
        val t = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        createViewModel()
        vm.moveTask(-(t.id + 1000), tomorrow)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(tomorrow, db[t.id]?.date)
    }

    // ======================== copyTask ========================

    @Test
    fun `copyTask creates copy at new date`() = runTest {
        val task = addTaskToDb(title = "Original"); createViewModel()
        vm.copyTask(task.id, tomorrow)
        testDispatcher.scheduler.advanceUntilIdle()
        val copy = db.values.find { it.title == "Original" && it.date == tomorrow }
        assertNotNull(copy)
        assertFalse(copy?.isCompleted == true)
        assertFalse("Copy has different id", copy?.id == task.id)
    }

    // ======================== сортировка и перемещение ========================

    private fun titles(tasks: List<Task>): List<String> = tasks.map { it.title }

    @Test
    fun `tasks with time are sorted ascending and placed before tasks without time`() = runTest {
        addTaskToDb(title = "B", time = null, date = today)
        addTaskToDb(title = "Late", time = "18:00", date = today)
        addTaskToDb(title = "Early", time = "08:00", date = today)
        addTaskToDb(title = "A", time = null, date = today)
        createViewModel()

        assertEquals(listOf("Early", "Late", "B", "A"), titles(vm.state.value.tasks))
    }

    @Test
    fun `tasks without time keep creation order by default`() = runTest {
        addTaskToDb(title = "Old", time = null, date = today)
        addTaskToDb(title = "New", time = null, date = today)
        createViewModel()

        assertEquals(listOf("Old", "New"), titles(vm.state.value.tasks))
    }

    @Test
    fun `week view sorts tasks by time within each day`() = runTest {
        addTaskToDb(title = "Late", time = "18:00", date = today)
        addTaskToDb(title = "Early", time = "08:00", date = today)
        createViewModel()

        vm.setViewMode(TaskViewMode.WEEK)
        testDispatcher.scheduler.advanceUntilIdle()

        val names = titles(vm.state.value.dayGroups.first { it.date == today }.tasks)
        assertEquals(listOf("Early", "Late"), names)
    }

    @Test
    fun `month view sorts tasks by time within each day`() = runTest {
        addTaskToDb(title = "Late", time = "18:00", date = today)
        addTaskToDb(title = "Early", time = "08:00", date = today)
        createViewModel()

        vm.setViewMode(TaskViewMode.MONTH)
        testDispatcher.scheduler.advanceUntilIdle()

        val names = titles(vm.state.value.dayGroups.first { it.date == today }.tasks)
        assertEquals(listOf("Early", "Late"), names)
    }

    @Test
    fun `recurring virtual occurrence placed at its time position`() = runTest {
        addTaskToDb(title = "Rec", date = yesterday, time = "09:00", repeatType = RepeatType.DAILY.value)
        addTaskToDb(title = "Early", time = "08:00", date = today)
        addTaskToDb(title = "Late", time = "10:00", date = today)
        createViewModel()

        assertEquals(listOf("Early", "Rec", "Late"), titles(vm.state.value.tasks))
    }

    @Test
    fun `moveTaskUp reorders untimed tasks and reindexes sort_order`() = runTest {
        val a = addTaskToDb(title = "A", time = null, date = today)
        val b = addTaskToDb(title = "B", time = null, date = today)
        val c = addTaskToDb(title = "C", time = null, date = today)
        createViewModel()

        vm.moveTaskUp(c)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.updateSortOrder(a.id, 0) }
        coVerify { repository.updateSortOrder(c.id, 1) }
        coVerify { repository.updateSortOrder(b.id, 2) }
        assertEquals(listOf("A", "C", "B"), titles(vm.state.value.tasks))
    }

    @Test
    fun `moveTaskDown reorders untimed tasks`() = runTest {
        val a = addTaskToDb(title = "A", time = null, date = today)
        val b = addTaskToDb(title = "B", time = null, date = today)
        createViewModel()

        vm.moveTaskDown(a)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.updateSortOrder(a.id, 1) }
        coVerify { repository.updateSortOrder(b.id, 0) }
        assertEquals(listOf("B", "A"), titles(vm.state.value.tasks))
    }

    @Test
    fun `moveTaskUp at first position does nothing`() = runTest {
        val a = addTaskToDb(title = "A", time = null, date = today)
        addTaskToDb(title = "B", time = null, date = today)
        createViewModel()

        vm.moveTaskUp(a)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSortOrder(any(), any()) }
        assertEquals(listOf("A", "B"), titles(vm.state.value.tasks))
    }

    @Test
    fun `moveTaskDown of timed task does nothing`() = runTest {
        val timed = addTaskToDb(title = "T", time = "10:00", date = today)
        addTaskToDb(title = "U", time = null, date = today)
        createViewModel()

        vm.moveTaskDown(timed)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSortOrder(any(), any()) }
        assertEquals(listOf("T", "U"), titles(vm.state.value.tasks))
    }

    @Test
    fun `moveTaskDown in drafts reorders drafts`() = runTest {
        val a = addDraftToDb(title = "A")
        val b = addDraftToDb(title = "B")
        createViewModel()

        vm.setViewMode(TaskViewMode.DRAFTS)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("A", "B"), titles(vm.state.value.tasks))

        vm.moveTaskDown(a)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.updateSortOrder(b.id, 0) }
        coVerify { repository.updateSortOrder(a.id, 1) }
        assertEquals(listOf("B", "A"), titles(vm.state.value.tasks))
    }

    // ======================== navigation ========================

    @Test
    fun `selectDate updates state date`() = runTest {
        createViewModel()
        vm.selectDate(yesterday)
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(yesterday, vm.state.value.selectedDate)
    }

    @Test
    fun `nextDay advances date`() = runTest {
        createViewModel()
        vm.nextDay()
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        val expected = DateUtils.toIsoDate(DateUtils.parseDate(today).plusDays(1))
        assertEquals(expected, vm.state.value.selectedDate)
    }

    @Test
    fun `prevDay goes back`() = runTest {
        createViewModel()
        vm.prevDay()
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        val expected = DateUtils.toIsoDate(DateUtils.parseDate(today).minusDays(1))
        assertEquals(expected, vm.state.value.selectedDate)
    }

    @Test
    fun `goToToday resets to today`() = runTest {
        createViewModel(); vm.selectDate(yesterday)
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.goToToday()
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(today, vm.state.value.selectedDate)
    }

    // ======================== search ========================

    @Test
    fun `searchQuery filters tasks by title`() = runTest {
        val id1 = nextId++; db[id1] = Task(title = "Alpha", description = "", date = today, id = id1)
        val id2 = nextId++; db[id2] = Task(title = "Beta", description = "", date = today, id = id2)
        createViewModel()
        vm.setSearchQuery("Alpha")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.tasks.size)
        assertEquals("Alpha", vm.state.value.tasks[0].title)
        vm.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.tasks.size)
    }

    @Test
    fun `searchQuery filters tasks by description`() = runTest {
        val id1 = nextId++; db[id1] = Task(title = "T1", description = "find me", date = today, id = id1)
        val id2 = nextId++; db[id2] = Task(title = "T2", description = "other", date = today, id = id2)
        createViewModel()
        vm.setSearchQuery("find")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.tasks.size)
        assertEquals("T1", vm.state.value.tasks[0].title)
    }

    // ======================== performAutoMove ========================

    @Test
    fun `performAutoMove moves overdue tasks to today`() = runTest {
        val overdue = addTaskToDb(title = "O", date = yesterday, autoMove = true)
        coEvery { repository.getOverdueAutoMoveTasks(today) } returns listOf(overdue)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(today, db[overdue.id]?.date)
    }

    @Test
    fun `performAutoMove keeps recurring template anchor date`() = runTest {
        val template = addTaskToDb(
            title = "R", date = yesterday, autoMove = true,
            repeatType = RepeatType.DAILY.value
        )
        coEvery { repository.getOverdueAutoMoveTasks(today) } returns listOf(template)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(yesterday, db[template.id]?.date)
    }

    // ======================== virtual duplicate suppression ========================

    @Test
    fun `virtual task does not appear if materialized copy exists`() = runTest {
        val t = addTaskToDb(title = "R", date = yesterday, repeatType = RepeatType.DAILY.value)
        addTaskToDb(date = today, repeatOriginId = t.id)
        createViewModel()
        val state = vm.state.value
        assertFalse(state.tasks.any { it.id < 0 && abs(it.id) - 1000 == t.id })
        assertTrue(state.tasks.any { it.repeatOriginId == t.id })
    }

    @Test
    fun `repeating task appears once on its origin date`() = runTest {
        val t = addTaskToDb(title = "R", date = today, repeatType = RepeatType.DAILY.value)
        createViewModel()
        val state = vm.state.value
        assertTrue("Real template row is displayed", state.tasks.any { it.id == t.id })
        assertFalse("No virtual duplicate on the origin date",
            state.tasks.any { it.id < 0 && abs(it.id) - 1000 == t.id })
        assertEquals("Exactly one occurrence today",
            1, state.tasks.count { it.id == t.id || (it.id < 0 && abs(it.id) - 1000 == t.id) })
    }

    // ======================== INTERVAL repeat (every N days) ========================

    private fun daysAgo(n: Long): String = DateUtils.toIsoDate(DateUtils.parseDate(today).minusDays(n))

    @Test
    fun `INTERVAL virtual task appears on exact multiple day`() = runTest {
        val t = addTaskToDb(title = "R3", date = daysAgo(3), repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 3)
        createViewModel()
        val virtual = vm.state.value.tasks.find { it.id < 0 && abs(it.id) - 1000 == t.id }
        assertNotNull("Virtual task should appear on the exact interval day", virtual)
    }

    @Test
    fun `INTERVAL virtual task does not appear off schedule`() = runTest {
        val t = addTaskToDb(title = "R3", date = daysAgo(2), repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 3)
        createViewModel()
        assertFalse("No virtual task when (days from origin) % interval != 0",
            vm.state.value.tasks.any { it.id < 0 && abs(it.id) - 1000 == t.id })
    }

    @Test
    fun `INTERVAL task appears once on its origin date`() = runTest {
        val t = addTaskToDb(title = "R3", date = today, repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 3)
        createViewModel()
        val state = vm.state.value
        assertTrue("Real template row is displayed", state.tasks.any { it.id == t.id })
        assertFalse("No virtual duplicate on the origin date",
            state.tasks.any { it.id < 0 && abs(it.id) - 1000 == t.id })
        assertEquals("Exactly one occurrence today",
            1, state.tasks.count { it.id == t.id || (it.id < 0 && abs(it.id) - 1000 == t.id) })
    }

    @Test
    fun `INTERVAL with null or zero interval never repeats`() = runTest {
        addTaskToDb(title = "N1", date = daysAgo(3), repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = null)
        addTaskToDb(title = "N2", date = daysAgo(3), repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 0)
        createViewModel()
        assertTrue("No virtual tasks for invalid intervals", vm.state.value.tasks.none { it.id < 0 })
    }

    @Test
    fun `toggleCompleted on INTERVAL virtual task materializes next occurrence`() = runTest {
        val t = addTaskToDb(title = "R3", date = daysAgo(3), repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 3)
        createViewModel()

        val virtual = vm.state.value.tasks.find { it.id < 0 && abs(it.id) - 1000 == t.id }
        assertNotNull(virtual)
        vm.toggleCompleted(virtual!!)
        testDispatcher.scheduler.advanceUntilIdle()

        val todayCopy = db.values.find { it.repeatOriginId == t.id && it.date == today }
        assertNotNull("Materialized copy for today", todayCopy)
        assertTrue("Today copy completed", todayCopy?.isCompleted == true)

        val expectedNext = DateUtils.toIsoDate(DateUtils.parseDate(today).plusDays(3))
        val next = db.values.find { it.repeatOriginId == t.id && it.date == expectedNext }
        assertNotNull("Next occurrence materialized at origin + 3 days", next)
        assertFalse("Next occurrence not completed", next?.isCompleted == true)
    }

    @Test
    fun `addTask stores repeat interval days`() = runTest {
        createViewModel()
        vm.addTask(title = "I", description = "", date = today, time = null, categoryId = null,
            remind = false, repeatType = RepeatType.INTERVAL.value, repeatIntervalDays = 5)
        testDispatcher.scheduler.advanceUntilIdle()
        val added = db.values.find { it.title == "I" }
        assertNotNull(added)
        assertEquals(RepeatType.INTERVAL.value, added?.repeatType)
        assertEquals(5, added?.repeatIntervalDays)
    }

    // ======================== publishDraft ========================

    @Test
    fun `publishDraft clears draft flag and moves task to chosen date`() = runTest {
        val t = addTaskToDb(title = "D", date = yesterday)
        db[t.id] = t.copy(isDraft = true)
        createViewModel()

        vm.selectDate(yesterday)
        vm.publishDraft(t.id, tomorrow)
        testDispatcher.scheduler.advanceUntilIdle()

        val published = db[t.id]
        assertNotNull(published)
        assertFalse("isDraft cleared", published?.isDraft == true)
        assertEquals(tomorrow, published?.date)
    }

    @Test
    fun `publishDraft on unknown id does not update anything`() = runTest {
        createViewModel()
        vm.publishDraft(999999L, today)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.update(any()) }
    }
}
