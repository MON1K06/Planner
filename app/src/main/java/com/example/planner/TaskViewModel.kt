package com.example.planner

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskViewModel(application: Application, private val dao: TaskDao) : AndroidViewModel(application) {

    // --- Настройки для заголовка "Общие" ---
    private val prefs = application.getSharedPreferences("planner_prefs", Context.MODE_PRIVATE)
    private val _generalTitle = MutableStateFlow(prefs.getString("general_title", "Общие") ?: "Общие")
    val generalTitle = _generalTitle.asStateFlow()

    fun renameGeneralCategory(newName: String) {
        if (newName.isBlank()) return
        _generalTitle.value = newName
        prefs.edit().putString("general_title", newName).apply()
    }

    // --- Остальной код ---
    val categories: StateFlow<List<Category>> = dao.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedCategoryIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedCategoryIds = _expandedCategoryIds.asStateFlow()

    fun toggleCategoryExpand(categoryId: Int) {
        val current = _expandedCategoryIds.value
        if (current.contains(categoryId)) {
            _expandedCategoryIds.value = current - categoryId
        } else {
            _expandedCategoryIds.value = current + categoryId
        }
    }

    // --- Задачи ---
    fun addTask(title: String, date: Long?, categoryId: Int?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertTask(Task(title = title, date = date, categoryId = categoryId))
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { dao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    }

    fun renameTask(task: Task, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { dao.updateTask(task.copy(title = newTitle)) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.deleteTask(task) }
    }

    // --- Категории ---
    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.sortOrder } ?: 0
            dao.insertCategory(Category(name = name, sortOrder = maxOrder + 1))
        }
    }

    fun renameCategory(category: Category, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { dao.updateCategory(category.copy(name = newName)) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { dao.deleteCategory(category) }
    }

    fun clearCategoryTasks(categoryId: Int?) {
        viewModelScope.launch {
            // Если categoryId == null, удаляем задачи где categoryId IS NULL
            if (categoryId != null) {
                dao.deleteTasksByCategoryId(categoryId)
            } else {
                // Для "Общих" нам нужен отдельный SQL запрос или логика.
                // В Room сложно удалить IS NULL через простой @Query, поэтому сделаем хитро:
                // Мы не можем просто вызвать deleteTasksByCategoryId(null).
                // Удалим через перебор (безопасно) или добавим запрос в DAO.
                // Для простоты и надежности добавим запрос в DAO (см. ниже шаг 1.1)
                dao.deleteGeneralTasks()
            }
        }
    }

    fun updateCategoriesOrder(newOrderList: List<Category>) {
        viewModelScope.launch {
            val updatedList = newOrderList.mapIndexed { index, category ->
                category.copy(sortOrder = index)
            }
            dao.updateCategories(updatedList)
        }
    }

    fun getTasksForDate(date: Long): Flow<List<Task>> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis
        return dao.getTasksByDate(start, end)
    }
}