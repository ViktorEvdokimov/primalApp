package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.ResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources WHERE hunter_id = :hunterId")
    fun getResources(hunterId: Long): Flow<List<ResourceEntity>>

    @Query("SELECT * FROM resources WHERE hunter_id = :hunterId")
    suspend fun getResourcesList(hunterId: Long): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE hunter_id = :hunterId AND resource_type = :resourceType")
    suspend fun getResourcesByTypeList(hunterId: Long, resourceType: String): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE hunter_id = :hunterId AND resource_type = :resourceType AND resource_name = :resourceName")
    suspend fun getResource(hunterId: Long, resourceType: String, resourceName: String): ResourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: ResourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<ResourceEntity>)

    @Query("UPDATE resources SET quantity = :quantity WHERE hunter_id = :hunterId AND resource_type = :resourceType AND resource_name = :resourceName")
    suspend fun updateQuantity(hunterId: Long, resourceType: String, resourceName: String, quantity: Int)

    @Query("DELETE FROM resources WHERE hunter_id = :hunterId")
    suspend fun deleteResourcesByHunter(hunterId: Long)
}
