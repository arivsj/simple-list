package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoRepository
import javax.inject.Inject

class GetTodoByIdUseCase @Inject constructor(
    private val repository: TodoRepository
) {
    suspend operator fun invoke(id: Long) = repository.getItemById(id)
}
