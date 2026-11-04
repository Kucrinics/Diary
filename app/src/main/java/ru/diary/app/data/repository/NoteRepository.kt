package ru.diary.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.db.NoteDao
import ru.diary.app.data.model.Note

class NoteRepository(private val dao: NoteDao) {

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    suspend fun getById(id: Long): Note? = dao.getById(id)

    suspend fun add(note: Note): Long = dao.insert(note)

    suspend fun update(note: Note) = dao.update(note)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    fun observeTrash(): Flow<List<Note>> = dao.observeTrash()

    fun observeDatesWithImportantNotes(): Flow<List<String>> = dao.observeDatesWithImportantNotes()

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun updateSortOrder(noteId: Long, order: Int) = dao.updateSortOrder(noteId, order)

    /** Удаляет записи корзины старше срока хранения (автоочистка при старте). */
    suspend fun purgeOldTrash() = dao.deleteOldTrash(DateUtils.trashRetentionThreshold())
}
