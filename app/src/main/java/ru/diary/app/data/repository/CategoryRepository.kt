package ru.diary.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.diary.app.data.db.CategoryDao
import ru.diary.app.data.model.Category

class CategoryRepository(private val dao: CategoryDao) {

    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    suspend fun getAll(): List<Category> = dao.getAll()

    suspend fun add(category: Category): Long = dao.insert(category)

    suspend fun update(category: Category) = dao.update(category)

    suspend fun delete(category: Category) = dao.delete(category)
}
