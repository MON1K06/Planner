package com.example.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
                return TaskViewModel(this@MainActivity.application, database.taskDao()) as T
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
                    onSurface = Color.Black
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
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var initialCategoryForTask by remember { mutableStateOf<Category?>(null) }
    var initialDateForTask by remember { mutableStateOf<Long?>(null) }
    val calendarState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (currentMode == ScreenMode.LIST) "Планы" else "Календарь", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                actions = {
                    IconButton(onClick = { currentMode = if (currentMode == ScreenMode.LIST) ScreenMode.CALENDAR else ScreenMode.LIST }) {
                        Icon(if (currentMode == ScreenMode.LIST) Icons.Default.DateRange else Icons.Default.List, "View")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            if (currentMode == ScreenMode.LIST) {
                FloatingActionButton(onClick = { showAddCategoryDialog = true }, containerColor = Color.Black, contentColor = Color.White) {
                    Icon(Icons.Default.CreateNewFolder, "New Category")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            if (currentMode == ScreenMode.LIST) {
                ListViewContent(viewModel, onAddTaskToCategory = { cat ->
                    initialCategoryForTask = cat
                    initialDateForTask = null
                    showAddTaskDialog = true
                })
            } else {
                CalendarViewContent(viewModel, calendarState, onAddTaskForDate = { date ->
                    initialCategoryForTask = null
                    initialDateForTask = date
                    showAddTaskDialog = true
                })
            }
        }

        if (showAddCategoryDialog) {
            InputTextDialog("Новый раздел", "", "Название", { showAddCategoryDialog = false }) { viewModel.addCategory(it) }
        }
        if (showAddTaskDialog) {
            AddTaskFullDialog(initialCategoryForTask, initialDateForTask, viewModel.categories.collectAsState().value, { showAddTaskDialog = false }) { title, date, catId -> viewModel.addTask(title, date, catId) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListViewContent(viewModel: TaskViewModel, onAddTaskToCategory: (Category?) -> Unit) {
    val dbCategories by viewModel.categories.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val expandedIds by viewModel.expandedCategoryIds.collectAsState()
    val generalTitle by viewModel.generalTitle.collectAsState()

    val density = androidx.compose.ui.platform.LocalDensity.current
    val localCategories = remember { mutableStateListOf<Category>() }

    LaunchedEffect(dbCategories) {
        if (localCategories.size != dbCategories.size || !localCategories.map { it.id }.containsAll(dbCategories.map { it.id })) {
            localCategories.clear()
            localCategories.addAll(dbCategories)
        }
    }

    // Состояния для меню и редактирования
    var categoryForOptions by remember { mutableStateOf<Category?>(null) }
    var showGeneralOptions by remember { mutableStateOf(false) }

    // Новые состояния для подтверждения удаления/очистки
    var categoryToClear by remember { mutableStateOf<Category?>(null) }
    var categoryToDeleteConfirm by remember { mutableStateOf<Category?>(null) }
    var isGeneralToClear by remember { mutableStateOf(false) }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var showEditGeneralTitle by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }

    val lazyListState = rememberLazyListState()

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // --- 1. Раздел "Общие" ---
        val generalTasks = allTasks.filter { it.categoryId == null }
        val isGeneralExpanded = expandedIds.contains(-1)

        stickyHeader {
            CategoryCard(
                title = generalTitle,
                isExpanded = isGeneralExpanded,
                onToggleExpand = { viewModel.toggleCategoryExpand(-1) },
                isDragging = false,
                content = {
                    if (isGeneralExpanded) {
                        if (generalTasks.isNotEmpty()) {
                            Column {
                                generalTasks.forEach { task ->
                                    TaskItem(task, { viewModel.toggleTask(task) }, { taskToDelete = task }, { taskToEdit = task })
                                }
                            }
                        } else {
                            Text("Пусто", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        }
                    }
                },
                headerContent = {
                    IconButton(onClick = { showGeneralOptions = true }) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onAddTaskToCategory(null) }) { Icon(Icons.Default.Add, "Add") }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- 2. Перетаскиваемые категории ---
        itemsIndexed(localCategories, key = { _, cat -> cat.id }) { index, category ->
            val isExpanded = expandedIds.contains(category.id)
            val catTasks = allTasks.filter { it.categoryId == category.id }
            val isDragging = index == draggingItemIndex

            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) draggingItemOffset else 0f }
                    .pointerInput(category.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItemIndex = index
                                draggingItemOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingItemOffset += dragAmount.y
                                val itemHeightPx = with(density) { 60.dp.toPx() }
                                val offsetSteps = (draggingItemOffset / itemHeightPx).toInt()
                                val currentIdx = draggingItemIndex ?: return@detectDragGesturesAfterLongPress
                                val targetIdx = (currentIdx + offsetSteps).coerceIn(0, localCategories.lastIndex)
                                if (currentIdx != targetIdx) {
                                    localCategories.add(targetIdx, localCategories.removeAt(currentIdx))
                                    draggingItemIndex = targetIdx
                                    draggingItemOffset = 0f
                                }
                            },
                            onDragEnd = {
                                viewModel.updateCategoriesOrder(localCategories.toList())
                                draggingItemIndex = null
                                draggingItemOffset = 0f
                            },
                            onDragCancel = { draggingItemIndex = null; draggingItemOffset = 0f }
                        )
                    }
            ) {
                CategoryCard(
                    title = category.name,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleCategoryExpand(category.id) },
                    isDragging = isDragging,
                    content = {
                        if (isExpanded && !isDragging) {
                            Column {
                                catTasks.forEach { task ->
                                    TaskItem(task, { viewModel.toggleTask(task) }, { taskToDelete = task }, { taskToEdit = task })
                                }
                                if (catTasks.isEmpty()) {
                                    Text("Пусто", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                                }
                            }
                        }
                    },
                    headerContent = {
                        IconButton(onClick = { categoryForOptions = category }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onAddTaskToCategory(category) }) { Icon(Icons.Default.Add, "Add") }
                    }
                )
            }
        }
    }

    // --- 1. Меню действий для обычных разделов ---
    if (categoryForOptions != null) {
        AlertDialog(
            onDismissRequest = { categoryForOptions = null },
            title = { Text("Раздел: ${categoryForOptions!!.name}") },
            confirmButton = { },
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { categoryToEdit = categoryForOptions; categoryForOptions = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) { Text("Переименовать") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        // Вместо мгновенного удаления открываем диалог подтверждения
                        onClick = { categoryToClear = categoryForOptions; categoryForOptions = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) { Text("Очистить задачи") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        // Вместо мгновенного удаления открываем диалог подтверждения
                        onClick = { categoryToDeleteConfirm = categoryForOptions; categoryForOptions = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Удалить раздел") }
                    TextButton(onClick = { categoryForOptions = null }) { Text("Отмена", color = Color.Black) }
                }
            }
        )
    }

    // --- 2. Меню действий для раздела "Общие" ---
    if (showGeneralOptions) {
        AlertDialog(
            onDismissRequest = { showGeneralOptions = false },
            title = { Text("Раздел: $generalTitle") },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { showEditGeneralTitle = true; showGeneralOptions = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) { Text("Переименовать") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { isGeneralToClear = true; showGeneralOptions = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) { Text("Очистить задачи") }
                    TextButton(onClick = { showGeneralOptions = false }) { Text("Отмена", color = Color.Black) }
                }
            }
        )
    }

    // --- 3. ДИАЛОГИ ПОДТВЕРЖДЕНИЯ ---

    // Подтверждение удаления раздела
    if (categoryToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { categoryToDeleteConfirm = null },
            title = { Text("Удалить раздел?") },
            text = { Text("Раздел \"${categoryToDeleteConfirm!!.name}\" и все задачи в нем будут безвозвратно удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(categoryToDeleteConfirm!!)
                        categoryToDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteConfirm = null }) { Text("Отмена", color = Color.Black) }
            }
        )
    }

    // Подтверждение очистки задач в обычном разделе
    if (categoryToClear != null) {
        AlertDialog(
            onDismissRequest = { categoryToClear = null },
            title = { Text("Очистить задачи?") },
            text = { Text("Все задачи в разделе \"${categoryToClear!!.name}\" будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCategoryTasks(categoryToClear!!.id)
                        categoryToClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { categoryToClear = null }) { Text("Отмена", color = Color.Black) }
            }
        )
    }

    // Подтверждение очистки задач в Общих
    if (isGeneralToClear) {
        AlertDialog(
            onDismissRequest = { isGeneralToClear = false },
            title = { Text("Очистить раздел?") },
            text = { Text("Все задачи в разделе \"$generalTitle\" будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCategoryTasks(null)
                        isGeneralToClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { isGeneralToClear = false }) { Text("Отмена", color = Color.Black) }
            }
        )
    }

    // --- 4. Остальные диалоги (редактирование текста и удаление одной задачи) ---
    if (taskToDelete != null) {
        AlertDialog(onDismissRequest = { taskToDelete = null }, title = { Text("Удалить задачу?") }, confirmButton = { Button(onClick = { viewModel.deleteTask(taskToDelete!!); taskToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Да") } }, dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Нет", color = Color.Black) } })
    }
    if (categoryToEdit != null) {
        InputTextDialog("Переименовать", categoryToEdit!!.name, "Название", { categoryToEdit = null }) { viewModel.renameCategory(categoryToEdit!!, it) }
    }
    if (showEditGeneralTitle) {
        InputTextDialog("Переименовать раздел", generalTitle, "Название", { showEditGeneralTitle = false }) { viewModel.renameGeneralCategory(it) }
    }
    if (taskToEdit != null) {
        InputTextDialog("Изменить задачу", taskToEdit!!.title, "Текст", { taskToEdit = null }) { viewModel.renameTask(taskToEdit!!, it) }
    }
}


@Composable
fun CategoryCard(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isDragging: Boolean,
    content: @Composable () -> Unit,
    headerContent: @Composable RowScope.() -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow")

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = if (isDragging) CardDefaults.cardElevation(8.dp) else CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Expand", modifier = Modifier.rotate(rotation))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                headerContent()
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewContent(viewModel: TaskViewModel, calendarState: DatePickerState, onAddTaskForDate: (Long) -> Unit) {
    val selectedDate = calendarState.selectedDateMillis ?: System.currentTimeMillis()
    val tasksForDate by produceState(initialValue = emptyList(), key1 = selectedDate, key2 = viewModel.allTasks.collectAsState().value) {
        viewModel.getTasksForDate(selectedDate).collect { value = it }
    }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    val dateFormatter = SimpleDateFormat("dd MMMM", Locale("ru"))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                DatePicker(state = calendarState, colors = DatePickerDefaults.colors(todayContentColor = Color.Black, todayDateBorderColor = Color.Black, selectedDayContainerColor = Color.Black, selectedDayContentColor = Color.White))
                Button(onClick = { onAddTaskForDate(selectedDate) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.small) { Text("Добавить задачу на ${dateFormatter.format(Date(selectedDate))}") }
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }
        }
        items(tasksForDate) { task -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { TaskItem(task, { viewModel.toggleTask(task) }, { taskToDelete = task }, { taskToEdit = task }) } }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (taskToDelete != null) { AlertDialog(onDismissRequest = { taskToDelete = null }, title = { Text("Удалить задачу?") }, confirmButton = { Button(onClick = { viewModel.deleteTask(taskToDelete!!); taskToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Да") } }, dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Нет", color = Color.Black) } }) }
    if (taskToEdit != null) { InputTextDialog("Изменить", taskToEdit!!.title, "Текст", { taskToEdit = null }) { viewModel.renameTask(taskToEdit!!, it) } }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())
    Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Color.Black))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clickable { onEdit() }) {
                Text(task.title, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null, color = if (task.isCompleted) Color.Gray else Color.Black)
                if (task.date != null) Text("📅 ${dateFormatter.format(Date(task.date))}", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Delete", tint = Color.LightGray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFullDialog(initialCategory: Category?, initialDate: Long?, categories: List<Category>, onDismiss: () -> Unit, onConfirm: (String, Long?, Int?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedCatId by remember { mutableStateOf(initialCategory?.id) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDate ?: System.currentTimeMillis())

    if (showDatePicker) { DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { selectedDate = dateState.selectedDateMillis; showDatePicker = false }) { Text("ОК", color = Color.Black) } }) { DatePicker(state = dateState) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Новая задача"); Button(onClick = { onConfirm(text, selectedDate, selectedCatId); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Сохранить") } } },
        text = { Column { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Что сделать?") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showDatePicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray), modifier = Modifier.fillMaxWidth()) { val dateStr = if (selectedDate != null) SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedDate!!)) else "Выбрать дату"; Text("📅 $dateStr") }
            Spacer(modifier = Modifier.height(8.dp)); Text("Раздел:", fontWeight = FontWeight.Bold)
            Column { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedCatId == null, onClick = { selectedCatId = null }, colors = RadioButtonDefaults.colors(selectedColor = Color.Black)); Text("Общее") }
                categories.forEach { cat -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedCatId == cat.id, onClick = { selectedCatId = cat.id }, colors = RadioButtonDefaults.colors(selectedColor = Color.Black)); Text(cat.name) } } } } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } }
    )
}

@Composable
fun InputTextDialog(title: String, initialText: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black)) }, confirmButton = { Button(onClick = { onConfirm(text); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("ОК") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } })
}