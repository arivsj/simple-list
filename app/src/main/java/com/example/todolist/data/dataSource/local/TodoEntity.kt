package com.example.todolist.data.dataSource.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.todolist.models.TodoItem

@Entity(tableName = "todo_items")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val isDone: Boolean,
    val priority: Int,
    val groupId: Long? = null
)

fun TodoEntity.toDomain() = TodoItem(
    id = id,
    title = title,
    description = description,
    isDone = isDone,
    priority = priority,
    groupId = groupId
)

fun TodoItem.toEntity() = TodoEntity(
    id = id,
    title = title,
    description = description,
    isDone = isDone,
    priority = priority,
    groupId = groupId
)
