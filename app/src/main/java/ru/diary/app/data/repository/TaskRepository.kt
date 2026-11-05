package ru.diary.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.db.TaskDao
import ru.diary.app.data.model.Task

/**
 * Инкапсулирует доступ к задачам. ViewModel работает только с репозиторием,
 * не зная о DAO и Room напрямую.
 */
class TaskRepository(private val dao: TaskDao) {

    fun observeForDate(date: String): Flow<List<Task>> = dao.observeTasksForDate(date)

    fun observeBetween(start: String, end: String): Flow<List<Task>> = dao.observeTasksBetween(start, end)

    suspend fun getById(id: Long): Task? = dao.getById(id)

    /**
     * Добавляет задачу. Новая задача получает sort_order = max + 1 по своей дате,
     * поэтому попадает в конец блока задач без времени (задачи со временем
     * упорядочиваются по времени, и sort_order на них не влияет).
     */
    suspend fun add(task: Task): Long {
        val maxOrder = dao.getMaxSortOrder(task.date)?.plus(1) ?: 0
        return dao.insert(task.copy(sortOrder = maxOrder, updatedAt = System.currentTimeMillis()))
    }

    suspend fun update(task: Task) = dao.update(task.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(task: Task) = dao.delete(task)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun moveToDate(taskId: Long, newDate: String) = dao.moveToDate(taskId, newDate)

    suspend fun setCompleted(taskId: Long, completed: Boolean) =
        dao.setCompleted(taskId, completed)

    suspend fun getPendingReminders(): List<Task> = dao.getAllPendingReminders()

    fun observeDatesWithTasks(): Flow<List<String>> = dao.observeDatesWithTasks()

    /** Все активные задачи (не удалённые, не черновики) — для агрегатов календаря. */
    fun observeAll(): Flow<List<Task>> = dao.observeAllActive()

    fun observeDatesWithImportantTasks(): Flow<List<String>> = dao.observeDatesWithImportantTasks()

    suspend fun getOverdueAutoMoveTasks(today: String): List<Task> = dao.getOverdueAutoMoveTasks(today)

    fun observeTrash(): Flow<List<Task>> = dao.observeTrash()

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun updateSortOrder(taskId: Long, order: Int) = dao.updateSortOrder(taskId, order)

    suspend fun getGeneratedInstances(templateId: Long) = dao.getGeneratedInstances(templateId)
    suspend fun deleteGeneratedInstances(templateId: Long) = dao.deleteGeneratedInstances(templateId)
    suspend fun deletePendingGeneratedInstances(templateId: Long) =
        dao.deletePendingGeneratedInstances(templateId)
    suspend fun deleteGeneratedInstanceAt(templateId: Long, date: String) = dao.deleteGeneratedInstanceAt(templateId, date)
    suspend fun isOccurrenceCompleted(templateId: Long, date: String) = dao.isOccurrenceCompleted(templateId, date)

    /** Удаляет записи корзины старше срока хранения (автоочистка при старте). */
    suspend fun purgeOldTrash() = dao.deleteOldTrash(DateUtils.trashRetentionThreshold())

    fun observeRecurringTemplates(): Flow<List<Task>> = dao.observeRecurringTemplates()

    fun observeRecurringDates(): Flow<List<String>> = dao.observeRecurringDates()

    fun observeDrafts(): Flow<List<Task>> = dao.observeDrafts()
}
