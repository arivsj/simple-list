package com.example.todolist.data.repository

import com.example.todolist.data.dataSource.local.TodoDao
import com.example.todolist.data.dataSource.local.toDomain
import com.example.todolist.data.dataSource.local.toEntity
import com.example.todolist.domain.repository.TodoRepository
import com.example.todolist.models.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val dao: TodoDao
) : TodoRepository {
    override fun getAllItems(): Flow<List<TodoItem>> {
        return dao.getAllItems().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertItem(item: TodoItem): Long {
        return dao.insertItem(item.toEntity())
    }

    override suspend fun updateItem(item: TodoItem) {
        dao.updateItem(item.toEntity())
    }

    override suspend fun updateItems(items: List<TodoItem>) {
        dao.updateItems(items.map { it.toEntity() })
    }

    override suspend fun deleteItem(item: TodoItem) {
        dao.deleteItem(item.toEntity())
    }

    override suspend fun getItemById(id: Long): TodoItem? {
        return dao.getItemById(id)?.toDomain()
    }
}
