package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.TaskInfoEntity

@Dao
interface TaskInfoDao {
    @Query("SELECT * FROM task_info ORDER BY quest_number")
    suspend fun getAllTaskInfo(): List<TaskInfoEntity>

    @Query("SELECT * FROM task_info WHERE quest_number = :questNumber LIMIT 1")
    suspend fun getTaskInfo(questNumber: Int): TaskInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskInfo(task: TaskInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTaskInfo(tasks: List<TaskInfoEntity>)
}
