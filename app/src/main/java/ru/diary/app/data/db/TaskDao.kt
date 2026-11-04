package ru.diary.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.model.Task

@Dao
interface TaskDao {

    /**
     * Все задачи на конкретную дату (не удалённые, не черновики).
     * Порядок: сначала задачи со временем (по времени, от ранних к поздним),
     * затем задачи без времени (по sort_order, затем по порядку создания).
     */
    @Query("SELECT * FROM tasks WHERE date = :date AND is_deleted = 0 AND is_draft = 0 ORDER BY time IS NULL, time ASC, sort_order ASC, id ASC")
    fun observeTasksForDate(date: String): Flow<List<Task>>

    /** Все задачи в диапазоне дат startDate..endDate (не удалённые, не черновики). */
    @Query("SELECT * FROM tasks WHERE date BETWEEN :startDate AND :endDate AND is_deleted = 0 AND is_draft = 0 ORDER BY date ASC, time IS NULL, time ASC, sort_order ASC, id ASC")
    fun observeTasksBetween(startDate: String, endDate: String): Flow<List<Task>>

    /** Все задачи (для перепланирования напоминаний после перезагрузки). */
    @Query("SELECT * FROM tasks WHERE remind = 1 AND is_completed = 0 AND is_deleted = 0 AND is_draft = 0")
    suspend fun getAllPendingReminders(): List<Task>

    /** Уникальные даты, на которые есть задачи (не удалённые, не черновики). */
    @Query("SELECT DISTINCT date FROM tasks WHERE is_deleted = 0 AND is_draft = 0")
    fun observeDatesWithTasks(): Flow<List<String>>

    /** Уникальные даты, на которые есть важные задачи (не удалённые, не черновики). */
    @Query("SELECT DISTINCT date FROM tasks WHERE is_deleted = 0 AND is_important = 1 AND is_draft = 0")
    fun observeDatesWithImportantTasks(): Flow<List<String>>

    /** Невыполненные задачи с autoMove=1, у которых дата раньше указанной (не черновики). */
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 AND is_completed = 0 AND auto_move = 1 AND is_draft = 0 AND date < :today")
    suspend fun getOverdueAutoMoveTasks(today: String): List<Task>

    /** Удалённые задачи, отсортированные по дате удаления (сначала новые). */
    @Query("SELECT * FROM tasks WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<Task>>

    /** Восстановить задачу из корзины. */
    @Query("UPDATE tasks SET is_deleted = 0, deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    /** Максимальный sort_order среди задач на дату (null, если задач нет; черновики не учитываются). */
    @Query("SELECT MAX(sort_order) FROM tasks WHERE date = :date AND is_deleted = 0 AND is_draft = 0")
    suspend fun getMaxSortOrder(date: String): Int?

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Перенос задачи на другую дату (для невыполненных задач). */
    @Query("UPDATE tasks SET date = :newDate WHERE id = :taskId")
    suspend fun moveToDate(taskId: Long, newDate: String)

    /** Отметка выполнения. */
    @Query("UPDATE tasks SET is_completed = :completed WHERE id = :taskId")
    suspend fun setCompleted(taskId: Long, completed: Boolean)

    /** Обновить sort_order для списка задач (batch). */
    @Query("UPDATE tasks SET sort_order = :order WHERE id = :taskId")
    suspend fun updateSortOrder(taskId: Long, order: Int)

    /** Все материализованные вхождения серии (для отмены их будильников). */
    @Query("SELECT * FROM tasks WHERE repeat_origin_id = :templateId AND is_deleted = 0")
    suspend fun getGeneratedInstances(templateId: Long): List<Task>

    /** Удалить порождённые копии повторяющейся задачи (при удалении серии). */
    @Query("DELETE FROM tasks WHERE repeat_origin_id = :templateId")
    suspend fun deleteGeneratedInstances(templateId: Long)

    /**
     * Удалить только незавершённые порождённые вхождения (при редактировании
     * шаблона). Завершённые копии остаются как история.
     */
    @Query("DELETE FROM tasks WHERE repeat_origin_id = :templateId AND is_completed = 0")
    suspend fun deletePendingGeneratedInstances(templateId: Long)

    /** Удалить порождённую копию на указанную дату (чтобы не плодить дубли). */
    @Query("DELETE FROM tasks WHERE repeat_origin_id = :templateId AND date = :date")
    suspend fun deleteGeneratedInstanceAt(templateId: Long, date: String)

    /** Выполнена ли порождённая копия повтора на указанную дату. */
    @Query("SELECT COUNT(*) FROM tasks WHERE repeat_origin_id = :templateId AND date = :date AND is_completed = 1")
    suspend fun isOccurrenceCompleted(templateId: Long, date: String): Boolean

    /** Удалить из корзины записи старше threshold (мс). */
    @Query("DELETE FROM tasks WHERE is_deleted = 1 AND deleted_at IS NOT NULL AND deleted_at < :threshold")
    suspend fun deleteOldTrash(threshold: Long)

    /** Задачи-шаблоны для повторения (не удалённые, не выполненные, не черновики, с повторением). */
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 AND is_completed = 0 AND is_draft = 0 AND repeat_type != 'NONE'")
    fun observeRecurringTemplates(): Flow<List<Task>>

    /** Все даты, на которые есть активные повторяющиеся задачи (не черновики). */
    @Query("SELECT DISTINCT date FROM tasks WHERE is_deleted = 0 AND is_draft = 0 AND repeat_type != 'NONE'")
    fun observeRecurringDates(): Flow<List<String>>

    /** Все черновики: сначала по кастомному порядку (sort_order), затем по порядку создания. */
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 AND is_draft = 1 ORDER BY sort_order ASC, id ASC")
    fun observeDrafts(): Flow<List<Task>>

    @Query("DELETE FROM tasks WHERE updated_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}