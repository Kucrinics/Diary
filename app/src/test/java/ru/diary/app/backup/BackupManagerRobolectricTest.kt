package ru.diary.app.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.diary.app.data.db.AppDatabase
import ru.diary.app.data.model.Note
import java.io.File

/**
 * Проверяет живой путь резервного копирования через external files dir (SDK 28):
 * фоновый бэкап при сворачивании, ротацию и автоматическое восстановление.
 * SDK 28 выбран, потому что Robolectric нативно поддерживает файловый путь,
 * а не теневой MediaStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupManagerRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(BackupConstants.DB_NAME).delete()
        context.getSharedPreferences("diary_backup", Context.MODE_PRIVATE).edit().clear().commit()
        backupsDir().deleteRecursively()
        db = Room.databaseBuilder(context, AppDatabase::class.java, BackupConstants.DB_NAME)
            .allowMainThreadQueries()
            .build()
        backupManager = BackupManager(context)
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
    }

    private fun backupsDir(): File = File(context.getExternalFilesDir(null), "backups")

    private fun backupCount(): Int =
        backupsDir().listFiles { f -> f.name.endsWith(BackupConstants.SUFFIX) }?.size ?: 0

    private fun clearDatabaseFiles() {
        val dbPath = context.getDatabasePath(BackupConstants.DB_NAME).absolutePath
        File(dbPath).delete()
        File("$dbPath-wal").delete()
        File("$dbPath-shm").delete()
        File("$dbPath-journal").delete()
    }

    @Test
    fun `background backup writes an archive`() = runBlocking {
        db.noteDao().insert(Note(title = "Hello", date = "2026-10-18"))

        backupManager.backup(db)

        assertEquals("должна появиться ровно одна копия", 1, backupCount())
    }

    @Test
    fun `background backup skips when there is no data`() = runBlocking {
        backupManager.backup(db)

        assertEquals("пустая БД не должна бэкапиться", 0, backupCount())
    }

    @Test
    fun `backup keeps at most six archives`() = runBlocking {
        db.noteDao().insert(Note(title = "Data", date = "2026-10-18"))

        repeat(8) { backupManager.backup(db) }

        assertTrue("число копий не должно превышать лимит", backupCount() in 1..6)
    }

    @Test
    fun `restoreIfNeeded restores from backup when database is missing`() = runBlocking {
        db.noteDao().insert(Note(title = "FromBackup", date = "2026-10-18"))
        backupManager.backup(db)
        assertEquals("копия должна быть создана", 1, backupCount())

        // «Чистая установка»: убираем БД и отметку о восстановлении.
        db.close()
        clearDatabaseFiles()
        context.getSharedPreferences("diary_backup", Context.MODE_PRIVATE).edit().clear().commit()

        backupManager.restoreIfNeeded()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, BackupConstants.DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val note = reopened.noteDao().getById(1L)
            assertNotNull("заметка должна восстановиться из копии", note)
            assertEquals("FromBackup", note!!.title)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `restoreIfNeeded does not overwrite an existing database`() = runBlocking {
        // Копия с «устаревшим» содержимым…
        db.noteDao().insert(Note(title = "Stale", date = "2020-01-01"))
        backupManager.backup(db)
        assertEquals(1, backupCount())
        db.close()

        // …и свежие данные пользователя поверх.
        clearDatabaseFiles()
        context.getSharedPreferences("diary_backup", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.databaseBuilder(context, AppDatabase::class.java, BackupConstants.DB_NAME)
            .allowMainThreadQueries()
            .build()
        db.noteDao().insert(Note(title = "Fresh", date = "2026-10-20"))
        db.close()

        backupManager.restoreIfNeeded()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, BackupConstants.DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val note = reopened.noteDao().getById(1L)
            assertNotNull("свежая заметка должна остаться", note)
            assertEquals("Fresh", note!!.title)
        } finally {
            reopened.close()
        }
    }
}
