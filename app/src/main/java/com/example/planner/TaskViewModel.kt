package com.example.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    // Списки данных
    val categories: StateFlow<List<Category>> = dao.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Действия с Задачами ---
    fun addTask(title: String, date: Long?, categoryId: Int?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertTask(Task(title = title, date = date, categoryId = categoryId))
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { dao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.deleteTask(task) }
    }

    // --- Действия с Категориями ---
    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { dao.insertCategory(Category(name = name)) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { dao.deleteCategory(category) }
    }

    // Хелпер для получения задач конкретной даты (для календаря)
    fun getTasksForDate(date: Long): Flow<List<Task>> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        // Обнуляем время до начала дня
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        // Конец дня
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis

        return dao.getTasksByDate(start, end)
    }
}