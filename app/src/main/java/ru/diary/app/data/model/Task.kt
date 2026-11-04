package ru.diary.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Задача. Дата хранится как ISO "yyyy-MM-dd", время как "HH:mm" или null.
 *
 * Повторяющиеся задачи хранятся как «шаблон» (repeatType != NONE, repeatOriginId == null).
 * Порождённые вхождения повторения материализуются в отдельные строки с
 * repeatOriginId = id шаблона, чтобы каждое вхождение можно было независимо
 * отметить выполненным, перенести или удалить.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "date")
    val date: String,               // yyyy-MM-dd

    @ColumnInfo(name = "time")
    val time: String? = null,       // HH:mm

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    /** Цветовое кодирование задачи (ARGB). null — без цвета. */
    @ColumnInfo(name = "color")
    val color: Int? = null,

    @ColumnInfo(name = "remind")
    val remind: Boolean = false,

    @ColumnInfo(name = "is_important")
    val isImportant: Boolean = false,

    @ColumnInfo(name = "is_draft")
    val isDraft: Boolean = false,

    @ColumnInfo(name = "auto_move")
    val autoMove: Boolean = true,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "repeat_type")
    val repeatType: String = RepeatType.NONE.value,

    @ColumnInfo(name = "repeat_interval_days")
    val repeatIntervalDays: Int? = null,

    @ColumnInfo(name = "repeat_days_of_week")
    val repeatDaysOfWeek: Int? = null,

    @ColumnInfo(name = "repeat_end_date")
    val repeatEndDate: String? = null,

    @ColumnInfo(name = "repeat_origin_id")
    val repeatOriginId: Long? = null
)