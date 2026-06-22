package com.example.todolist.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.domain.useCase.*
import com.example.todolist.models.TodoGroup
import com.example.todolist.models.TodoItem
import com.example.todolist.notification.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val _selectedGroup = MutableStateFlow<TodoGroup?>(null)
    private val _showCelebration = MutableStateFlow(false)
    private val _celebrationPhrase = MutableStateFlow("")

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
            val groupId = addGroupUseCase(TodoGroup(title = title, deadline = deadline, reminderMinutes = reminderMinutes))
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
            if (!newDone) {
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

    fun addTodo(title: String, description: String = "", priority: Int = 0, deadline: Long? = null, reminderMinutes: Int? = null) {
        val groupId = _selectedGroup.value?.id
        viewModelScope.launch {
            val itemId = addTodoUseCase(TodoItem(title = title, description = description, priority = priority, groupId = groupId, deadline = deadline, reminderMinutes = reminderMinutes))
            scheduleReminder(itemId, title, deadline, reminderMinutes)
        }
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
            val updated = item.copy(isDone = !item.isDone)
            updateTodoUseCase(updated)
            if (updated.isDone) {
                checkAllCompleted()
                reminderScheduler.cancel(updated.id)
            } else {
                scheduleReminder(updated.id, updated.title, updated.deadline, updated.reminderMinutes)
            }
            item.groupId?.let { groupId ->
                val groupItems = _allItems.value.filter { it.groupId == groupId }
                val allDone = groupItems.all { it.id == updated.id || it.isDone }
                val group = _roomGroups.value.find { it.id == groupId }
                if (group != null && allDone && !group.isDone) {
                    updateGroupUseCase(group.copy(isDone = true))
                } else if (group != null && !allDone && group.isDone) {
                    updateGroupUseCase(group.copy(isDone = false))
                }
            }
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

    private fun checkAllCompleted() {
        val allDone = _allItems.value.all { it.isDone }
        if (allDone && _allItems.value.isNotEmpty()) {
            _celebrationPhrase.value = inspirationalPhrases.random()
            _showCelebration.value = true
        }
    }

    fun dismissCelebration() {
        _showCelebration.value = false
    }

    companion object {
        private val inspirationalPhrases = listOf(
            "A persistência é o caminho do êxito.",
            "O sucesso nasce da vontade de vencer.",
            "Tudo que você precisa está dentro de você.",
            "Acredite no seu potencial infinito.",
            "Cada passo conta na jornada.",
            "Você é mais forte do que imagina.",
            "O impossível é só questão de opinião.",
            "Grandes realizações começam com pequenos passos.",
            "Acredite que você pode, já está no meio do caminho.",
            "Seu único limite é você mesmo.",
            "A coragem não é a ausência do medo, mas a vitória sobre ele.",
            "Sonhe grande, trabalhe duro.",
            "A felicidade está nas pequenas conquistas diárias.",
            "Você é capaz de coisas incríveis.",
            "Não pare quando estiver cansado, pare quando estiver pronto.",
            "O sucesso é a soma de pequenos esforços repetidos diariamente.",
            "Acredite em si mesmo e todo o resto se encaixa.",
            "Cada dia é uma nova oportunidade para brilhar.",
            "Sua determinação é sua maior força.",
            "Nunca é tarde demais para ser o que você poderia ter sido.",
            "O segredo do sucesso é começar.",
            "Você já venceu ao não ter desistido."
        )
    }
}
