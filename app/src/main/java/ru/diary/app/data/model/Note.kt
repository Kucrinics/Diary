package ru.diary.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "date")
    val date: String,               // yyyy-MM-dd (creation)

    @ColumnInfo(name = "updated_at")
    val updatedAt: String = date,   // yyyy-MM-dd HH:mm (last modified)

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "is_important")
    val isImportant: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
