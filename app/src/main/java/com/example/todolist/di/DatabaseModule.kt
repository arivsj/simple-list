package com.example.todolist.di

import android.content.Context
import androidx.room.Room
import com.example.todolist.data.dataSource.local.TodoDao
import com.example.todolist.data.dataSource.local.TodoDatabase
import com.example.todolist.data.dataSource.local.TodoGroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TodoDatabase {
        return Room.databaseBuilder(
            context,
            TodoDatabase::class.java,
            "todo_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideTodoDao(database: TodoDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    fun provideTodoGroupDao(database: TodoDatabase): TodoGroupDao {
        return database.todoGroupDao()
    }
}
