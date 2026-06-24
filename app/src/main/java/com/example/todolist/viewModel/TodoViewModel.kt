package com.example.todolist.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.R
import com.example.todolist.models.TodoItem.Companion.REPEAT_DAILY
import com.example.todolist.models.TodoItem.Companion.REPEAT_MONTHLY
import com.example.todolist.models.TodoItem.Companion.REPEAT_WEEKLY
import com.example.todolist.models.TodoItem.Companion.REPEAT_YEARLY
import java.util.Calendar
import com.example.todolist.domain.useCase.*
import com.example.todolist.models.TodoGroup
import com.example.todolist.models.TodoItem
import com.example.todolist.notification.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoUiState(
    val groups: List<TodoGroup> = emptyList(),
    val standaloneItems: List<TodoItem> = emptyList(),
    val selectedGroup: TodoGroup? = null,
    val groupItems: List<TodoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCelebration: Boolean = false,
    val celebrationPhrase: String = ""
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getAllTodosUseCase: GetAllTodosUseCase,
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val reorderTodosUseCase: ReorderTodosUseCase,
    private val getAllGroupsUseCase: GetAllGroupsUseCase,
    private val addGroupUseCase: AddGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val reorderGroupsUseCase: ReorderGroupsUseCase,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _roomGroups = MutableStateFlow<List<TodoGroup>>(emptyList())
    private val _allItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val allItems: StateFlow<List<TodoItem>> = _allItems
    private val _selectedGroup = MutableStateFlow<TodoGroup?>(null)
    private val _showCelebration = MutableStateFlow(false)
    private val _celebrationPhrase = MutableStateFlow("")
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage
    private val _highlightItemId = MutableStateFlow<Long?>(null)
    val highlightItemId: StateFlow<Long?> = _highlightItemId

    private var _dragGroups: List<TodoGroup>? = null
    private var _dragStandaloneItems: List<TodoItem>? = null
    private var _dragGroupItems: List<TodoItem>? = null

    val uiState: StateFlow<TodoUiState> = combine(
        getAllGroupsUseCase(),
        getAllTodosUseCase(),
        _selectedGroup,
        _showCelebration,
        _celebrationPhrase
    ) { roomGroups, allItems, selectedGroup, showCelebration, phrase ->
        _roomGroups.value = roomGroups
        _allItems.value = allItems

        TodoUiState(
            groups = _dragGroups ?: roomGroups,
            standaloneItems = allItems.filter { it.groupId == null },
            selectedGroup = selectedGroup,
            groupItems = if (selectedGroup != null) allItems.filter { it.groupId == selectedGroup.id } else emptyList(),
            isLoading = false,
            showCelebration = showCelebration,
            celebrationPhrase = phrase
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodoUiState(isLoading = true)
    )

    fun selectGroup(group: TodoGroup) {
        _selectedGroup.value = group
        _dragGroupItems = null
    }

    fun clearSelectedGroup() {
        _selectedGroup.value = null
        _dragGroupItems = null
    }

    fun moveGroup(fromIndex: Int, toIndex: Int) {
        val current = _dragGroups ?: _roomGroups.value
        if (current.isEmpty()) return
        val mutable = current.toMutableList()
        if (fromIndex in mutable.indices && toIndex in mutable.indices) {
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            _dragGroups = mutable
        }
    }

    fun saveGroupsOrder() {
        viewModelScope.launch {
            val groups = _dragGroups
            if (groups != null) {
                reorderGroupsUseCase(groups)
                _dragGroups = null
            }
        }
    }

    fun addGroup(title: String, deadline: Long? = null, reminderMinutes: Int? = null) {
        viewModelScope.launch {
            val maxPriority = (_roomGroups.value.maxOfOrNull { it.priority } ?: 0) + 1
            val groupId = addGroupUseCase(TodoGroup(title = title, deadline = deadline, reminderMinutes = reminderMinutes, priority = maxPriority))
            scheduleReminder(groupId, title, deadline, reminderMinutes)
        }
    }

    fun updateGroup(group: TodoGroup) {
        reminderScheduler.cancel(group.id)
        viewModelScope.launch {
            updateGroupUseCase(group)
            scheduleReminder(group.id, group.title, group.deadline, group.reminderMinutes)
        }
    }

    fun deleteGroup(group: TodoGroup) {
        reminderScheduler.cancel(group.id)
        viewModelScope.launch {
            val groupItems = _allItems.value.filter { it.groupId == group.id }
            groupItems.forEach { item ->
                reminderScheduler.cancel(item.id)
                deleteTodoUseCase(item)
            }
            deleteGroupUseCase(group)
            if (_selectedGroup.value?.id == group.id) {
                clearSelectedGroup()
            }
        }
    }

    fun toggleGroup(group: TodoGroup) {
        val newDone = !group.isDone
        if (newDone) {
            reminderScheduler.cancel(group.id)
        }
        viewModelScope.launch {
            updateGroupUseCase(group.copy(isDone = newDone))
            val groupItems = _allItems.value.filter { it.groupId == group.id }
            groupItems.forEach { item ->
                reminderScheduler.cancel(item.id)
                if (newDone && item.repeatType != null) {
                    val nextDeadline = advanceDeadline(item.deadline ?: System.currentTimeMillis(), item.repeatType)
                    updateTodoUseCase(item.copy(
                        lastCompleted = System.currentTimeMillis(),
                        deadline = nextDeadline,
                        isDone = false
                    ))
                    scheduleReminder(item.id, item.title, nextDeadline, item.reminderMinutes)
                } else {
                    updateTodoUseCase(item.copy(isDone = newDone))
                }
            }
            if (newDone) {
                showCelebration()
            } else {
                scheduleReminder(group.id, group.title, group.deadline, group.reminderMinutes)
            }
        }
    }

    fun moveTodo(fromIndex: Int, toIndex: Int) {
        if (_selectedGroup.value != null) {
            val current = _dragGroupItems ?: _allItems.value.filter { it.groupId == _selectedGroup.value!!.id }
            if (fromIndex in current.indices && toIndex in current.indices) {
                val mutable = current.toMutableList()
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                _dragGroupItems = mutable
            }
        } else {
            val current = _dragStandaloneItems ?: _allItems.value.filter { it.groupId == null }
            if (fromIndex in current.indices && toIndex in current.indices) {
                val mutable = current.toMutableList()
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                _dragStandaloneItems = mutable
            }
        }
    }

    fun saveOrder() {
        viewModelScope.launch {
            val itemsToSave = if (_selectedGroup.value != null) {
                _dragGroupItems ?: _allItems.value.filter { it.groupId == _selectedGroup.value!!.id }
            } else {
                _dragStandaloneItems ?: _allItems.value.filter { it.groupId == null }
            }
            reorderTodosUseCase(itemsToSave)
            _dragStandaloneItems = null
            _dragGroupItems = null
        }
    }

    fun addTodo(title: String, description: String = "", priority: Int = 0, deadline: Long? = null, reminderMinutes: Int? = null, repeatType: Int? = null, isDone: Boolean = false) {
        val groupId = _selectedGroup.value?.id
        viewModelScope.launch {
            val items = if (groupId != null) _allItems.value.filter { it.groupId == groupId }
                        else _allItems.value.filter { it.groupId == null }
            val maxPriority = (items.maxOfOrNull { it.priority } ?: 0) + 1
            val itemId = addTodoUseCase(TodoItem(title = title, description = description, priority = maxPriority, groupId = groupId, deadline = deadline, reminderMinutes = reminderMinutes, repeatType = repeatType, isDone = isDone))
            _highlightItemId.value = itemId
            scheduleReminder(itemId, title, deadline, reminderMinutes)
        }
    }

    fun clearHighlightItemId() {
        _highlightItemId.value = null
    }

    fun updateTodo(item: TodoItem) {
        reminderScheduler.cancel(item.id)
        viewModelScope.launch {
            updateTodoUseCase(item)
            scheduleReminder(item.id, item.title, item.deadline, item.reminderMinutes)
        }
    }

    fun deleteTodo(item: TodoItem) {
        reminderScheduler.cancel(item.id)
        viewModelScope.launch {
            deleteTodoUseCase(item)
        }
    }

    fun toggleTodo(item: TodoItem) {
        viewModelScope.launch {
            if (item.repeatType != null) {
                if (isCompletedToday(item.lastCompleted)) {
                    _toastMessage.value = appContext.getString(R.string.already_completed_today)
                    return@launch
                }
                val nextDeadline = advanceDeadline(item.deadline ?: System.currentTimeMillis(), item.repeatType)
                val updated = item.copy(
                    lastCompleted = System.currentTimeMillis(),
                    deadline = nextDeadline,
                    isDone = false
                )
                updateTodoUseCase(updated)
                reminderScheduler.cancel(updated.id)
                scheduleReminder(updated.id, updated.title, nextDeadline, updated.reminderMinutes)
                showCelebration()
            } else {
                val becomingDone = !item.isDone
                val updated = item.copy(isDone = becomingDone, priority = if (becomingDone) 0 else item.priority)
                updateTodoUseCase(updated)
                if (updated.isDone) {
                    reminderScheduler.cancel(updated.id)
                } else {
                    scheduleReminder(updated.id, updated.title, updated.deadline, updated.reminderMinutes)
                }
                item.groupId?.let { groupId ->
                    val groupItems = _allItems.value.filter { it.groupId == groupId }
                    val allGroupDone = groupItems.all { it.id == updated.id || it.isDone }
                    val group = _roomGroups.value.find { it.id == groupId }
                    if (group != null && allGroupDone && !group.isDone) {
                        updateGroupUseCase(group.copy(isDone = true))
                        if (updated.isDone) showCelebration()
                    } else if (group != null && !allGroupDone && group.isDone) {
                        updateGroupUseCase(group.copy(isDone = false))
                    }
                }
                if (item.groupId == null && updated.isDone) {
                    showCelebration()
                }
            }
        }
    }

    private fun isCompletedToday(lastCompleted: Long?): Boolean {
        if (lastCompleted == null) return false
        val today = Calendar.getInstance()
        val last = Calendar.getInstance().apply { timeInMillis = lastCompleted }
        return last.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                last.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun advanceDeadline(currentDeadline: Long, repeatType: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentDeadline
        return when (repeatType) {
            REPEAT_DAILY -> cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            REPEAT_WEEKLY -> cal.apply { add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
            REPEAT_MONTHLY -> cal.apply { add(Calendar.MONTH, 1) }.timeInMillis
            REPEAT_YEARLY -> cal.apply { add(Calendar.YEAR, 1) }.timeInMillis
            else -> currentDeadline
        }
    }

    private fun scheduleReminder(itemId: Long, title: String, deadline: Long?, reminderMinutes: Int?) {
        if (deadline != null && reminderMinutes != null && reminderMinutes > 0) {
            val triggerTime = deadline - (reminderMinutes * 60_000L)
            if (triggerTime > System.currentTimeMillis()) {
                reminderScheduler.schedule(itemId, title, triggerTime)
            }
        }
    }

    private fun showCelebration() {
        _celebrationPhrase.value = appContext.resources.getStringArray(R.array.phrases).random()
        _showCelebration.value = true
    }

    fun dismissCelebration() {
        _showCelebration.value = false
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }
}
