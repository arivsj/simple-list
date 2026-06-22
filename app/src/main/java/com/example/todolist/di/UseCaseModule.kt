package com.example.todolist.di

import com.example.todolist.domain.repository.TodoGroupRepository
import com.example.todolist.domain.repository.TodoRepository
import com.example.todolist.domain.useCase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetAllTodosUseCase(repository: TodoRepository): GetAllTodosUseCase {
        return GetAllTodosUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideAddTodoUseCase(repository: TodoRepository): AddTodoUseCase {
        return AddTodoUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideUpdateTodoUseCase(repository: TodoRepository): UpdateTodoUseCase {
        return UpdateTodoUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDeleteTodoUseCase(repository: TodoRepository): DeleteTodoUseCase {
        return DeleteTodoUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetTodoByIdUseCase(repository: TodoRepository): GetTodoByIdUseCase {
        return GetTodoByIdUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideReorderTodosUseCase(repository: TodoRepository): ReorderTodosUseCase {
        return ReorderTodosUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetAllGroupsUseCase(repository: TodoGroupRepository): GetAllGroupsUseCase {
        return GetAllGroupsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideAddGroupUseCase(repository: TodoGroupRepository): AddGroupUseCase {
        return AddGroupUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideUpdateGroupUseCase(repository: TodoGroupRepository): UpdateGroupUseCase {
        return UpdateGroupUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDeleteGroupUseCase(repository: TodoGroupRepository): DeleteGroupUseCase {
        return DeleteGroupUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideReorderGroupsUseCase(repository: TodoGroupRepository): ReorderGroupsUseCase {
        return ReorderGroupsUseCase(repository)
    }
}
