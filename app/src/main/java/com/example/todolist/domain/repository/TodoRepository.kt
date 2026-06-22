package com.example.todolist.domain.repository

import com.example.todolist.models.TodoItem
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun getAllItems(): Flow<List<TodoItem>>
    suspend fun insertItem(item: TodoItem)
    suspend fun updateItem(item: TodoItem)
    suspend fun updateItems(items: List<TodoItem>)
    suspend fun deleteItem(item: TodoItem)
    suspend fun getItemById(id: Long): TodoItem?
}
