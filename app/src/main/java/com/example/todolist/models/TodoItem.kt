package com.example.todolist.models

data class TodoItem(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Int = 0, // Used for reclassification
    val groupId: Long? = null
)
