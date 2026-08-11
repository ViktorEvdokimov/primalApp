package com.primalapp.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.primalapp.database.dao.AchievementDao
import com.primalapp.database.dao.CampaignDao
import com.primalapp.database.dao.HunterDao
import com.primalapp.database.dao.QuestDao
import com.primalapp.database.dao.ResourceDao
import com.primalapp.database.dao.SkillDao
import com.primalapp.database.dao.TrophyDao
import com.primalapp.database.entity.AchievementEntity
import com.primalapp.database.entity.CampaignEntity
import com.primalapp.database.entity.HunterEntity
import com.primalapp.database.entity.QuestEntity
import com.primalapp.database.entity.ResourceEntity
import com.primalapp.database.entity.SkillEntity
import com.primalapp.database.entity.TrophyEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE quests ADD COLUMN quest_number INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        CampaignEntity::class,
        HunterEntity::class,
        SkillEntity::class,
        ResourceEntity::class,
        AchievementEntity::class,
        TrophyEntity::class,
        QuestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PrimalDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun hunterDao(): HunterDao
    abstract fun skillDao(): SkillDao
    abstract fun resourceDao(): ResourceDao
    abstract fun achievementDao(): AchievementDao
    abstract fun trophyDao(): TrophyDao
    abstract fun questDao(): QuestDao
}
