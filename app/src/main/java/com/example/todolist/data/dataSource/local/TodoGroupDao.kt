package com.example.todolist.data.dataSource.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoGroupDao {
    @Query("SELECT * FROM todo_groups ORDER BY priority DESC, id DESC")
    fun getAllGroups(): Flow<List<TodoGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TodoGroupEntity): Long

    @Update
    suspend fun updateGroup(group: TodoGroupEntity)

    @Update
    suspend fun updateGroups(groups: List<TodoGroupEntity>)

    @Delete
    suspend fun deleteGroup(group: TodoGroupEntity)

    @Query("SELECT * FROM todo_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): TodoGroupEntity?
}
