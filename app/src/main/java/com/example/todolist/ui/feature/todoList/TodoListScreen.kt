package com.example.todolist.ui.feature.todoList

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.todolist.models.TodoGroup
import com.example.todolist.models.TodoItem
import com.example.todolist.viewModel.TodoViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private sealed class UiItem {
    data class GroupItem(val group: TodoGroup) : UiItem()
    data class TaskItem(val item: TodoItem) : UiItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(viewModel: TodoViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TodoItem?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<TodoGroup?>(null) }
    var showCreateChoice by remember { mutableStateOf(false) }
    var showCelebrationDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var draggedItemType by remember { mutableStateOf<Class<*>?>(null) }

    val mergedItems: List<UiItem> = remember(uiState.groups, uiState.standaloneItems, uiState.selectedGroup, uiState.groupItems) {
        if (uiState.selectedGroup != null) {
            uiState.groupItems.map { UiItem.TaskItem(it) }
        } else {
            buildList {
                if (uiState.groups.isNotEmpty()) {
                    addAll(uiState.groups.map { UiItem.GroupItem(it) })
                }
                if (uiState.standaloneItems.isNotEmpty()) {
                    addAll(uiState.standaloneItems.map { UiItem.TaskItem(it) })
                }
            }
        }
    }

    LaunchedEffect(uiState.showCelebration) {
        if (uiState.showCelebration) {
            showCelebrationDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.selectedGroup != null) uiState.selectedGroup!!.title else "Simple List")
                },
                navigationIcon = {
                    if (uiState.selectedGroup != null) {
                        IconButton(onClick = { viewModel.clearSelectedGroup() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (uiState.selectedGroup != null) {
                    showAddDialog = true
                } else {
                    showCreateChoice = true
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (mergedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.selectedGroup != null) "Nenhuma tarefa neste grupo" else "Nenhum item ainda",
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

                                    if (targetItem != null) {
                                        val targetIndexInType = mergedItems.withIndex()
                                            .filter { it.value::class.java == type }
                                            .indexOfFirst { it.index == targetItem.index }

                                        val currentIndexInType = mergedItems.withIndex()
                                            .filter { it.value::class.java == type }
                                            .indexOfFirst { it.index == currentIndex }

                                        if (targetIndexInType >= 0 && currentIndexInType >= 0) {
                                            when (type) {
                                                UiItem.GroupItem::class.java -> {
                                                    viewModel.moveGroup(currentIndexInType, targetIndexInType)
                                                }
                                                UiItem.TaskItem::class.java -> {
                                                    viewModel.moveTodo(currentIndexInType, targetIndexInType)
                                                }
                                            }
                                            draggedItemIndex = targetItem.index
                                            draggingOffset += (draggedInfo.offset - targetItem.offset)
                                        }
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
                                    draggedItemIndex = null
                                    draggingOffset = 0f
                                    draggedItemType = null
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
                                    GroupItemRow(
                                        group = item.group,
                                        elevation = elevation,
                                        onClick = { viewModel.selectGroup(item.group) },
                                        onToggle = { viewModel.toggleGroup(item.group) },
                                        onEdit = { groupToEdit = item.group },
                                        onDelete = { viewModel.deleteGroup(item.group) }
                                    )
                                }
                                is UiItem.TaskItem -> {
                                    TodoItemRow(
                                        item = item.item,
                                        elevation = elevation,
                                        onToggle = { viewModel.toggleTodo(item.item) },
                                        onDelete = { viewModel.deleteTodo(item.item) },
                                        onEdit = { itemToEdit = item.item }
                                    )
                                }
                            }
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
            onConfirm = { title, desc, priority ->
                viewModel.addTodo(title, desc, priority)
                showAddDialog = false
            }
        )
    }

    itemToEdit?.let { item ->
        TodoDialog(
            initialTitle = item.title,
            initialDesc = item.description,
            initialPriority = item.priority,
            onDismiss = { itemToEdit = null },
            onConfirm = { title, desc, priority ->
                viewModel.updateTodo(item.copy(title = title, description = desc, priority = priority))
                itemToEdit = null
            }
        )
    }

    if (showGroupDialog) {
        GroupDialog(
            initialTitle = groupToEdit?.title ?: "",
            onDismiss = {
                showGroupDialog = false
                groupToEdit = null
            },
            onConfirm = { title ->
                if (groupToEdit != null) {
                    viewModel.updateGroup(groupToEdit!!.copy(title = title))
                } else {
                    viewModel.addGroup(title)
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
                onDismiss = { groupToEdit = null },
                onConfirm = { title ->
                    viewModel.updateGroup(group.copy(title = title))
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
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Parabéns!",
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
                    Text("Continuar")
                }
            }
        )
    }
}

@Composable
private fun GroupItemRow(
    group: TodoGroup,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Arrastar",
                modifier = Modifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Grupo",
                modifier = Modifier.padding(end = 8.dp),
                tint = if (group.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Checkbox(
                checked = group.isDone,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (group.isDone) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir")
            }
        }
    }
}

@Composable
fun TodoItemRow(
    item: TodoItem,
    elevation: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Arrastar",
                modifier = Modifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir")
            }
        }
    }
}

@Composable
fun TodoDialog(
    initialTitle: String = "",
    initialDesc: String = "",
    initialPriority: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var desc by remember { mutableStateOf(initialDesc) }
    var priority by remember { mutableIntStateOf(initialPriority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) "Adicionar Tarefa" else "Editar Tarefa") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, desc, priority)
                    }
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun GroupDialog(
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) "Criar Grupo" else "Editar Grupo") },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nome do Grupo") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title)
                    }
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CreateChoiceDialog(
    onDismiss: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("O que deseja criar?") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCreateTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Criar Tarefa")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCreateGroup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Criar Grupo")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
