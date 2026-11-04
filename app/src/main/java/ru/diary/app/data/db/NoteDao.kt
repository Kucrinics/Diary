package ru.diary.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.model.Note

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE is_deleted = 0 ORDER BY sort_order ASC, updated_at DESC, id DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("UPDATE notes SET sort_order = :order WHERE id = :noteId")
    suspend fun updateSortOrder(noteId: Long, order: Int)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** РЈРґР°Р»С‘РЅРЅС‹Рµ Р·Р°РјРµС‚РєРё, РѕС‚СЃРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Рµ РїРѕ РґР°С‚Рµ СѓРґР°Р»РµРЅРёСЏ (СЃРЅР°С‡Р°Р»Р° РЅРѕРІС‹Рµ). */
    @Query("SELECT * FROM notes WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<Note>>

    /** Р’РѕСЃСЃС‚Р°РЅРѕРІРёС‚СЊ Р·Р°РјРµС‚РєСѓ РёР· РєРѕСЂР·РёРЅС‹. */
    @Query("UPDATE notes SET is_deleted = 0, deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    /** РЈРЅРёРєР°Р»СЊРЅС‹Рµ РґР°С‚С‹, РЅР° РєРѕС‚РѕСЂС‹Рµ РµСЃС‚СЊ РІР°Р¶РЅС‹Рµ Р·Р°РјРµС‚РєРё. */
    @Query("SELECT DISTINCT date FROM notes WHERE is_deleted = 0 AND is_important = 1")
    fun observeDatesWithImportantNotes(): Flow<List<String>>

    @Query("DELETE FROM notes WHERE is_deleted = 1 AND deleted_at IS NOT NULL AND deleted_at < :threshold")
    suspend fun deleteOldTrash(threshold: Long): Int

    @Query("DELETE FROM notes WHERE updated_at < :cutoff")
    suspend fun deleteNotesOlderThan(cutoff: Long): Int
}
