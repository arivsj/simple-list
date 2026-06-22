package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoRepository
import com.example.todolist.models.TodoItem
import javax.inject.Inject

class ReorderTodosUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(items: List<TodoItem>) {
        // Re-assign priorities based on index. 
        // Higher index = lower priority in a sorted list if we want it to stay exactly like this.
        // Since DAO sorts by priority DESC:
        val reorderedItems = items.mapIndexed { index, item ->
            item.copy(priority = items.size - index)
        }
        repository.updateItems(reorderedItems)
    }
}
