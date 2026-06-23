package com.example.todolist.models

data class TodoItem(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Int = 0,
    val groupId: Long? = null,
    val deadline: Long? = null,
    val reminderMinutes: Int? = null,
    val repeatType: Int? = null,
    val lastCompleted: Long? = null
) {
    companion object {
        const val REPEAT_DAILY = 1
        const val REPEAT_WEEKLY = 2
        const val REPEAT_MONTHLY = 3
        const val REPEAT_YEARLY = 4
    }
}
