package ru.diary.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.diary.app.BuildConfig
import ru.diary.app.data.db.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date

class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        private const val PREFS_NAME = "diary_backup"
        private const val KEY_RESTORED = "backup_restored"
        private const val KEY_LAST_BACKUP = "last_backup_ms"
        private const val BACKUP_DIR = "Diary"
        private const val MAX_BACKUPS = 6
    }

    // ==================== RESTORE ====================

    fun restoreIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_RESTORED, false)) return

        if (databaseFile().exists()) {
            prefs.edit().putBoolean(KEY_RESTORED, true).apply()
            return
        }

        val restored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            restoreFromMediaStore()
        } else {
            restoreFromExternalFiles()
        }
        if (!restored) {
            Log.w(TAG, "Auto restore found no usable backup")
        }
        prefs.edit().putBoolean(KEY_RESTORED, restored).apply()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun restoreFromMediaStore(): Boolean {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("${BackupConstants.PREFIX}%${BackupConstants.SUFFIX}")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        val cursor = context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val uri = Uri.withAppendedPath(collection, id.toString())
                context.contentResolver.openInputStream(uri)?.use { input ->
                    return extractZip(input)
                }
            }
        }
        return false
    }

    private fun restoreFromExternalFiles(): Boolean {
        val backupsDir = backupsDir() ?: return false
        val backups = backupsDir.listFiles { f -> f.name.endsWith(BackupConstants.SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
        backups?.firstOrNull()?.let { file ->
            FileInputStream(file).use { input ->
                return extractZip(input)
            }
        }
        return false
    }

    /** Распаковывает ZIP в файл БД и файл настроек. Возвращает `true` при успехе. */
    private fun extractZip(input: java.io.InputStream): Boolean {
        val result = try {
            BackupArchive.read(input) { entryName -> targetOutputStream(entryName) }
        } catch (e: Exception) {
            Log.e(TAG, "Restore from backup failed", e)
            return false
        }
        if (!result.success) {
            Log.w(TAG, "Restore failed: ${result.error}")
        }
        return result.success
    }

    /** Приёмник для известных записей архива: база и настройки. Неизвестные — пропускаем. */
    private fun targetOutputStream(entryName: String): OutputStream? = when (entryName) {
        BackupConstants.DB_ENTRY -> {
            val dbFile = databaseFile()
            dbFile.parentFile?.mkdirs()
            FileOutputStream(dbFile)
        }
        BackupConstants.SETTINGS_ENTRY -> {
            val spDir = File(context.dataDir, "shared_prefs").apply { mkdirs() }
            FileOutputStream(File(spDir, BackupConstants.SETTINGS_ENTRY))
        }
        else -> null
    }

    // ==================== BACKUP ====================

    private fun databaseFile(): File = context.getDatabasePath(BackupConstants.DB_NAME)

    private fun backupsDir(): File? =
        context.getExternalFilesDir(null)?.let { File(it, "backups") }

    private fun settingsFile(): File = File(context.dataDir, "shared_prefs/${BackupConstants.SETTINGS_ENTRY}")

    private fun hasData(): Boolean {
        return try {
            val dbPath = databaseFile().absolutePath
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.use {
                val taskCount = it.rawQuery("SELECT COUNT(*) FROM tasks", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                val noteCount = it.rawQuery("SELECT COUNT(*) FROM notes", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                taskCount > 0 || noteCount > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query data counts", e)
            false
        }
    }

    private fun checkpoint(database: RoomDatabase?) {
        try {
            database?.openHelper?.writableDatabase
                ?.query("PRAGMA wal_checkpoint(TRUNCATE)")
                ?.use { it.moveToFirst() }
        } catch (e: Exception) {
            Log.w(TAG, "WAL checkpoint failed", e)
        }
    }

    /** Фоновое копирование при сворачивании — не чаще раза в [BackupScheduler.INTERVAL_MS]. */
    suspend fun backup(database: RoomDatabase) = performBackup(database, force = false)

    private suspend fun performBackup(database: RoomDatabase, force: Boolean): Boolean = withContext(Dispatchers.IO) {
        checkpoint(database)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val lastBackup = prefs.getLong(KEY_LAST_BACKUP, 0L)
        if (!force && !BackupScheduler.shouldBackup(lastBackup, now)) return@withContext false

        val dbFile = databaseFile()
        if (!dbFile.exists()) return@withContext false

        // Не бэкапить пустую БД — не обновляем timestamp.
        if (!hasData()) return@withContext false

        val fileName = BackupNaming.backupFileName(Date(now))
        if (!writeLocalBackup(fileName)) return@withContext false

        rotateBackups()
        prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
        true
    }

    /**
     * Собирает ZIP с текущей базой и настройками и кладёт его в локальное хранилище
     * (MediaStore на Android 10+ или external files ниже). Возвращает `true` при успехе.
     */
    private fun writeLocalBackup(fileName: String): Boolean {
        val dbFile = databaseFile()
        if (!dbFile.exists()) return false
        val spFile = settingsFile()
        val tempZip = File(context.cacheDir, fileName)

        return try {
            FileOutputStream(tempZip).use { fos ->
                FileInputStream(dbFile).use { dbIn ->
                    val settings = if (spFile.exists()) FileInputStream(spFile) else null
                    try {
                        if (!BackupArchive.write(
                                fos,
                                dbIn,
                                settings,
                                schemaVersion = AppDatabase.SCHEMA_VERSION,
                                appVersion = BuildConfig.VERSION_NAME,
                                createdAt = System.currentTimeMillis()
                            )
                        ) return false
                    } finally {
                        settings?.close()
                    }
                }
            }

            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(tempZip, fileName)
            } else {
                saveToExternalFiles(tempZip, fileName)
            }
            if (!saved) Log.w(TAG, "Local backup was not saved: $fileName")
            saved
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            false
        } finally {
            tempZip.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(file: File, fileName: String): Boolean {
        var uri: Uri? = null
        return try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
            ) ?: return false

            val out = context.contentResolver.openOutputStream(uri)
            if (out == null) {
                context.contentResolver.delete(uri, null, null)
                return false
            }
            out.use { os ->
                file.inputStream().use { inp -> inp.copyTo(os) }
                os.flush()
            }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Saving backup to MediaStore failed", e)
            uri?.let { failed -> runCatching { context.contentResolver.delete(failed, null, null) } }
            false
        }
    }

    private fun saveToExternalFiles(file: File, fileName: String): Boolean {
        val backupsDir = backupsDir() ?: return false
        backupsDir.mkdirs()
        return try {
            file.copyTo(File(backupsDir, fileName), overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Saving backup to external files failed", e)
            false
        }
    }

    private fun rotateBackups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rotateMediaStoreBackups()
        } else {
            rotateExternalFileBackups()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun rotateMediaStoreBackups() {
        try {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("${BackupConstants.PREFIX}%${BackupConstants.SUFFIX}")
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} ASC"

            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                collection, projection, selection, selectionArgs, sortOrder
            )?.use {
                while (it.moveToNext()) {
                    ids.add(it.getLong(0))
                }
            }
            while (ids.size > MAX_BACKUPS) {
                val id = ids.removeAt(0)
                context.contentResolver.delete(
                    Uri.withAppendedPath(collection, id.toString()), null, null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backup rotation failed", e)
        }
    }

    private fun rotateExternalFileBackups() {
        try {
            val backupsDir = backupsDir() ?: return
            if (!backupsDir.exists()) return
            val backups = backupsDir.listFiles { f -> f.name.endsWith(BackupConstants.SUFFIX) }
                ?.map { it.name to it.lastModified() }.orEmpty()
            val toDelete = BackupRetention.oldestToDelete(backups, MAX_BACKUPS)
            toDelete.forEach { File(backupsDir, it).delete() }
        } catch (_: Exception) { }
    }

}