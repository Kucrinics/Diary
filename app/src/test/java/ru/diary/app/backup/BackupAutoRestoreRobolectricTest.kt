package ru.diary.app.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.diary.app.data.db.AppDatabase
import ru.diary.app.data.model.Note
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Проверяет автоматическое восстановление из локальной копии ([BackupManager.restoreIfNeeded]).
 * Использует SDK 28, чтобы работал путь внешних файлов (external files dir), а не MediaStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupAutoRestoreRobolectricTest {

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

    /**
     * Кладёт в папку копий архив с БД, содержащей единственную заметку [title].
     * Архив собирается напрямую через [BackupArchive.write] из байтов отдельной БД,
     * чтобы содержимое копии не совпадало с основным `diary.db`.
     */
    private fun writeBackupFile(fileName: String, title: String): File = runBlocking {
        val sourcePath = "src_${title}.db"
        context.getDatabasePath(sourcePath).delete()
        val source = Room.databaseBuilder(context, AppDatabase::class.java, sourcePath)
            .allowMainThreadQueries()
            .build()
        source.noteDao().insert(Note(title = title, date = "2026-10-18"))
        source.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { cursor -> cursor.moveToFirst() }
        source.close()

        val sourceBytes = context.getDatabasePath(sourcePath).readBytes()
        backupsDir().mkdirs()
        val target = File(backupsDir(), fileName)
        FileOutputStream(target).use { fos ->
            BackupArchive.write(fos, ByteArrayInputStream(sourceBytes), null)
        }
        target
    }

    @Test
    fun `restoreIfNeeded does not overwrite an existing database`() = runBlocking {
        // Свежие данные пользователя уже в БД.
        db.noteDao().insert(Note(title = "Fresh", date = "2026-10-20"))
        db.close()

        // Рядом лежит старая копия с другим содержимым.
        writeBackupFile("DiaryBackup_2020-01-01_00-00.diarybak", "Stale")

        backupManager.restoreIfNeeded()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, BackupConstants.DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val note = reopened.noteDao().getById(1L)
            assertNotNull("свежая заметка должна остаться на месте", note)
            assertEquals("Fresh", note!!.title)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `restoreIfNeeded restores from backup when database is missing`() = runBlocking {
        // «Чистая установка»: файла БД нет, копия рядом есть.
        writeBackupFile("DiaryBackup_2026-10-18_10-00.diarybak", "FromBackup")

        context.getDatabasePath(BackupConstants.DB_NAME).delete()
        File(context.getDatabasePath(BackupConstants.DB_NAME).absolutePath + "-wal").delete()
        File(context.getDatabasePath(BackupConstants.DB_NAME).absolutePath + "-shm").delete()
        File(context.getDatabasePath(BackupConstants.DB_NAME).absolutePath + "-journal").delete()

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
}
