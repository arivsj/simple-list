package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoRepository
import com.example.todolist.models.TodoItem
import javax.inject.Inject

class UpdateTodoUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(item: TodoItem) = repository.updateItem(item)
}
