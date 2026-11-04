package ru.diary.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.db.AttachmentDao
import ru.diary.app.data.model.Attachment

class AttachmentRepository(private val dao: AttachmentDao) {

    fun observeByNoteId(noteId: Long): Flow<List<Attachment>> = dao.observeByNoteId(noteId)

    suspend fun getById(id: Long): Attachment? = dao.getById(id)

    suspend fun add(attachment: Attachment): Long = dao.insert(attachment)

    suspend fun delete(attachment: Attachment) = dao.delete(attachment)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteByNoteId(noteId: Long) = dao.deleteByNoteId(noteId)
}
