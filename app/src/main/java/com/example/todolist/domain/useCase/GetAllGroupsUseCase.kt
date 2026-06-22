package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.models.TodoGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllGroupsUseCase @Inject constructor(
    private val repository: TodoGroupRepository
) {
    operator fun invoke(): Flow<List<TodoGroup>> = repository.getAllGroups()
}
