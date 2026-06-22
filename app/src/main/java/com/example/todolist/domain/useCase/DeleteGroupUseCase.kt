package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.models.TodoGroup
import javax.inject.Inject

class DeleteGroupUseCase @Inject constructor(
    private val repository: TodoGroupRepository
) {
    suspend operator fun invoke(group: TodoGroup) = repository.deleteGroup(group)
}
