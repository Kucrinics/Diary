package ru.diary.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.model.Attachment

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE note_id = :noteId ORDER BY added_at ASC")
    fun observeByNoteId(noteId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): Attachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE note_id = :noteId")
    suspend fun deleteByNoteId(noteId: Long)
}
