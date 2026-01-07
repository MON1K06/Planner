package com.example.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            // ЧЕРНО-БЕЛАЯ ТЕМА
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

// Режимы экрана
enum class ScreenMode { LIST, CALENDAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: TaskViewModel = viewModel(factory = viewModelFactory)
    var currentMode by remember { mutableStateOf(ScreenMode.LIST) }

    // Состояния для диалогов
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedCategoryForTask by remember { mutableStateOf<Category?>(null) } // В какую категорию добавляем

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
                    // Кнопка переключения режима
                    IconButton(onClick = {
                        currentMode = if (currentMode == ScreenMode.LIST) ScreenMode.CALENDAR else ScreenMode.LIST
                    }) {
                        Icon(
                            if (currentMode == ScreenMode.LIST) Icons.Default.DateRange else Icons.Default.List,
                            contentDescription = "Switch View"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            // Кнопка добавления (только в режиме списка добавляем категории/задачи)
            if (currentMode == ScreenMode.LIST) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { showAddCategoryDialog = true },
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, "New Category")
                    }
                    FloatingActionButton(
                        onClick = {
                            selectedCategoryForTask = null // Общая задача
                            showAddTaskDialog = true
                        },
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, "New Task")
                    }
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

        // --- ДИАЛОГИ ---

        if (showAddCategoryDialog) {
            InputTextDialog(
                title = "Новый раздел",
                label = "Название",
                onDismiss = { showAddCategoryDialog = false },
                onConfirm = { name -> viewModel.addCategory(name) }
            )
        }

        if (showAddTaskDialog) {
            AddTaskFullDialog(
                initialCategory = selectedCategoryForTask,
                categories = viewModel.categories.collectAsState().value,
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { title, date, catId ->
                    viewModel.addTask(title, date, catId)
                }
            )
        }
    }
}

// --- ЭКРАН СПИСКА (КАТЕГОРИИ + ЗАДАЧИ) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListViewContent(viewModel: TaskViewModel, onAddTaskToCategory: (Category?) -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()

    // Состояние удаления
    var itemToDelete by remember { mutableStateOf<Any?>(null) } // Task или Category

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 1. Задачи без категории (Общие)
        val generalTasks = allTasks.filter { it.categoryId == null }
        if (generalTasks.isNotEmpty()) {
            stickyHeader { SectionHeader("Общие", null, onDelete = null, onAdd = null) }
            items(generalTasks) { task ->
                TaskItem(task,
                    onToggle = { viewModel.toggleTask(task) },
                    onDelete = { itemToDelete = task }
                )
            }
        }

        // 2. Категории и их задачи
        items(categories) { category ->
            val catTasks = allTasks.filter { it.categoryId == category.id }

            // Заголовок категории с кнопками
            SectionHeader(
                title = category.name,
                category = category,
                onDelete = { itemToDelete = category },
                onAdd = { onAddTaskToCategory(category) }
            )

            // Список задач внутри
            catTasks.forEach { task ->
                TaskItem(task,
                    onToggle = { viewModel.toggleTask(task) },
                    onDelete = { itemToDelete = task }
                )
            }
            if (catTasks.isEmpty()) {
                Text("Нет задач", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            }
        }
    }

    // Подтверждение удаления
    if (itemToDelete != null) {
        val title = if (itemToDelete is Category) "Удалить раздел?" else "Удалить задачу?"
        val msg = if (itemToDelete is Category) "Все задачи внутри раздела будут удалены." else "Вы уверены?"

        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(title) },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    onClick = {
                        if (itemToDelete is Category) viewModel.deleteCategory(itemToDelete as Category)
                        else viewModel.deleteTask(itemToDelete as Task)
                        itemToDelete = null
                    }
                ) { Text("Удалить", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Отмена", color = Color.Black) }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, category: Category?, onDelete: (() -> Unit)?, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add task to cat") }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete cat", tint = Color.Black) }
        }
    }
    HorizontalDivider(color = Color.Black, thickness = 2.dp)
}

// --- ЭКРАН КАЛЕНДАРЯ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewContent(viewModel: TaskViewModel) {
    val calendarState = rememberDatePickerState()
    val selectedDate = calendarState.selectedDateMillis ?: System.currentTimeMillis()

    // Получаем задачи для выбранной даты
    // Используем collectAsState с ключом (key), чтобы перезапрашивать при смене даты
    val tasksForDate by produceState(initialValue = emptyList(), key1 = selectedDate, key2 = viewModel.allTasks.collectAsState().value) {
        viewModel.getTasksForDate(selectedDate).collect { value = it }
    }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DatePicker(
            state = calendarState,
            colors = DatePickerDefaults.colors(
                todayContentColor = Color.Black,
                todayDateBorderColor = Color.Black,
                selectedDayContainerColor = Color.Black,
                selectedDayContentColor = Color.White
            )
        )

        Divider()

        Text(
            "Задачи на этот день:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(tasksForDate) { task ->
                TaskItem(task,
                    onToggle = { viewModel.toggleTask(task) },
                    onDelete = { taskToDelete = task }
                )
            }
        }

        if (taskToDelete != null) {
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                title = { Text("Удалить задачу?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        onClick = {
                            viewModel.deleteTask(taskToDelete!!)
                            taskToDelete = null
                        }
                    ) { Text("Да") }
                },
                dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Нет", color = Color.Black) } }
            )
        }
    }
}

// --- КОМПОНЕНТЫ ---

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Color.Black)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = task.title,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) Color.Gray else Color.Black
                )
                if (task.date != null) {
                    Text(
                        text = "📅 ${dateFormatter.format(Date(task.date))}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

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

    // Для DatePicker в диалоге
    var showDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = dateState.selectedDateMillis
                    showDatePicker = false
                }) { Text("ОК", color = Color.Black) }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Что сделать?") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        cursorColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Выбор даты
                Button(
                    onClick = { showDatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val dateStr = if (selectedDate != null)
                        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedDate!!))
                    else "Выбрать дату"
                    Text(text = "📅 $dateStr")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Выбор категории (простой список RadioButton)
                Text("Раздел:", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedCatId == null,
                        onClick = { selectedCatId = null },
                        colors = RadioButtonDefaults.colors(selectedColor = Color.Black)
                    )
                    Text("Общее")
                }
                categories.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedCatId == cat.id,
                            onClick = { selectedCatId = cat.id },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.Black)
                        )
                        Text(cat.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(text, selectedDate, selectedCatId)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) }
        }
    )
}

@Composable
fun InputTextDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    cursorColor = Color.Black
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } }
    )
}