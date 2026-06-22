package com.example.todolist.domain.repository

import com.example.todolist.models.TodoGroup
import kotlinx.coroutines.flow.Flow

interface TodoGroupRepository {
    fun getAllGroups(): Flow<List<TodoGroup>>
    suspend fun insertGroup(group: TodoGroup)
    suspend fun updateGroup(group: TodoGroup)
    suspend fun updateGroups(groups: List<TodoGroup>)
    suspend fun deleteGroup(group: TodoGroup)
    suspend fun getGroupById(id: Long): TodoGroup?
}
