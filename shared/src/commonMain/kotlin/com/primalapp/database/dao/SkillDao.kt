package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills WHERE hunter_id = :hunterId")
    fun getSkills(hunterId: Long): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE hunter_id = :hunterId")
    suspend fun getSkillsList(hunterId: Long): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkills(skills: List<SkillEntity>)

    @Query("UPDATE skills SET unlocked = :unlocked WHERE hunter_id = :hunterId AND branch = :branch AND tier = :tier")
    suspend fun setUnlocked(hunterId: Long, branch: String, tier: Int, unlocked: Boolean)

    @Query("DELETE FROM skills WHERE hunter_id = :hunterId")
    suspend fun deleteSkillsByHunter(hunterId: Long)
}
