package com.example.todolist.domain.useCase

import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.models.TodoGroup
import javax.inject.Inject

class ReorderGroupsUseCase @Inject constructor(
    private val repository: TodoGroupRepository
) {
    suspend operator fun invoke(groups: List<TodoGroup>) {
        val reordered = groups.mapIndexed { index, group ->
            group.copy(priority = groups.size - index)
        }
        repository.updateGroups(reordered)
    }
}
