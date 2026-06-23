package com.example.todolist.stats

import com.example.todolist.data.dataSource.local.TodoDao
import com.example.todolist.data.dataSource.local.TodoGroupDao
import com.example.todolist.models.TodoItem
import com.example.todolist.models.TodoGroup
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class DailyCompletion(val day: String, val count: Int)

data class CategoryStats(val name: String, val total: Int, val completed: Int, val rate: Float)

data class DashboardStats(
    val totalTasks: Int,
    val completedTasks: Int,
    val pendingTasks: Int,
    val overdueTasks: Int,
    val completionRate: Float,
    val dailyCompletions: List<DailyCompletion>,
    val categoryStats: List<CategoryStats>,
    val bestCategory: CategoryStats?,
    val worstCategory: CategoryStats?,
    val recurringActive: Int,
    val tasksWithDeadline: Int
)

@Singleton
class StatsCalculator @Inject constructor(
    private val todoDao: TodoDao,
    private val groupDao: TodoGroupDao
) {
    suspend fun compute(days: Int = 0): DashboardStats {
        val allItems = todoDao.getAllItemsOnce().map { entity ->
            TodoItem(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                isDone = entity.isDone,
                priority = entity.priority,
                groupId = entity.groupId,
                deadline = entity.deadline,
                reminderMinutes = entity.reminderMinutes,
                repeatType = entity.repeatType,
                lastCompleted = entity.lastCompleted
            )
        }
        val allGroups = groupDao.getAllGroupsOnce().map { entity ->
            TodoGroup(
                id = entity.id,
                title = entity.title,
                isDone = entity.isDone,
                priority = entity.priority,
                deadline = entity.deadline,
                reminderMinutes = entity.reminderMinutes
            )
        }
        val now = System.currentTimeMillis()
        val cutoff = if (days > 0) now - (days * 86_400_000L) else 0L

        val completedItems = allItems.filter { it.isDone || isCompletedToday(it) }
        val filteredItems = if (days > 0) allItems.filter { it.lastCompleted != null && it.lastCompleted!! >= cutoff } else allItems

        val dailyMap = mutableMapOf<String, Int>()
        val cal = Calendar.getInstance()
        filteredItems.forEach { item ->
            item.lastCompleted?.let { ts ->
                cal.timeInMillis = ts
                val key = "${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
                dailyMap[key] = (dailyMap[key] ?: 0) + 1
            }
        }

        val dailyCompletions = if (days > 0) {
            val list = mutableListOf<DailyCompletion>()
            val c = Calendar.getInstance()
            repeat(days) { i ->
                c.timeInMillis = now - (i * 86_400_000L)
                val key = "${c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}/${(c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
                list.add(0, DailyCompletion(key, dailyMap[key] ?: 0))
            }
            list
        } else {
            dailyMap.map { DailyCompletion(it.key, it.value) }.sortedBy { it.day }
        }

        val categoryStats = allGroups.map { group ->
            val groupItems = allItems.filter { it.groupId == group.id }
            val completed = groupItems.count { it.isDone || isCompletedToday(it) }
            CategoryStats(
                name = group.title,
                total = groupItems.size,
                completed = completed,
                rate = if (groupItems.isNotEmpty()) completed.toFloat() / groupItems.size else 0f
            )
        }

        val overdueTasks = allItems.count { item ->
            !item.isDone && !isCompletedToday(item) && item.deadline != null && item.deadline!! < now
        }

        return DashboardStats(
            totalTasks = allItems.size,
            completedTasks = completedItems.size,
            pendingTasks = allItems.size - completedItems.size,
            overdueTasks = overdueTasks,
            completionRate = if (allItems.isNotEmpty()) completedItems.size.toFloat() / allItems.size else 0f,
            dailyCompletions = dailyCompletions,
            categoryStats = categoryStats,
            bestCategory = categoryStats.filter { it.total > 0 }.maxByOrNull { it.rate },
            worstCategory = categoryStats.filter { it.total > 0 }.minByOrNull { it.rate },
            recurringActive = allItems.count { it.repeatType != null },
            tasksWithDeadline = allItems.count { it.deadline != null }
        )
    }

    private fun isCompletedToday(item: TodoItem): Boolean {
        if (item.lastCompleted == null) return false
        val today = Calendar.getInstance()
        val last = Calendar.getInstance().apply { timeInMillis = item.lastCompleted!! }
        return last.get(Calendar.YEAR) == today.get(Calendar.YEAR) && last.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }
}
