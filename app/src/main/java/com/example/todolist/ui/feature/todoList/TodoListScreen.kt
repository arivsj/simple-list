package com.example.todolist.ui.feature.todoList

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todolist.R
import com.example.todolist.models.TodoGroup
import com.example.todolist.stats.DashboardScreen
import com.example.todolist.models.TodoItem
import com.example.todolist.viewModel.TodoViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private sealed class UiItem {
    data class GroupItem(val group: TodoGroup) : UiItem()
    data class TaskItem(val item: TodoItem) : UiItem()
    data object GroupHeader : UiItem()
    data object TaskHeader : UiItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(viewModel: TodoViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TodoItem?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<TodoGroup?>(null) }
    var showCreateChoice by remember { mutableStateOf(false) }
    var showCelebrationDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<TodoGroup?>(null) }
    var taskToDelete by remember { mutableStateOf<TodoItem?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var draggedItemType by remember { mutableStateOf<Class<*>?>(null) }
    var showDashboard by remember { mutableStateOf(false) }

    val mergedItems: List<UiItem> = remember(uiState.groups, uiState.standaloneItems, uiState.selectedGroup, uiState.groupItems) {
        if (uiState.selectedGroup != null) {
            uiState.groupItems.map { UiItem.TaskItem(it) }
        } else {
            buildList {
                if (uiState.groups.isNotEmpty()) {
                    add(UiItem.GroupHeader)
                    addAll(uiState.groups.map { UiItem.GroupItem(it) })
                }
                if (uiState.standaloneItems.isNotEmpty()) {
                    add(UiItem.TaskHeader)
                    addAll(uiState.standaloneItems.map { UiItem.TaskItem(it) })
                }
            }
        }
    }

    var newItemIds by remember { mutableStateOf(setOf<Long>()) }
    val highlightItemId by viewModel.highlightItemId.collectAsState()
    LaunchedEffect(highlightItemId) {
        highlightItemId?.let { id ->
            newItemIds = newItemIds + id
            var targetIndex = -1
            repeat(20) {
                targetIndex = mergedItems.indexOfFirst { item ->
                    when (item) {
                        is UiItem.TaskItem -> item.item.id == id
                        else -> false
                    }
                }
                if (targetIndex >= 0) return@repeat
                delay(100)
            }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
            delay(2500)
            newItemIds = newItemIds - id
            viewModel.clearHighlightItemId()
        }
    }

    val toastMessage by viewModel.toastMessage.collectAsState()
    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    val fabSize = 34.dp
    val infiniteTransition = rememberInfiniteTransition()
    val rainbowRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart)
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val rainbowColors = listOf(
        Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
        Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF4B0082), Color(0xFF8B00FF)
    )

    LaunchedEffect(uiState.showCelebration) {
        if (uiState.showCelebration) {
            showCelebrationDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.selectedGroup != null) uiState.selectedGroup!!.title else stringResource(R.string.app_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                },
                navigationIcon = {
                    if (uiState.selectedGroup != null) {
                        IconButton(onClick = { viewModel.clearSelectedGroup() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                },
                actions = {
                    if (uiState.selectedGroup != null) {
                        IconButton(onClick = { groupToEdit = uiState.selectedGroup }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar categoria")
                        }
                    } else {
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_dashboard)) },
                                    onClick = { showMenu = false; showDashboard = true }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(fabSize + 4.dp)
                    .drawBehind {
                        val strokeWidth = 3.dp.toPx()
                        rotate(rainbowRotation, pivot = center) {
                            drawCircle(
                                brush = Brush.sweepGradient(rainbowColors, center = center),
                                radius = fabSize.toPx() / 2,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.selectedGroup != null) showAddDialog = true
                        else showCreateChoice = true
                    },
                    modifier = Modifier.size(fabSize),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.fab_add),
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
            ) {
                if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (mergedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.selectedGroup != null) stringResource(R.string.empty_group_tasks) else stringResource(R.string.empty_no_items),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(mergedItems) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { item ->
                                            offset.y.toInt() in item.offset..(item.offset + item.size)
                                        }
                                        ?.also {
                                            if (it.index in mergedItems.indices) {
                                                draggedItemType = mergedItems[it.index]::class.java
                                                draggedItemIndex = it.index
                                            }
                                        }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggingOffset += dragAmount.y

                                    val currentIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                                    val type = draggedItemType ?: return@detectDragGesturesAfterLongPress
                                    val draggedInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == currentIndex } ?: return@detectDragGesturesAfterLongPress

                                    val targetItem = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info ->
                                            if (info.index !in mergedItems.indices) return@firstOrNull false
                                            if (mergedItems[info.index]::class.java != type) return@firstOrNull false
                                            if (info.index == currentIndex) return@firstOrNull false
                                            val itemTop = info.offset
                                            val itemBottom = info.offset + info.size
                                            val dragPosition = draggedInfo.offset + draggingOffset + (draggedInfo.size / 2)
                                            dragPosition > itemTop && dragPosition < itemBottom
                                        }

                                    if (dragJob?.isActive != true) {
                                        val viewportHeight = listState.layoutInfo.viewportEndOffset
                                        val dragY = draggedInfo.offset + draggingOffset
                                        val scrollSpeed = 200f

                                        if (dragY < 100f) {
                                            dragJob = coroutineScope.launch {
                                                listState.animateScrollBy(-scrollSpeed)
                                            }
                                        } else if (dragY > viewportHeight - 100f) {
                                            dragJob = coroutineScope.launch {
                                                listState.animateScrollBy(scrollSpeed)
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val savedType = draggedItemType
                                    val startIndex = draggedItemIndex
                                    val dragOffset = draggingOffset
                                    draggedItemIndex = null
                                    draggingOffset = 0f
                                    draggedItemType = null

                                    if (startIndex != null && savedType != null && dragOffset != 0f) {
                                        val draggedInfo = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == startIndex }
                                        if (draggedInfo != null) {
                                            val dragPosition = draggedInfo.offset + dragOffset + (draggedInfo.size / 2)
                                            val targetItem = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { info ->
                                                    if (info.index !in mergedItems.indices) return@firstOrNull false
                                                    if (mergedItems[info.index]::class.java != savedType) return@firstOrNull false
                                                    if (info.index == startIndex) return@firstOrNull false
                                                    dragPosition > info.offset && dragPosition < info.offset + info.size
                                                }
                                            if (targetItem != null) {
                                                val targetIndexInType = mergedItems.withIndex()
                                                    .filter { it.value::class.java == savedType }
                                                    .indexOfFirst { it.index == targetItem.index }
                                                val currentIndexInType = mergedItems.withIndex()
                                                    .filter { it.value::class.java == savedType }
                                                    .indexOfFirst { it.index == startIndex }
                                                if (targetIndexInType >= 0 && currentIndexInType >= 0) {
                                                    when (savedType) {
                                                        UiItem.GroupItem::class.java ->
                                                            viewModel.moveGroup(currentIndexInType, targetIndexInType)
                                                        UiItem.TaskItem::class.java ->
                                                            viewModel.moveTodo(currentIndexInType, targetIndexInType)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    when (savedType) {
                                        UiItem.GroupItem::class.java -> viewModel.saveGroupsOrder()
                                        UiItem.TaskItem::class.java -> viewModel.saveOrder()
                                    }
                                },
                                onDragCancel = {
                                    draggedItemIndex = null
                                    draggingOffset = 0f
                                    draggedItemType = null
                                }
                            )
                        }
                ) {
                    itemsIndexed(mergedItems, key = { _, item ->
                        when (item) {
                            is UiItem.GroupItem -> "group_${item.group.id}"
                            is UiItem.TaskItem -> "task_${item.item.id}"
                            UiItem.GroupHeader -> "group_header"
                            UiItem.TaskHeader -> "task_header"
                        }
                    }) { index, item ->
                        val isDragging = index == draggedItemIndex
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp, label = "elevation")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isDragging) draggingOffset else 0f
                                    scaleX = if (isDragging) 1.05f else 1f
                                    scaleY = if (isDragging) 1.05f else 1f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                        ) {
                            when (item) {
                                is UiItem.GroupItem -> {
                                    val allItems by viewModel.allItems.collectAsState()
                                    val groupItems = allItems.filter { it.groupId == item.group.id }
                                    val cal = Calendar.getInstance()
                                    GroupItemRow(
                                        group = item.group,
                                        totalTasks = groupItems.size,
                                        doneTasks = groupItems.count { i ->
                                            i.isDone || (i.repeatType != null && i.lastCompleted?.let { last ->
                                                val lastCal = Calendar.getInstance().apply { timeInMillis = last }
                                                cal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
                                            } == true)
                                        },
                                        elevation = elevation,
                                        onClick = { viewModel.selectGroup(item.group) },
                                        onToggle = { viewModel.toggleGroup(item.group) },
                                        onEdit = { groupToEdit = item.group },
                                        onDelete = { groupToDelete = item.group }
                                    )
                                }
                                is UiItem.TaskItem -> {
                                    TodoItemRow(
                                        item = item.item,
                                        isNew = item.item.id in newItemIds,
                                        elevation = elevation,
                                        onToggle = { viewModel.toggleTodo(item.item) },
                                        onDelete = { taskToDelete = item.item },
                                        onEdit = { itemToEdit = item.item }
                                    )
                                }
                                UiItem.GroupHeader -> {
                                    Text(
                                        text = stringResource(R.string.section_categories),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                UiItem.TaskHeader -> {
                                    Text(
                                        text = stringResource(R.string.section_tasks),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            if (uiState.selectedGroup != null && uiState.groupItems.isNotEmpty()) {
                val cal = Calendar.getInstance()
                val total = uiState.groupItems.size
                val done = uiState.groupItems.count { i ->
                    i.isDone || (i.repeatType != null && i.lastCompleted?.let { last ->
                        val lastCal = Calendar.getInstance().apply { timeInMillis = last }
                        cal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
                    } == true)
                }
                val pct = if (total > 0) done * 100 / total else 0
                val progressColor = when {
                    pct <= 25 -> Color(0xFFE53935)
                    pct <= 50 -> Color(0xFFFB8C00)
                    pct <= 75 -> Color(0xFF1E88E5)
                    else -> Color(0xFF43A047)
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { done.toFloat() / total },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp,
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${pct}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = progressColor
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${done}/${total}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.stats_tasks_completed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            if (uiState.showCelebration) {
                ConfettiAnimation(
                    modifier = Modifier.fillMaxSize(),
                    onAnimationEnd = { }
                )
            }
        }
    }

    BackHandler(enabled = showDashboard) {
        showDashboard = false
    }

    BackHandler(enabled = uiState.selectedGroup != null) {
        viewModel.clearSelectedGroup()
    }

    if (showDashboard) {
        DashboardScreen(onBack = { showDashboard = false })
    }

    if (showCreateChoice) {
        CreateChoiceDialog(
            onDismiss = { showCreateChoice = false },
            onCreateTask = {
                showCreateChoice = false
                showAddDialog = true
            },
            onCreateGroup = {
                showCreateChoice = false
                showGroupDialog = true
            }
        )
    }

    if (showAddDialog) {
        TodoDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, priority, deadline, reminderMinutes, repeatType, isDone ->
                viewModel.addTodo(title, desc, priority, deadline, reminderMinutes, repeatType, isDone)
                showAddDialog = false
            }
        )
    }

    itemToEdit?.let { item ->
        TodoDialog(
            initialTitle = item.title,
            initialDesc = item.description,
            initialPriority = item.priority,
            initialDeadline = item.deadline,
            initialReminderMinutes = item.reminderMinutes,
            initialRepeatType = item.repeatType,
            initialIsDone = item.isDone || (item.repeatType != null && item.lastCompleted?.let { last ->
                val cal = Calendar.getInstance()
                val lastCal = Calendar.getInstance().apply { timeInMillis = last }
                cal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
            } == true),
            onDismiss = { itemToEdit = null },
            onConfirm = { title, desc, priority, deadline, reminderMinutes, repeatType, isDone ->
                viewModel.updateTodo(item.copy(title = title, description = desc, priority = priority, deadline = deadline, reminderMinutes = reminderMinutes, repeatType = repeatType, isDone = isDone, lastCompleted = if (!isDone && item.repeatType != null) null else item.lastCompleted))
                itemToEdit = null
            }
        )
    }

    if (showGroupDialog) {
        GroupDialog(
            initialTitle = groupToEdit?.title ?: "",
            initialDeadline = groupToEdit?.deadline,
            initialReminderMinutes = groupToEdit?.reminderMinutes,
            onDismiss = {
                showGroupDialog = false
                groupToEdit = null
            },
            onConfirm = { title, deadline, reminderMinutes ->
                if (groupToEdit != null) {
                    viewModel.updateGroup(groupToEdit!!.copy(title = title, deadline = deadline, reminderMinutes = reminderMinutes))
                } else {
                    viewModel.addGroup(title, deadline, reminderMinutes)
                }
                showGroupDialog = false
                groupToEdit = null
            }
        )
    }

    groupToEdit?.let { group ->
        if (!showGroupDialog) {
            GroupDialog(
                initialTitle = group.title,
                initialDeadline = group.deadline,
                initialReminderMinutes = group.reminderMinutes,
                onDismiss = { groupToEdit = null },
                onConfirm = { title, deadline, reminderMinutes ->
                    viewModel.updateGroup(group.copy(title = title, deadline = deadline, reminderMinutes = reminderMinutes))
                    groupToEdit = null
                }
            )
        }
    }

    if (showCelebrationDialog && uiState.showCelebration) {
        AlertDialog(
            onDismissRequest = {
                showCelebrationDialog = false
                viewModel.dismissCelebration()
            },
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.celebration_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = uiState.celebrationPhrase,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCelebrationDialog = false
                    viewModel.dismissCelebration()
                }) {
                    Text(stringResource(R.string.celebration_continue))
                }
            }
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.delete_category_title)) },
            text = { Text(stringResource(R.string.delete_category_message, group.title)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteGroup(group)
                    groupToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = Color(0xFF548FD7))
                }
            }
        )
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message, task.title)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTodo(task)
                    taskToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = Color(0xFF548FD7))
                }
            }
        )
    }
}

@Composable
private fun GroupItemRow(
    group: TodoGroup,
    totalTasks: Int = 0,
    doneTasks: Int = 0,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val groupBorderColor = if (group.isDone) Color(0xFF4CAF50) else Color(0xFF2196F3)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(2.dp, groupBorderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
          //  Icon(
          //      Icons.Default.Menu,
          //      contentDescription = "Arrastar",
         //       modifier = Modifier.padding(end = 8.dp),
         //       tint = MaterialTheme.colorScheme.outline
          //  )
            val context = LocalContext.current
            Icon(
                Icons.Filled.Apps,
                contentDescription = stringResource(R.string.icon_category),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(context) {
                        detectTapGestures {
                            Toast.makeText(context, "Segure e arraste para reposicionar", Toast.LENGTH_SHORT).show()
                        }
                    },
                tint = if (group.isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
            )
            Checkbox(
                checked = group.isDone,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (group.isDone) TextDecoration.LineThrough else null
                )
                if (group.deadline != null) {
                    Text(
                        text = stringResource(R.string.deadline_prefix) + SimpleDateFormat(stringResource(R.string.date_format_full), Locale.getDefault()).format(group.deadline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.icon_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
                if (totalTasks > 0) {
                    val gPct = doneTasks * 100 / totalTasks
                    val gColor = when {
                        gPct <= 25 -> Color(0xFFE53935)
                        gPct <= 50 -> Color(0xFFFB8C00)
                        gPct <= 75 -> Color(0xFF1E88E5)
                        else -> Color(0xFF43A047)
                    }
                    Text(
                        text = "feito ${doneTasks}/${totalTasks}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = gColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TodoItemRow(
    item: TodoItem,
    isNew: Boolean = false,
    elevation: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val isRecurringCompletedToday = item.repeatType != null && item.lastCompleted?.let { last ->
        val cal = Calendar.getInstance()
        val lastCal = Calendar.getInstance().apply { timeInMillis = last }
        cal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
    } == true
    val isDoneDisplay = item.isDone || isRecurringCompletedToday
    val borderColor = if (isDoneDisplay) Color(0xFF4CAF50) else Color(0xFFFFC107)
    val shimmerProgress = remember { Animatable(-1f) }
    LaunchedEffect(isNew) {
        if (isNew) {
            shimmerProgress.snapTo(-1f)
            shimmerProgress.animateTo(2f, animationSpec = tween(1500, easing = LinearEasing))
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .drawWithContent {
                drawContent()
                if (isNew && shimmerProgress.value < 2f) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            start = Offset(size.width * (shimmerProgress.value - 0.3f), 0f),
                            end = Offset(size.width * (shimmerProgress.value + 0.3f), size.height)
                        )
                    )
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            Icon(
                Icons.Filled.AdsClick,
 contentDescription = stringResource(R.string.icon_drag),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(context) {
                        detectTapGestures {
                            Toast.makeText(context, "Segure e arraste para reposicionar", Toast.LENGTH_SHORT).show()
                        }
                    },
                tint = if (isDoneDisplay) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
            )
            Checkbox(
                checked = isDoneDisplay,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isDoneDisplay) TextDecoration.LineThrough else null
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.deadline != null) {
                    Text(
                        text = stringResource(R.string.deadline_prefix) + SimpleDateFormat(stringResource(R.string.date_format_full), Locale.getDefault()).format(item.deadline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (item.repeatType != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val label = when (item.repeatType) {
                            1 -> stringResource(R.string.repeat_label_daily)
                            2 -> stringResource(R.string.repeat_label_weekly)
                            3 -> stringResource(R.string.repeat_label_monthly)
                            4 -> stringResource(R.string.repeat_label_yearly)
                            else -> ""
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.lastCompleted != null) {
                        Text(
                            text = stringResource(R.string.repeat_last_completed) + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(item.lastCompleted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.icon_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDialog(
    initialTitle: String = "",
    initialDesc: String = "",
    initialPriority: Int = 0,
    initialDeadline: Long? = null,
    initialReminderMinutes: Int? = null,
    initialRepeatType: Int? = null,
    initialIsDone: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long?, Int?, Int?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var desc by remember { mutableStateOf(initialDesc) }
    var priority by remember { mutableIntStateOf(initialPriority) }
    var hasDeadline by remember { mutableStateOf(initialDeadline != null) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var hasReminder by remember { mutableStateOf(initialReminderMinutes != null) }
    var repeatType by remember { mutableIntStateOf(initialRepeatType ?: 0) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var currentIsDone by remember { mutableStateOf(initialIsDone) }

    val repeatLabels = listOf(
        stringResource(R.string.repeat_none),
        stringResource(R.string.repeat_daily),
        stringResource(R.string.repeat_weekly),
        stringResource(R.string.repeat_monthly),
        stringResource(R.string.repeat_yearly)
    )
    var reminderValue by remember { mutableStateOf(
        initialReminderMinutes?.let { mins ->
            when {
                mins % 525600 == 0 -> (mins / 525600).toString()
                mins % 43200 == 0 -> (mins / 43200).toString()
                mins % 1440 == 0 -> (mins / 1440).toString()
                mins % 60 == 0 -> (mins / 60).toString()
                else -> mins.toString()
            }
        } ?: "1"
    ) }
    var reminderUnitIndex by remember { mutableIntStateOf(
        initialReminderMinutes?.let { mins ->
            when {
                mins % 525600 == 0 && mins / 525600 in 1..999 -> 4
                mins % 43200 == 0 && mins / 43200 in 1..999 -> 3
                mins % 1440 == 0 && mins / 1440 in 1..999 -> 2
                mins % 60 == 0 && mins / 60 in 1..999 -> 1
                else -> 0
            }
        } ?: 2
    ) }
    var showUnitMenu by remember { mutableStateOf(false) }
    val unitLabels = listOf(
        stringResource(R.string.reminder_unit_minutes),
        stringResource(R.string.reminder_unit_hours),
        stringResource(R.string.reminder_unit_days),
        stringResource(R.string.reminder_unit_months),
        stringResource(R.string.reminder_unit_years)
    )
    val unitMultipliers = listOf(1, 60, 1440, 43200, 525600)

    val datePickerInitial = deadline?.let {
        val local = Calendar.getInstance()
        local.timeInMillis = it
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.set(Calendar.YEAR, local.get(Calendar.YEAR))
        utc.set(Calendar.MONTH, local.get(Calendar.MONTH))
        utc.set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
        utc.set(Calendar.HOUR_OF_DAY, 0)
        utc.set(Calendar.MINUTE, 0)
        utc.set(Calendar.SECOND, 0)
        utc.set(Calendar.MILLISECOND, 0)
        utc.timeInMillis
    } ?: System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = datePickerInitial
    )
    val timePickerState = rememberTimePickerState(
        initialHour = if (deadline != null) {
            Calendar.getInstance().apply { timeInMillis = deadline!! }.get(Calendar.HOUR_OF_DAY)
        } else Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = if (deadline != null) {
            Calendar.getInstance().apply { timeInMillis = deadline!! }.get(Calendar.MINUTE)
        } else Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) stringResource(R.string.todo_dialog_title_add) else stringResource(R.string.todo_dialog_title_edit)) },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box {
                    OutlinedButton(onClick = { showRepeatMenu = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(repeatLabels[repeatType])
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showRepeatMenu,
                        onDismissRequest = { showRepeatMenu = false }
                    ) {
                        repeatLabels.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    repeatType = index
                                    showRepeatMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.task_already_done), modifier = Modifier.weight(1f))
                    Switch(checked = currentIsDone, onCheckedChange = { currentIsDone = it })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                    Text(stringResource(R.string.deadline_checkbox))
                }
                if (hasDeadline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (deadline != null) {
                                    SimpleDateFormat(stringResource(R.string.date_format_date), Locale.getDefault()).format(deadline)
                                } else stringResource(R.string.date_placeholder)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (deadline != null) {
                                    SimpleDateFormat(stringResource(R.string.date_format_time), Locale.getDefault()).format(deadline)
                                } else stringResource(R.string.time_placeholder)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasReminder, onCheckedChange = { hasReminder = it })
                        Text(stringResource(R.string.reminder_checkbox))
                    }
                    if (hasReminder) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = reminderValue,
                                onValueChange = { reminderValue = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                OutlinedButton(onClick = { showUnitMenu = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(unitLabels[reminderUnitIndex])
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showUnitMenu,
                                    onDismissRequest = { showUnitMenu = false }
                                ) {
                                    unitLabels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                reminderUnitIndex = index
                                                showUnitMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val reminderMinutes = if (hasDeadline && hasReminder) {
                            (reminderValue.toIntOrNull() ?: 0) * unitMultipliers[reminderUnitIndex]
                        } else null
                        onConfirm(title, desc, priority, deadline, if (reminderMinutes == 0) null else reminderMinutes, if (repeatType == 0) null else repeatType, currentIsDone)
                    }
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Color(0xFF548FD7))
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = selectedDate
                        val cal = Calendar.getInstance()
                        if (deadline != null) cal.timeInMillis = deadline!!
                        cal.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        deadline = cal.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.time_picker_title)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    if (deadline != null) cal.timeInMillis = deadline!!
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(Calendar.MINUTE, timePickerState.minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    deadline = cal.timeInMillis
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDialog(
    initialTitle: String = "",
    initialDeadline: Long? = null,
    initialReminderMinutes: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Int?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var hasDeadline by remember { mutableStateOf(initialDeadline != null) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var hasReminder by remember { mutableStateOf(initialReminderMinutes != null) }
    var reminderValue by remember { mutableStateOf(
        initialReminderMinutes?.let { mins ->
            when {
                mins % 525600 == 0 -> (mins / 525600).toString()
                mins % 43200 == 0 -> (mins / 43200).toString()
                mins % 1440 == 0 -> (mins / 1440).toString()
                mins % 60 == 0 -> (mins / 60).toString()
                else -> mins.toString()
            }
        } ?: "1"
    ) }
    var reminderUnitIndex by remember { mutableIntStateOf(
        initialReminderMinutes?.let { mins ->
            when {
                mins % 525600 == 0 && mins / 525600 in 1..999 -> 4
                mins % 43200 == 0 && mins / 43200 in 1..999 -> 3
                mins % 1440 == 0 && mins / 1440 in 1..999 -> 2
                mins % 60 == 0 && mins / 60 in 1..999 -> 1
                else -> 0
            }
        } ?: 2
    ) }
    var showUnitMenu by remember { mutableStateOf(false) }
    val unitLabels = listOf(
        stringResource(R.string.reminder_unit_minutes),
        stringResource(R.string.reminder_unit_hours),
        stringResource(R.string.reminder_unit_days),
        stringResource(R.string.reminder_unit_months),
        stringResource(R.string.reminder_unit_years)
    )
    val unitMultipliers = listOf(1, 60, 1440, 43200, 525600)

    val datePickerInitial = deadline?.let {
        val local = Calendar.getInstance()
        local.timeInMillis = it
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.set(Calendar.YEAR, local.get(Calendar.YEAR))
        utc.set(Calendar.MONTH, local.get(Calendar.MONTH))
        utc.set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
        utc.set(Calendar.HOUR_OF_DAY, 0)
        utc.set(Calendar.MINUTE, 0)
        utc.set(Calendar.SECOND, 0)
        utc.set(Calendar.MILLISECOND, 0)
        utc.timeInMillis
    } ?: System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = datePickerInitial
    )
    val timePickerState = rememberTimePickerState(
        initialHour = if (deadline != null) {
            Calendar.getInstance().apply { timeInMillis = deadline!! }.get(Calendar.HOUR_OF_DAY)
        } else Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = if (deadline != null) {
            Calendar.getInstance().apply { timeInMillis = deadline!! }.get(Calendar.MINUTE)
        } else Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) stringResource(R.string.group_dialog_title_add) else stringResource(R.string.group_dialog_title_edit)) },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_group_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                    Text(stringResource(R.string.deadline_checkbox))
                }
                if (hasDeadline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (deadline != null) {
                                    SimpleDateFormat(stringResource(R.string.date_format_date), Locale.getDefault()).format(deadline)
                                } else stringResource(R.string.date_placeholder)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (deadline != null) {
                                    SimpleDateFormat(stringResource(R.string.date_format_time), Locale.getDefault()).format(deadline)
                                } else stringResource(R.string.time_placeholder)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasReminder, onCheckedChange = { hasReminder = it })
                        Text(stringResource(R.string.reminder_checkbox))
                    }
                    if (hasReminder) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = reminderValue,
                                onValueChange = { reminderValue = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                OutlinedButton(onClick = { showUnitMenu = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(unitLabels[reminderUnitIndex])
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showUnitMenu,
                                    onDismissRequest = { showUnitMenu = false }
                                ) {
                                    unitLabels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                reminderUnitIndex = index
                                                showUnitMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val reminderMinutes = if (hasDeadline && hasReminder) {
                            (reminderValue.toIntOrNull() ?: 0) * unitMultipliers[reminderUnitIndex]
                        } else null
                        onConfirm(title, deadline, if (reminderMinutes == 0) null else reminderMinutes)
                    }
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Color(0xFF548FD7))
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = selectedDate
                        val cal = Calendar.getInstance()
                        if (deadline != null) cal.timeInMillis = deadline!!
                        cal.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        deadline = cal.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.time_picker_title)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    if (deadline != null) cal.timeInMillis = deadline!!
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(Calendar.MINUTE, timePickerState.minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    deadline = cal.timeInMillis
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CreateChoiceDialog(
    onDismiss: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_choice_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCreateTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                   // Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_task), color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCreateGroup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.width(8.dp).size(6.dp))
                    Text(stringResource(R.string.create_category), color = Color.White)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Color(0xFF548FD7))
            }
        }
    )
}

@Composable
private fun ConfettiAnimation(
    modifier: Modifier = Modifier,
    onAnimationEnd: () -> Unit
) {
    val particles = remember {
        val colors = listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
            Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA),
            Color(0xFFFCBAD3), Color(0xFFA8D8EA), Color(0xFFAA96DA),
            Color(0xFFFFD93D)
        )
        List(60) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -Random.nextFloat() * 0.5f,
                velocityX = (Random.nextFloat() - 0.5f) * 300f,
                velocityY = Random.nextFloat() * 300f + 200f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 360f,
                size = Random.nextFloat() * 12f + 6f,
                color = colors[Random.nextInt(colors.size)],
                delay = Random.nextFloat() * 2f
            )
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }
    val duration = 3f

    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (elapsed < duration) {
            elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
            delay(16)
        }
        onAnimationEnd()
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        particles.forEach { p ->
            val t = elapsed - p.delay
            if (t < 0f) return@forEach

            val x = p.x * canvasWidth + p.velocityX * t + 0.5f * 0f * t * t
            val y = p.y * canvasHeight + p.velocityY * t + 0.5f * 300f * t * t
            val alpha = (1f - t / duration).coerceIn(0f, 1f)
            val rotation = p.rotation + p.rotationSpeed * t
            val size = p.size

            if (y < canvasHeight + size && x > -size && x < canvasWidth + size) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(
                        x = x.toFloat(),
                        y = y.toFloat()
                    ),
                    size = Size(size, size * 0.6f)
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color,
    val delay: Float
)
