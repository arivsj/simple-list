package com.example.todolist.models

data class TodoGroup(
    val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val priority: Int = 0,
    val deadline: Long? = null,
    val reminderMinutes: Int? = null
)
