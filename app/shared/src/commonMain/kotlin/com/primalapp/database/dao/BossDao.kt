package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.BossEntity

@Dao
interface BossDao {
    @Query("SELECT * FROM bosses ORDER BY name, difficulty")
    suspend fun getAllBosses(): List<BossEntity>

    @Query("SELECT * FROM bosses WHERE difficulty = :difficulty ORDER BY name")
    suspend fun getBossesByDifficulty(difficulty: Int): List<BossEntity>

    @Query("SELECT * FROM bosses WHERE name = :name AND difficulty = :difficulty LIMIT 1")
    suspend fun getBossByNameDifficulty(name: String, difficulty: Int): BossEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoss(boss: BossEntity): Long
}
