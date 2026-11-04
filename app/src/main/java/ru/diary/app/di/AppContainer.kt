package ru.diary.app.di

import android.content.Context
import ru.diary.app.cleanup.CleanupManager
import ru.diary.app.backup.BackupManager
import ru.diary.app.data.db.AppDatabase
import ru.diary.app.data.repository.AttachmentRepository
import ru.diary.app.data.repository.CategoryRepository
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.data.repository.TaskRepository
import ru.diary.app.data.settings.SettingsManager
import ru.diary.app.notifications.AlarmScheduler

/**
 * Ручной контейнер зависимостей.
 * Без Hilt/Dagger — проще для новичка, и для приложения такого размера хватает.
 *
 * Создаётся один раз в DiaryApp и доступен через Android Service Locator.
 */
class AppContainer(context: Context, val backupManager: BackupManager) {

    val database: AppDatabase = AppDatabase.get(context)

    val taskRepository: TaskRepository = TaskRepository(database.taskDao())
    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao())
    val noteRepository: NoteRepository = NoteRepository(database.noteDao())
    val attachmentRepository: AttachmentRepository = AttachmentRepository(database.attachmentDao())
    val alarmScheduler: AlarmScheduler = AlarmScheduler(context)
    val settingsManager: SettingsManager = SettingsManager(context)
    val cleanupManager: CleanupManager = CleanupManager(context)
}
