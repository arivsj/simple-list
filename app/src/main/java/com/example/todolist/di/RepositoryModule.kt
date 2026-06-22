package com.example.todolist.di

import com.example.todolist.data.repository.TodoGroupRepositoryImpl
import com.example.todolist.data.repository.TodoRepositoryImpl
import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.domain.repository.TodoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTodoRepository(
        todoRepositoryImpl: TodoRepositoryImpl
    ): TodoRepository

    @Binds
    @Singleton
    abstract fun bindTodoGroupRepository(
        todoGroupRepositoryImpl: TodoGroupRepositoryImpl
    ): TodoGroupRepository
}
