package com.example.planner

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TimePicker
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
import androidx.compose.ui.platform.LocalContext
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

enum class ScreenMode { LIST, CALENDAR, WEEK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: TaskViewModel = viewModel(factory = viewModelFactory)
    var currentMode by remember { mutableStateOf(ScreenMode.LIST) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    // Переменные для инициализации диалога
    var initialCategoryForTask by remember { mutableStateOf<Category?>(null) }
    var initialDateForTask by remember { mutableStateOf<Long?>(null) }
    var initialIsWeekTask by remember { mutableStateOf(false) }

    val calendarState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when(currentMode) {
                        ScreenMode.LIST -> "Планы"
                        ScreenMode.CALENDAR -> "Календарь"
                        ScreenMode.WEEK -> "Неделя"
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                actions = {
                    IconButton(onClick = { currentMode = ScreenMode.WEEK }) {
                        Icon(Icons.Default.DateRange, "Week View", tint = if(currentMode == ScreenMode.WEEK) Color.Black else Color.Gray)
                    }
                    IconButton(onClick = { currentMode = ScreenMode.CALENDAR }) {
                        Icon(Icons.Default.CalendarMonth, "Calendar View", tint = if(currentMode == ScreenMode.CALENDAR) Color.Black else Color.Gray)
                    }
                    IconButton(onClick = { currentMode = ScreenMode.LIST }) {
                        Icon(Icons.Default.List, "List View", tint = if(currentMode == ScreenMode.LIST) Color.Black else Color.Gray)
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
            when (currentMode) {
                ScreenMode.LIST -> {
                    ListViewContent(viewModel, onAddTaskToCategory = { cat ->
                        initialCategoryForTask = cat
                        initialDateForTask = null
                        initialIsWeekTask = false
                        showAddTaskDialog = true
                    })
                }
                ScreenMode.CALENDAR -> {
                    CalendarViewContent(viewModel, calendarState, onAddTaskForDate = { date ->
                        initialCategoryForTask = null
                        initialDateForTask = date
                        initialIsWeekTask = false
                        showAddTaskDialog = true
                    })
                }
                ScreenMode.WEEK -> {
                    WeekViewContent(viewModel, onAddTask = { date, isWeek ->
                        initialCategoryForTask = null
                        initialDateForTask = date
                        initialIsWeekTask = isWeek
                        showAddTaskDialog = true
                    })
                }
            }
        }

        if (showAddCategoryDialog) {
            InputTextDialog("Новый раздел", "", "Название", { showAddCategoryDialog = false }) { viewModel.addCategory(it) }
        }
        if (showAddTaskDialog) {
            AddTaskFullDialog(
                initialCategory = initialCategoryForTask,
                initialDate = initialDateForTask,
                initialIsWeekTask = initialIsWeekTask,
                categories = viewModel.categories.collectAsState().value,
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { title, date, catId, isWeek ->
                    viewModel.addTask(title, date, catId, isWeek)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewContent(viewModel: TaskViewModel, onAddTask: (Long, Boolean) -> Unit) {
    val currentWeekStart by viewModel.currentWeekStart.collectAsState()
    val parity by viewModel.weekParity.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()

    var showParityDialog by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    val weekPickerState = rememberDatePickerState(initialSelectedDateMillis = currentWeekStart)

    // --- Логика определения текущей недели ---
    val realCurrentWeekStart = remember {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }
    val isCurrentWeek = currentWeekStart == realCurrentWeekStart

    // --- Форматирование заголовка ---
    val headerFormatter = SimpleDateFormat("dd.MM", Locale("ru"))
    // Используем LLLL для именительного падежа (Февраль), а не MMMM (февраля)
    val monthFormatter = SimpleDateFormat("LLLL", Locale("ru"))

    val weekEnd = currentWeekStart + 6 * 24 * 60 * 60 * 1000

    // Делаем первую букву месяца заглавной
    val monthName = monthFormatter.format(Date(currentWeekStart)).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    // Формируем строку с длинным тире
    val headerText = "$monthName, ${headerFormatter.format(Date(currentWeekStart))} – ${headerFormatter.format(Date(weekEnd))}"

    val daysOfWeek = remember(currentWeekStart) {
        (0..6).map { i ->
            val millis = currentWeekStart + i * 24 * 60 * 60 * 1000
            val name = when(i) {
                0 -> "Понедельник"; 1 -> "Вторник"; 2 -> "Среда"; 3 -> "Четверг";
                4 -> "Пятница"; 5 -> "Суббота"; else -> "Воскресенье"
            }
            Triple(name, millis, SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis)))
        }
    }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    // Вспомогательная функция для отрисовки одинаковых карточек
    @Composable
    fun WeekCardCommon(
        title: String,
        tasks: List<Task>,
        onAddClick: () -> Unit,
        isHighlight: Boolean = false
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isHighlight) Color(0xFFF9F9F9) else Color.White
            ),
            border = if (!isHighlight) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)) else null,
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(if (isHighlight) 2.dp else 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0), contentColor = Color.Black),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.size(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 16.sp, modifier = Modifier.offset(y = (-1).dp))
                    }
                }

                if (tasks.isEmpty() && isHighlight) {
                    Text("Нет задач", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }

                tasks.forEach { task ->
                    TaskItem(task, { viewModel.toggleTask(task) }, { taskToDelete = task }, { taskToEdit = task })
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Навигация (Header) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.changeWeek(-1) }) { Icon(Icons.Default.ArrowBack, "Prev") }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showWeekPicker = true }) {
                // Заголовок даты с выделением цветом, если неделя текущая
                Text(
                    text = headerText,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentWeek) Color(0xFF3F51B5) else Color.Black // Синий цвет для текущей
                )

                // Строка с четностью и пометкой "Текущая"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showParityDialog = true }) {
                    if (isCurrentWeek) {
                        Text("Текущая • ", fontSize = 14.sp, color = Color(0xFF3F51B5), fontWeight = FontWeight.Bold)
                    }
                    Text("Неделя ($parity)", fontSize = 14.sp, color = Color.Gray)
                    Icon(Icons.Default.Edit, "Edit Parity", modifier = Modifier.size(14.dp), tint = Color.Gray)
                }
            }

            IconButton(onClick = { viewModel.changeWeek(1) }) { Icon(Icons.Default.ArrowForward, "Next") }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // 1. Блок "На неделю"
            item {
                WeekCardCommon(
                    title = "На неделю",
                    tasks = allTasks.filter { it.isWeekTask && it.date == currentWeekStart },
                    onAddClick = { onAddTask(currentWeekStart, true) },
                    isHighlight = true
                )
            }

            // 2. Дни недели
            items(daysOfWeek) { (dayName, dateMillis, dateStr) ->
                WeekCardCommon(
                    title = "$dayName, $dateStr",
                    tasks = allTasks.filter { !it.isWeekTask && it.date != null && isSameDay(it.date, dateMillis) },
                    onAddClick = { onAddTask(dateMillis, false) },
                    isHighlight = false
                )
            }
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    // --- Диалоги ---
    if (showParityDialog) {
        val nextParity = if (parity == 1) 2 else 1
        AlertDialog(
            onDismissRequest = { showParityDialog = false },
            title = { Text("Изменить номер недели") },
            text = { Text("Сейчас неделя считается $parity-й. Сделать её $nextParity-й? Все будущие недели пересчитаются.") },
            confirmButton = {
                Button(onClick = { viewModel.setParityForCurrentWeek(nextParity); showParityDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { showParityDialog = false }) { Text("Отмена", color = Color.Black) } }
        )
    }

    if (showWeekPicker) {
        DatePickerDialog(
            onDismissRequest = { showWeekPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    weekPickerState.selectedDateMillis?.let { viewModel.setWeekToDate(it) }
                    showWeekPicker = false
                }) { Text("ОК", color = Color.Black) }
            }
        ) { DatePicker(state = weekPickerState) }
    }

    if (taskToDelete != null) {
        AlertDialog(onDismissRequest = { taskToDelete = null }, title = { Text("Удалить задачу?") }, confirmButton = { Button(onClick = { viewModel.deleteTask(taskToDelete!!); taskToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Да") } }, dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Нет", color = Color.Black) } })
    }
    if (taskToEdit != null) {
        InputTextDialog("Изменить", taskToEdit!!.title, "Текст", { taskToEdit = null }) { viewModel.renameTask(taskToEdit!!, it) }
    }
}fun isSameDay(date1: Long, date2: Long): Boolean {
    val c1 = Calendar.getInstance(); c1.timeInMillis = date1
    val c2 = Calendar.getInstance(); c2.timeInMillis = date2
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
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

    var categoryForOptions by remember { mutableStateOf<Category?>(null) }
    var showGeneralOptions by remember { mutableStateOf(false) }

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
        // --- Раздел Общие ---
        val generalTasks = allTasks.filter { it.categoryId == null }
        val isGeneralExpanded = expandedIds.contains(-1)

        item {
            CategoryCard(
                title = generalTitle,
                taskCount = generalTasks.size, // <--- Передаем количество задач
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
                    IconButton(onClick = { onAddTaskToCategory(null) }) { Icon(Icons.Default.Add, "Add") }
                    IconButton(onClick = { showGeneralOptions = true }) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Пользовательские категории ---
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
                    taskCount = catTasks.size, // <--- Передаем количество задач
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
                        IconButton(onClick = { onAddTaskToCategory(category) }) { Icon(Icons.Default.Add, "Add") }
                        IconButton(onClick = { categoryForOptions = category }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }
        }
    }

    // ... (код диалогов остался без изменений) ...
    // Вставьте сюда код диалогов (AlertDialog) из предыдущего ответа,
    // он такой же, как и был.

    if (categoryForOptions != null) {
        AlertDialog(
            onDismissRequest = { categoryForOptions = null },
            title = { Text("Раздел: ${categoryForOptions!!.name}") },
            confirmButton = { },
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { categoryToEdit = categoryForOptions; categoryForOptions = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Переименовать") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { categoryToClear = categoryForOptions; categoryForOptions = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Очистить задачи") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { categoryToDeleteConfirm = categoryForOptions; categoryForOptions = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Удалить раздел") }
                    TextButton(onClick = { categoryForOptions = null }) { Text("Отмена", color = Color.Black) }
                }
            }
        )
    }

    if (showGeneralOptions) {
        AlertDialog(
            onDismissRequest = { showGeneralOptions = false },
            title = { Text("Раздел: $generalTitle") },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { showEditGeneralTitle = true; showGeneralOptions = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Переименовать") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { isGeneralToClear = true; showGeneralOptions = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Очистить задачи") }
                    TextButton(onClick = { showGeneralOptions = false }) { Text("Отмена", color = Color.Black) }
                }
            }
        )
    }

    if (categoryToDeleteConfirm != null) {
        AlertDialog(onDismissRequest = { categoryToDeleteConfirm = null }, title = { Text("Удалить раздел?") }, text = { Text("Раздел \"${categoryToDeleteConfirm!!.name}\" и все задачи в нем будут безвозвратно удалены.") }, confirmButton = { Button(onClick = { viewModel.deleteCategory(categoryToDeleteConfirm!!); categoryToDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { categoryToDeleteConfirm = null }) { Text("Отмена", color = Color.Black) } })
    }
    if (categoryToClear != null) {
        AlertDialog(onDismissRequest = { categoryToClear = null }, title = { Text("Очистить задачи?") }, text = { Text("Все задачи в разделе \"${categoryToClear!!.name}\" будут удалены.") }, confirmButton = { Button(onClick = { viewModel.clearCategoryTasks(categoryToClear!!.id); categoryToClear = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Очистить") } }, dismissButton = { TextButton(onClick = { categoryToClear = null }) { Text("Отмена", color = Color.Black) } })
    }
    if (isGeneralToClear) {
        AlertDialog(onDismissRequest = { isGeneralToClear = false }, title = { Text("Очистить раздел?") }, text = { Text("Все задачи в разделе \"$generalTitle\" будут удалены.") }, confirmButton = { Button(onClick = { viewModel.clearCategoryTasks(null); isGeneralToClear = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Очистить") } }, dismissButton = { TextButton(onClick = { isGeneralToClear = false }) { Text("Отмена", color = Color.Black) } })
    }
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
    taskCount: Int, // <--- Новый параметр
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

                // Название категории
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )

                // Оранжевый кружок с количеством (если задач > 0)
                if (taskCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(24.dp) // Размер кружка
                            .background(Color(0xFFFF9800), androidx.compose.foundation.shape.CircleShape), // Оранжевый цвет
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = taskCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Распорка, чтобы сдвинуть кнопки вправо
                Spacer(modifier = Modifier.weight(1f))

                // Кнопки (+ и редактировать)
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
    // Меняем формат: Сначала время, потом дата в скобках
    val dateTimeFormatter = SimpleDateFormat("HH:mm (dd.MM)", Locale.getDefault())
    val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Color.Black))

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clickable { onEdit() }) {
                // Текст задачи
                Text(
                    task.title,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) Color.Gray else Color.Black
                )

                // Дата и время
                if (task.isWeekTask) {
                    // Убрали fontSize = 12.sp, чтобы размер был как у текста задачи
                    Text("📅 На неделю", color = Color.Gray)
                } else if (task.date != null) {
                    val c = Calendar.getInstance()
                    c.timeInMillis = task.date
                    // Проверка на наличие времени (не полночь)
                    val hasTime = !(c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0)

                    val dateStr = if (hasTime) dateTimeFormatter.format(Date(task.date)) else dateFormatter.format(Date(task.date))

                    // Убрали fontSize = 12.sp
                    Text("📅 $dateStr", color = Color.Gray)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Delete", tint = Color.LightGray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskFullDialog(
    initialCategory: Category?,
    initialDate: Long?,
    initialIsWeekTask: Boolean,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Int?, Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedHour by remember { mutableStateOf(if (initialDate != null) Calendar.getInstance().apply { timeInMillis = initialDate }.get(Calendar.HOUR_OF_DAY) else 0) }
    var selectedMinute by remember { mutableStateOf(if (initialDate != null) Calendar.getInstance().apply { timeInMillis = initialDate }.get(Calendar.MINUTE) else 0) }

    var selectedCatId by remember { mutableStateOf(initialCategory?.id) }
    var isWeekTask by remember { mutableStateOf(initialIsWeekTask) }

    var showDatePicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDate ?: System.currentTimeMillis())

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDate = dateState.selectedDateMillis; showDatePicker = false }) { Text("ОК", color = Color.Black) }
            }
        ) { DatePicker(state = dateState) }
    }

    val handleConfirm = {
        var finalDate: Long? = selectedDate
        if (finalDate != null && !isWeekTask) {
            val c = Calendar.getInstance()
            c.timeInMillis = finalDate!!
            c.set(Calendar.HOUR_OF_DAY, selectedHour)
            c.set(Calendar.MINUTE, selectedMinute)
            finalDate = c.timeInMillis
        }
        onConfirm(text, finalDate, selectedCatId, isWeekTask)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Новая задача")
                Button(
                    onClick = handleConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) { Text("Сохранить") }
            }
        },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Что сделать?") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(checked = isWeekTask, onCheckedChange = { isWeekTask = it }, colors = CheckboxDefaults.colors(checkedColor = Color.Black))
                    Text("На всю неделю")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        // ИЗМЕНЕНИЕ ЗДЕСЬ: "Выбрать дату" -> "Дата"
                        val dateLabel = if (isWeekTask) "Выбрать неделю" else "Дата"
                        val dateStr = if (selectedDate != null) SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedDate!!)) else dateLabel
                        Text("📅 $dateStr")
                    }

                    if (!isWeekTask) {
                        Button(
                            onClick = {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        selectedHour = hour
                                        selectedMinute = minute
                                    },
                                    selectedHour,
                                    selectedMinute,
                                    true
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                        ) {
                            val timeStr = String.format("%02d:%02d", selectedHour, selectedMinute)
                            Text("⏰ $timeStr")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Раздел:", fontWeight = FontWeight.Bold)

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
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } }
    )
}

@Composable
fun InputTextDialog(title: String, initialText: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, focusedLabelColor = Color.Black)) }, confirmButton = { Button(onClick = { onConfirm(text); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("ОК") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.Black) } })
}