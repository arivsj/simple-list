package com.example.todolist.data.dataSource.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TodoEntity::class, TodoGroupEntity::class], version = 2)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun todoGroupDao(): TodoGroupDao
}
