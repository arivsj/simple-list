package com.example.todolist.data.dataSource.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY priority DESC, id DESC")
    fun getAllItems(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items ORDER BY priority DESC, id DESC")
    suspend fun getAllItemsOnce(): List<TodoEntity>

    @Query("SELECT * FROM todo_items WHERE groupId IS NULL ORDER BY priority DESC, id DESC")
    fun getStandaloneItems(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items WHERE groupId = :groupId ORDER BY priority DESC, id DESC")
    fun getItemsByGroupId(groupId: Long): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TodoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TodoEntity>)

    @Update
    suspend fun updateItem(item: TodoEntity)

    @Update
    suspend fun updateItems(items: List<TodoEntity>)

    @Delete
    suspend fun deleteItem(item: TodoEntity)

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getItemById(id: Long): TodoEntity?
}
