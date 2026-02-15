package com.example.planner

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class TaskViewModel(application: Application, private val dao: TaskDao) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("planner_prefs", Context.MODE_PRIVATE)

    // --- Заголовки ---
    private val _generalTitle = MutableStateFlow(prefs.getString("general_title", "Общие") ?: "Общие")
    val generalTitle = _generalTitle.asStateFlow()

    fun renameGeneralCategory(newName: String) {
        if (newName.isBlank()) return
        _generalTitle.value = newName
        prefs.edit().putString("general_title", newName).apply()
    }

    // --- Основные данные ---
    val categories: StateFlow<List<Category>> = dao.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedCategoryIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedCategoryIds = _expandedCategoryIds.asStateFlow()

    // --- Логика Недели ---
    // Текущий понедельник выбранной недели
    private val _currentWeekStart = MutableStateFlow(getStartOfCurrentWeek())
    val currentWeekStart = _currentWeekStart.asStateFlow()

    // Четность недели (1 или 2). Вычисляется относительно "якорной" даты в настройках.
    val weekParity = _currentWeekStart.map { weekStart ->
        calculateParity(weekStart)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private fun getStartOfCurrentWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun changeWeek(weeksToAdd: Int) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _currentWeekStart.value
        calendar.add(Calendar.WEEK_OF_YEAR, weeksToAdd)
        _currentWeekStart.value = calendar.timeInMillis
    }

    fun setWeekToDate(date: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        _currentWeekStart.value = calendar.timeInMillis
    }

    // Логика расчета четности
    private fun calculateParity(weekStart: Long): Int {
        val anchorDate = prefs.getLong("parity_anchor_date", -1L)
        val anchorParity = prefs.getInt("parity_anchor_value", 1) // 1 - нечетная, 2 - четная

        if (anchorDate == -1L) {
            // Если не задано, сохраняем текущую неделю как "1"
            prefs.edit().putLong("parity_anchor_date", weekStart).putInt("parity_anchor_value", 1).apply()
            return 1
        }

        val diffMillis = weekStart - anchorDate
        val diffWeeks = TimeUnit.MILLISECONDS.toDays(diffMillis) / 7
        // Если anchor=1, diff=0 -> 1. diff=1 -> 2. diff=2 -> 1.
        // Формула: ((Anchor - 1) + diff) % 2 ... но надо учесть отрицательные diff
        val result = (anchorParity - 1 + diffWeeks) % 2
        // Приведение к диапазону 1..2 (с учетом отрицательного остатка в Java/Kotlin)
        return if (result.toInt() % 2 == 0) 1 else 2 // Упрощенно: если четное смещение - та же четность
        // Более точная логика:
        val steps = abs(diffWeeks).toInt()
        var current = anchorParity
        if (weekStart > anchorDate) {
            for(i in 0 until steps) current = if(current == 1) 2 else 1
        } else {
            for(i in 0 until steps) current = if(current == 1) 2 else 1
        }
        return current
    }

    fun setParityForCurrentWeek(newParity: Int) {
        // Устанавливаем текущую просматриваемую неделю как "якорь" с выбранной четностью
        prefs.edit()
            .putLong("parity_anchor_date", _currentWeekStart.value)
            .putInt("parity_anchor_value", newParity)
            .apply()
        // Триггерим обновление пересозданием потока или просто изменением стейта (map пересчитает)
        _currentWeekStart.value = _currentWeekStart.value
    }

    // --- CRUD ---
    fun toggleCategoryExpand(categoryId: Int) {
        val current = _expandedCategoryIds.value
        if (current.contains(categoryId)) {
            _expandedCategoryIds.value = current - categoryId
        } else {
            _expandedCategoryIds.value = current + categoryId
        }
    }

    fun addTask(title: String, date: Long?, categoryId: Int?, isWeekTask: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch {
            // Если задача "на неделю", сохраняем дату как Понедельник этой недели
            val finalDate = if (isWeekTask && date != null) {
                val c = Calendar.getInstance()
                c.timeInMillis = date
                c.firstDayOfWeek = Calendar.MONDAY
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                c.timeInMillis
            } else {
                date
            }
            dao.insertTask(Task(title = title, date = finalDate, categoryId = categoryId, isWeekTask = isWeekTask))
        }
    }

    fun toggleTask(task: Task) { viewModelScope.launch { dao.updateTask(task.copy(isCompleted = !task.isCompleted)) } }
    fun renameTask(task: Task, newTitle: String) { if (newTitle.isBlank()) return; viewModelScope.launch { dao.updateTask(task.copy(title = newTitle)) } }
    fun deleteTask(task: Task) { viewModelScope.launch { dao.deleteTask(task) } }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.sortOrder } ?: 0
            dao.insertCategory(Category(name = name, sortOrder = maxOrder + 1))
        }
    }
    fun renameCategory(category: Category, newName: String) { if (newName.isBlank()) return; viewModelScope.launch { dao.updateCategory(category.copy(name = newName)) } }
    fun deleteCategory(category: Category) { viewModelScope.launch { dao.deleteCategory(category) } }
    fun clearCategoryTasks(categoryId: Int?) {
        viewModelScope.launch {
            if (categoryId != null) dao.deleteTasksByCategoryId(categoryId) else dao.deleteGeneralTasks()
        }
    }
    fun updateCategoriesOrder(newOrderList: List<Category>) {
        viewModelScope.launch {
            val updatedList = newOrderList.mapIndexed { index, category -> category.copy(sortOrder = index) }
            dao.updateCategories(updatedList)
        }
    }

    fun getTasksForDate(date: Long): Flow<List<Task>> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis
        // Исключаем задачи "на неделю", показываем только те, что на конкретный день
        return dao.getTasksByDate(start, end).map { list -> list.filter { !it.isWeekTask } }
    }
}