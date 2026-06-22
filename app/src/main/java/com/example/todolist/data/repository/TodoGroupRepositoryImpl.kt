package com.example.todolist.data.repository

import com.example.todolist.data.dataSource.local.TodoGroupDao
import com.example.todolist.data.dataSource.local.toDomain
import com.example.todolist.data.dataSource.local.toEntity
import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.models.TodoGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TodoGroupRepositoryImpl @Inject constructor(
    private val dao: TodoGroupDao
) : TodoGroupRepository {
    override fun getAllGroups(): Flow<List<TodoGroup>> {
        return dao.getAllGroups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertGroup(group: TodoGroup) {
        dao.insertGroup(group.toEntity())
    }

    override suspend fun updateGroup(group: TodoGroup) {
        dao.updateGroup(group.toEntity())
    }

    override suspend fun updateGroups(groups: List<TodoGroup>) {
        dao.updateGroups(groups.map { it.toEntity() })
    }

    override suspend fun deleteGroup(group: TodoGroup) {
        dao.deleteGroup(group.toEntity())
    }

    override suspend fun getGroupById(id: Long): TodoGroup? {
        return dao.getGroupById(id)?.toDomain()
    }
}
