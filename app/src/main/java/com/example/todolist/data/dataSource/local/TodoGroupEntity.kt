package com.example.todolist.data.dataSource.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.todolist.models.TodoGroup

@Entity(tableName = "todo_groups")
data class TodoGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isDone: Boolean,
    val priority: Int,
    val deadline: Long? = null,
    val reminderMinutes: Int? = null
)

fun TodoGroupEntity.toDomain() = TodoGroup(
    id = id,
    title = title,
    isDone = isDone,
    priority = priority,
    deadline = deadline,
    reminderMinutes = reminderMinutes
)

fun TodoGroup.toEntity() = TodoGroupEntity(
    id = id,
    title = title,
    isDone = isDone,
    priority = priority,
    deadline = deadline,
    reminderMinutes = reminderMinutes
)
