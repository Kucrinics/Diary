package ru.diary.app.cleanup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.diary.app.data.db.AppDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CleanupConstants {
    private const val TAG = "CleanupManager"
    private const val THREE_YEARS_MILLIS = 3L * 365L * 24L * 60L * 60L * 1000L
    private val NOTE_DATE_FORMATS = arrayOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT),
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    )

    init {
        NOTE_DATE_FORMATS.forEach { it.timeZone = TimeZone.getTimeZone("UTC") }
    }
}

class CleanupManager(private val context: Context) {
    private val threeYearsAgo: Long
        get() {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.add(Calendar.YEAR, -3)
            return cal.timeInMillis
        }

    suspend fun cleanupOlderThan3Years(database: AppDatabase): Int = withContext(Dispatchers.IO) {
        val cutoff = threeYearsAgo
        var total = 0

        try {
            database.beginTransaction()
            // Delete tasks
            total += database.taskDao().deleteOlderThan(cutoff)
            // Delete notes
            total += database.noteDao().deleteNotesOlderThan(cutoff)
            database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.w("CleanupManager", "cleanup failed", e)
        } finally {
            try {
                database.endTransaction()
            } catch (_: Exception) {
            }
        }

        total
    }
}
