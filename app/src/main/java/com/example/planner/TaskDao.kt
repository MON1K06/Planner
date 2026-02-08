package com.example.planner

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // --- ЗАДАЧИ ---
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE date >= :startOfDay AND date < :endOfDay")
    fun getTasksByDate(startOfDay: Long, endOfDay: Long): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM tasks WHERE categoryId = :categoryId")
    suspend fun deleteTasksByCategoryId(categoryId: Int)

    @Query("DELETE FROM tasks WHERE categoryId IS NULL")
    suspend fun deleteGeneralTasks()

    // --- КАТЕГОРИИ ---
    // Сортируем по sortOrder, чтобы порядок сохранялся
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>) // <--- ДЛЯ СОХРАНЕНИЯ ПОРЯДКА

    @Delete
    suspend fun deleteCategory(category: Category)
}