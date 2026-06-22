package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoRepository
import com.example.todolist.models.TodoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllTodosUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    operator fun invoke(): Flow<List<TodoItem>> = repository.getAllItems()
}
