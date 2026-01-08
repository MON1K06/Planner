package com.example.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TaskViewModel(database.taskDao()) as T
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color.Black,
                    onPrimary = Color.White,
                    secondary = Color.DarkGray,
                    background = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black,
                    primaryContainer = Color.White,
                    onPrimaryContainer = Color.Black
                )
            ) {
                MainScreen(viewModelFactory)
            }
        }
    }
}

enum class ScreenMode { LIST, CALENDAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: TaskViewModel = viewModel(factory = viewModelFactory)
    var currentMode by remember { mutableStateOf(ScreenMode.LIST) }

    // Состояния диалогов
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedCategoryForTask by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentMode == ScreenMode.LIST) "Планы" else "Календарь",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                actions = {
                    IconButton(onClick = {
                        currentMode = if (currentMode == ScreenMode.LIST) ScreenMode.CALENDAR else ScreenMode.LIST
                    }) {
                        Icon(if (currentMode == ScreenMode.LIST) Icons.Default.DateRange else Icons.Default.List, "View")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            if (currentMode == ScreenMode.LIST) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { showAddCategoryDialog = true },
                        containerColor = Color.Black, contentColor = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) { Icon(Icons.Default.CreateNewFolder, "New Category") } // Если нет иконки, замени на Icons.Default.Edit

                    FloatingActionButton(
                        onClick = {
                            selectedCategoryForTask = null
                            showAddTaskDialog = true
                        },
                        containerColor = Color.Black, contentColor = Color.White
                    ) { Icon(Icons.Default.Add, "New Task") }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            if (currentMode == ScreenMode.LIST) {
                ListViewContent(viewModel, onAddTaskToCategory = { cat ->
                    selectedCategoryForTask = cat
                    showAddTaskDialog = true
                })
            } else {
                CalendarViewContent(viewModel)
            }
        }

        if (showAddCategoryDialog) {
            InputTextDialog(
                title = "Новый раздел", initialText = "", label = "Название",
                onDismiss = { showAddCategoryDialog = false },
                onConfirm = { name -> viewModel.addCategory(name) }
            )
        }

        if (showAddTaskDialog) {
            AddTaskFullDialog(
                initialCategory = selectedCategoryForTask,
                categories = viewModel.categories.collectAsState().value,
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { title, date, catId -> viewModel.addTask(title, date, catId) }
            )
        }
    }
}

// --- СПИСОК (Сворачивание + Редактирование) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListViewContent(viewModel: TaskViewModel, onAddTaskToCategory: (Category?) -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()

    // Храним состояние свернутости по ID категории (по умолчанию все развернуты)
    // Если true - развернуто, false - свернуто
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    // Состояния для редактирования/удаления
    var itemToDelete by remember { mutableStateOf<Any?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 1. Общие задачи
        val generalTasks = allTasks.filter { it.categoryId == null }
        if (generalTasks.isNotEmpty()) {
            stickyHeader {
                // Общий раздел всегда развернут
                SectionHeader(
                    title = "Общие", category = null, isExpanded = true,
                    onToggleExpand = {}, onDelete = null, onEdit = null, onAdd = null
                )
            }
            items(generalTasks) { task ->
                TaskItem(task,
                    onToggle = { viewModel.toggleTask(task) },
                    onDelete = { itemToDelete = task },
                    onEdit = { taskToEdit = task }
                )
            }
        }

        // 2. Категории
        items(categories) { category ->
            val isExpanded = expandedStates[category.id] ?: true // По умолчанию true
            val catTasks = allTasks.filter { it.categoryId == category.id }

            // Заголовок
            SectionHeader(
                title = category.name,
                category = category,
                isExpanded = isExpanded,
                onToggleExpand = { expandedStates[category.id] = !isExpanded },
                onDelete = { itemToDelete = category },
                onEdit = { categoryToEdit = category },
                onAdd = { onAddTaskToCategory(category) }
            )

            // Задачи (показываем только если isExpanded)
            if (isExpanded) {
                catTasks.forEach { task ->
                    TaskItem(task,
                        onToggle = { viewModel.toggleTask(task) },
                        onDelete = { itemToDelete = task },
                        onEdit = { taskToEdit = task }
                    )
                }
                if (catTasks.isEmpty()) {
                    Text("Пусто", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
                }
            }
        }
    }

    // --- Диалоги ---

    // Удаление
    if (itemToDelete != null) {
        val isCat = itemToDelete is Category
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (isCat) "Удалить раздел?" else "Удалить задачу?") },
            text = { Text(if (isCat) "Все задачи внутри будут удалены." else "Вы уверены?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    onClick = {
                        if (isCat) viewModel.deleteCategory(itemToDelete as Category)
                        else viewModel.deleteTask(itemToDelete as Task)
                        itemToDelete = null
                    }
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Отмена", color = Color.Black) } }
        )
    }

    // Редактирование категории
    if (categoryToEdit != null) {
        InputTextDialog(
            title = "Переименовать", initialText = categoryToEdit!!.name, label = "Название",
            onDismiss = { categoryToEdit = null },
            onConfirm = { newName -> viewModel.renameCategory(categoryToEdit!!, newName) }
        )
    }

    // Редактирование задачи
    if (taskToEdit != null) {
        InputTextDialog(
            title = "Изменить задачу", initialText = taskToEdit!!.title, label = "Текст задачи",
            onDismiss = { taskToEdit = null },
            onConfirm = { newTitle -> viewModel.renameTask(taskToEdit!!, newTitle) }
        )
    }
}

// Заголовок раздела (кликабельный для сворачивания)
@Composable
fun SectionHeader(
    title: String,
    category: Category?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onAdd: (() -> Unit)?
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp)
            .clickable { onToggleExpand() }, // Нажатие на всю строку сворачивает/разворачивает
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Стрелочка
        if (category != null) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                modifier = Modifier.rotate(rotation)
            )
        }

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )

        // Кнопки действий
        if (onAdd != null) {
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add task") }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit Name") }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete cat", tint = Color.Black) }
        }
    }
    HorizontalDivider(color = Color.Black, thickness = 2.dp)
}

// Календарь (оставили почти как был)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewContent(viewModel: TaskViewModel) {
    val calendarState = rememberDatePickerState()
    val selectedDate = calendarState.selectedDateMillis ?: System.currentTimeMillis()
    val tasksForDate by produceState(initialValue = emptyList(), key1 = selectedDate, key2 = viewModel.allTasks.collectAsState().value) {
        viewModel.getTasksForDate(selectedDate).collect { value = it }
    }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DatePicker(
            state = calendarState,
            colors = DatePickerDefaults.colors(
                todayContentColor = Color.Black, todayDateBorderColor = Color.Black,
                selectedDayContainerColor = Color.Black, selectedDayContentColor = Color.White
            )
        )
        Divider()
        Text("Задачи на этот день:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(tasksForDate) { task ->
                TaskItem(task,
                    onToggle = { viewModel.toggleTask(task) },
                    onDelete = { taskToDelete = task },
                    onEdit = { taskToEdit = task }
                )
            }
        }
    }

    // Диалоги для календаря (дублируем логику, можно вынести в отдельную функцию)
    if (taskToDelete != null) { /* Код диалога удаления такой же */ }
    if (taskToEdit != null) {
        InputTextDialog(
            title = "Изменить", initialText = taskToEdit!!.title, label = "Текст",
            onDismiss = { taskToEdit = null },
            onConfirm = { newTitle -> viewModel.renameTask(taskToEdit!!, newTitle) }
        )
    }
}

// --- Компоненты ---

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Чекбокс - только завершение
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Color.Black)
            )

            // Текст - открывает редактирование
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable { onEdit() } // <--- Нажатие на текст открывает редактирование
            ) {
                Text(
                    text = task.title,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) Color.Gray else Color.Black
                )
                if (task.date != null) {
                    Text("📅 ${dateFormatter.format(Date(task.date))}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            // Кнопка удаления
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.LightGray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFullDialog(
    initialCategory: Category?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Int?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedCatId by remember { mutableStateOf(initialCategory?.id) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { selectedDate = dateState.selectedDateMillis; showDatePicker = false }) { Text("ОК", color = Color.Black) } }
        ) { DatePicker(state = dateState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Что сделать?") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val dateStr = if (selectedDate != null) SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedDate!!)) else "Выбрать дату"
                    Text("📅 $dateStr")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Раздел:", fontWeight = FontWeight.Bold)
                // Радио кнопки для категорий...
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedCatId == null, onClick = { selectedCatId = null }, colors = RadioButtonDefaults.colors(selectedColor = Color.Black))
                        Text("Общее")
                    }
                    categories.forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCatId == cat.id, onClick = { selectedCatId = cat.id }, colors = RadioButtonDefaults.colors(selectedColor = Color.Black))
                            Text(cat.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, selectedDate, selectedCatId); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } }
    )
}

@Composable
fun InputTextDialog(title: String, initialText: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } }
    )
}