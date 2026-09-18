package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.ChapterInfoEntity

@Dao
interface ChapterInfoDao {
    @Query("SELECT * FROM chapter_info ORDER BY chapter")
    suspend fun getAllChapterInfo(): List<ChapterInfoEntity>

    @Query("SELECT * FROM chapter_info WHERE chapter = :chapter LIMIT 1")
    suspend fun getChapterInfo(chapter: Int): ChapterInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterInfo(chapter: ChapterInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChapterInfo(chapters: List<ChapterInfoEntity>)
}
