package com.primalapp.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.primalapp.database.dao.AchievementDao
import com.primalapp.database.dao.BossDao
import com.primalapp.database.dao.CampaignDao
import com.primalapp.database.dao.ChapterInfoDao
import com.primalapp.database.dao.HunterDao
import com.primalapp.database.dao.QuestDao
import com.primalapp.database.dao.ResourceDao
import com.primalapp.database.dao.SkillDao
import com.primalapp.database.dao.TaskInfoDao
import com.primalapp.database.dao.TrophyDao
import com.primalapp.database.entity.AchievementEntity
import com.primalapp.database.entity.BossEntity
import com.primalapp.database.entity.CampaignEntity
import com.primalapp.database.entity.ChapterInfoEntity
import com.primalapp.database.entity.HunterEntity
import com.primalapp.database.entity.QuestEntity
import com.primalapp.database.entity.ResourceEntity
import com.primalapp.database.entity.SkillEntity
import com.primalapp.database.entity.TaskInfoEntity
import com.primalapp.database.entity.TrophyEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE quests ADD COLUMN quest_number INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bosses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                element TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                stance1_dfw INTEGER NOT NULL,
                stance1_hsc INTEGER NOT NULL,
                stance2_dfw INTEGER NOT NULL,
                stance2_hsc INTEGER NOT NULL,
                stance3_dfw INTEGER NOT NULL,
                stance3_hsc INTEGER NOT NULL,
                stance4_dfw INTEGER NOT NULL DEFAULT 0,
                stance4_hsc INTEGER NOT NULL DEFAULT 0,
                stance5_dfw INTEGER NOT NULL DEFAULT 0,
                stance5_hsc INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 0, 2, 7, 3, 3, 4, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 1, 5, 7, 7, 3, 10, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 2, 10, 7, 15, 3, 20, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 3, 18, 7, 24, 3, 30, 0)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("DROP TABLE IF EXISTS bosses")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bosses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                element TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                stance1_dfw INTEGER NOT NULL,
                stance1_hsc INTEGER,
                stance2_dfw INTEGER NOT NULL,
                stance2_hsc INTEGER,
                stance3_dfw INTEGER NOT NULL,
                stance3_hsc INTEGER,
                stance4_dfw INTEGER NOT NULL DEFAULT 0,
                stance4_hsc INTEGER,
                stance5_dfw INTEGER NOT NULL DEFAULT 0,
                stance5_hsc INTEGER
            )
        """.trimIndent())
        // Вираксен (Огонь) — смена стойки по здоровью
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 0, 2, 7, 3, 3, 4, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 1, 5, 7, 7, 3, 10, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 2, 10, 7, 15, 3, 20, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 3, 18, 7, 24, 3, 30, 0)")
        // Иекорос (Молния) — смена стойки по запросу (hsc = NULL)
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 0, 2, NULL, 4, NULL, 5, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 1, 7, NULL, 8, NULL, 12, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 2, 12, NULL, 17, NULL, 22, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 3, 20, NULL, 25, NULL, 30, NULL)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SQLiteConnection) {
        // bosses — nullable element
        db.execSQL("DROP TABLE IF EXISTS bosses")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bosses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                element TEXT,
                difficulty INTEGER NOT NULL,
                stance1_dfw INTEGER NOT NULL,
                stance1_hsc INTEGER,
                stance2_dfw INTEGER NOT NULL,
                stance2_hsc INTEGER,
                stance3_dfw INTEGER NOT NULL,
                stance3_hsc INTEGER,
                stance4_dfw INTEGER NOT NULL DEFAULT 0,
                stance4_hsc INTEGER,
                stance5_dfw INTEGER NOT NULL DEFAULT 0,
                stance5_hsc INTEGER
            )
        """.trimIndent())
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 0, 2, 7, 3, 3, 4, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 1, 5, 7, 7, 3, 10, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 2, 10, 7, 15, 3, 20, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Вираксен', 'FIRE', 3, 18, 7, 24, 3, 30, 0)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 0, 2, NULL, 4, NULL, 5, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 1, 7, NULL, 8, NULL, 12, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 2, 12, NULL, 17, NULL, 22, NULL)")
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc) VALUES ('Иекорос', 'LIGHTNING', 3, 20, NULL, 25, NULL, 30, NULL)")
        // Пробуждённый — без стихии, только сложность 3, 5 стоек
        db.execSQL("INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance4_hsc, stance5_dfw, stance5_hsc) VALUES ('Пробуждённый', NULL, 3, 30, 8, 40, 6, 50, 4, 60, 2, 60, 0)")

        // trophies — nullable element (пересоздание с сохранением данных)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS trophies_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                campaign_id INTEGER NOT NULL,
                boss_name TEXT NOT NULL,
                element TEXT,
                chapter INTEGER NOT NULL,
                acquired_at INTEGER NOT NULL,
                FOREIGN KEY(campaign_id) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("INSERT INTO trophies_new (id, campaign_id, boss_name, element, chapter, acquired_at) SELECT id, campaign_id, boss_name, element, chapter, acquired_at FROM trophies")
        db.execSQL("DROP TABLE trophies")
        db.execSQL("ALTER TABLE trophies_new RENAME TO trophies")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_trophies_campaign_id ON trophies (campaign_id)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SQLiteConnection) {
        recreateAndSeedBosses(db)
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SQLiteConnection) {
        recreateAndSeedBosses(db)
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SQLiteConnection) {
        recreateAndSeedBosses(db)
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            "DELETE FROM quests WHERE id NOT IN " +
                "(SELECT MIN(id) FROM quests GROUP BY campaign_id, quest_id)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_quests_campaign_id_quest_id " +
                "ON quests (campaign_id, quest_id)"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_info (
                quest_number INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                boss_name TEXT NOT NULL,
                boss_element TEXT,
                victory_materials TEXT NOT NULL DEFAULT '',
                victory_plants TEXT NOT NULL DEFAULT '',
                victory_open_quests TEXT NOT NULL DEFAULT '',
                victory_achievements TEXT NOT NULL DEFAULT '',
                victory_reward_cards TEXT NOT NULL DEFAULT '',
                victory_special TEXT NOT NULL DEFAULT '',
                defeat_open_quests TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE task_info ADD COLUMN victory_open_quest_conditions TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE task_info ADD COLUMN defeat_open_quest_conditions TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chapter_info (
                chapter INTEGER PRIMARY KEY NOT NULL,
                rewards TEXT NOT NULL DEFAULT '',
                reward_plants TEXT NOT NULL DEFAULT '',
                open_quests TEXT NOT NULL DEFAULT '',
                conditional_open_quests TEXT NOT NULL DEFAULT '',
                expire_quests TEXT NOT NULL DEFAULT '',
                forge_upgrade INTEGER NOT NULL DEFAULT 0,
                lab_upgrade INTEGER NOT NULL DEFAULT 0,
                hunter_kit_upgrade INTEGER NOT NULL DEFAULT 0,
                decisions TEXT NOT NULL DEFAULT '',
                messages TEXT NOT NULL DEFAULT '',
                conditional_messages TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("DELETE FROM task_info")
        seedTaskInfo(db)
        seedChapterInfo(db)
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SQLiteConnection) {
        // Схема не меняется; пере-сид каталога заданий с расширенной моделью условий
        // (составное условие зад. 25, условные достижения зад. 29/40).
        db.execSQL("DELETE FROM task_info")
        seedTaskInfo(db)
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE task_info ADD COLUMN defeat_achievements TEXT NOT NULL DEFAULT ''")
        db.execSQL("DELETE FROM task_info")
        seedTaskInfo(db)
        db.execSQL("DELETE FROM chapter_info")
        seedChapterInfo(db)
        recreateAndSeedBosses(db)
    }
}

private val CREATE_BOSSES_TABLE = """
    CREATE TABLE IF NOT EXISTS bosses (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        element TEXT,
        difficulty INTEGER NOT NULL,
        stance1_dfw INTEGER,
        stance1_hsc INTEGER,
        stance2_dfw INTEGER,
        stance2_hsc INTEGER,
        stance3_dfw INTEGER,
        stance3_hsc INTEGER,
        stance4_dfw INTEGER NOT NULL DEFAULT 0,
        stance4_hsc INTEGER,
        stance5_dfw INTEGER NOT NULL DEFAULT 0,
        stance5_hsc INTEGER
    )
""".trimIndent()

private fun recreateAndSeedBosses(db: SQLiteConnection) {
    db.execSQL("DROP TABLE IF EXISTS bosses")
    db.execSQL(CREATE_BOSSES_TABLE)
    seedBosses(db)
}

fun seedBosses(db: SQLiteConnection) {
    fun insert3(
        name: String,
        element: String,
        difficulty: Int,
        s1dfw: Int,
        s1hsc: Int,
        s2dfw: Int,
        s2hsc: Int,
        s3dfw: Int,
        s3hsc: Int
    ) {
        db.execSQL(
            "INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance5_dfw) " +
                "VALUES ('$name', '$element', $difficulty, $s1dfw, $s1hsc, $s2dfw, $s2hsc, $s3dfw, $s3hsc, 0, 0)"
        )
    }

    fun insert3NullHsc(name: String, element: String, difficulty: Int, s1dfw: Int, s2dfw: Int, s3dfw: Int) {
        db.execSQL(
            "INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance5_dfw) " +
                "VALUES ('$name', '$element', $difficulty, $s1dfw, NULL, $s2dfw, NULL, $s3dfw, NULL, 0, 0)"
        )
    }

    fun insert3NullDfw(name: String, element: String, difficulty: Int, s1dfw: Int, s1hsc: Int, s3dfw: Int, s3hsc: Int) {
        db.execSQL(
            "INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance5_dfw) " +
                "VALUES ('$name', '$element', $difficulty, $s1dfw, $s1hsc, NULL, NULL, $s3dfw, $s3hsc, 0, 0)"
        )
    }

    fun insert3NullHsc2(name: String, element: String, difficulty: Int, s1dfw: Int, s1hsc: Int, s2dfw: Int, s3dfw: Int, s3hsc: Int) {
        db.execSQL(
            "INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance5_dfw) " +
                "VALUES ('$name', '$element', $difficulty, $s1dfw, $s1hsc, $s2dfw, NULL, $s3dfw, $s3hsc, 0, 0)"
        )
    }

    // Вираксен (Огонь)
    insert3("Вираксен", "FIRE", 0, 2, 7, 3, 3, 4, 0)
    insert3("Вираксен", "FIRE", 1, 5, 7, 7, 3, 10, 0)
    insert3("Вираксен", "FIRE", 2, 10, 7, 15, 3, 20, 0)
    insert3("Вираксен", "FIRE", 3, 18, 7, 24, 3, 30, 0)

    // Иекорос (Молния) — смена стойки по запросу (hsc = NULL)
    insert3NullHsc("Иекорос", "LIGHTNING", 0, 2, 4, 5)
    insert3NullHsc("Иекорос", "LIGHTNING", 1, 7, 8, 12)
    insert3NullHsc("Иекорос", "LIGHTNING", 2, 12, 17, 22)
    insert3NullHsc("Иекорос", "LIGHTNING", 3, 20, 25, 30)

    // Пробуждённый — без стихии, только сложность 3, 5 стоек
    db.execSQL(
        "INSERT INTO bosses (name, element, difficulty, stance1_dfw, stance1_hsc, stance2_dfw, stance2_hsc, stance3_dfw, stance3_hsc, stance4_dfw, stance4_hsc, stance5_dfw, stance5_hsc) " +
            "VALUES ('Пробуждённый', NULL, 3, 30, 8, 40, 6, 50, 4, 60, 2, 60, 0)"
    )

    // Торамат (Рог)
    insert3("Торамат", "HORN", 0, 2, 7, 3, 4, 3, 0)
    insert3("Торамат", "HORN", 1, 4, 7, 6, 4, 9, 0)
    insert3("Торамат", "HORN", 2, 10, 7, 16, 4, 20, 0)
    insert3("Торамат", "HORN", 3, 18, 7, 25, 4, 30, 0)

    // Юром (Металл)
    insert3("Юром", "METAL", 0, 2, 6, 2, 3, 3, 0)
    insert3("Юром", "METAL", 1, 4, 6, 6, 3, 7, 0)
    insert3("Юром", "METAL", 2, 9, 6, 14, 3, 17, 0)
    insert3("Юром", "METAL", 3, 15, 7, 20, 3, 25, 0)

    // Озев (Молния)
    insert3("Озев", "LIGHTNING", 0, 3, 8, 2, 3, 2, 0)
    insert3("Озев", "LIGHTNING", 1, 7, 7, 5, 3, 3, 0)
    insert3("Озев", "LIGHTNING", 2, 16, 7, 12, 3, 9, 0)
    insert3("Озев", "LIGHTNING", 3, 25, 7, 18, 3, 15, 0)

    // Моркраас (Кристалл)
    insert3("Моркраас", "CRYSTAL", 0, 5, 6, 3, 3, 2, 0)
    insert3("Моркраас", "CRYSTAL", 1, 10, 6, 8, 3, 6, 0)
    insert3("Моркраас", "CRYSTAL", 2, 20, 6, 16, 3, 14, 0)
    insert3("Моркраас", "CRYSTAL", 3, 30, 6, 25, 6, 20, 0)

    // Дигоракс (Рог)
    insert3("Дигоракс", "HORN", 0, 2, 8, 3, 4, 4, 0)
    insert3("Дигоракс", "HORN", 1, 5, 8, 7, 4, 10, 0)
    insert3("Дигоракс", "HORN", 2, 9, 8, 14, 4, 18, 0)
    insert3("Дигоракс", "HORN", 3, 15, 8, 20, 4, 25, 0)

    // Харджа (Огонь)
    insert3("Харджа", "FIRE", 0, 2, 6, 3, 2, 5, 0)
    insert3("Харджа", "FIRE", 1, 5, 6, 7, 2, 12, 0)
    insert3("Харджа", "FIRE", 2, 10, 7, 16, 2, 20, 0)
    insert3("Харджа", "FIRE", 3, 15, 7, 25, 2, 30, 0)

    // Коровон (Коралл) — 2-я стойка без порога раны (dfw = NULL)
    insert3NullDfw("Коровон", "CORAL", 0, 2, 6, 4, 0)
    insert3NullDfw("Коровон", "CORAL", 1, 6, 6, 8, 0)
    insert3NullDfw("Коровон", "CORAL", 2, 13, 6, 16, 0)
    insert3NullDfw("Коровон", "CORAL", 3, 20, 6, 25, 0)

    // Таррагуа (Металл)
    insert3("Таррагуа", "METAL", 0, 2, 6, 3, 3, 4, 0)
    insert3("Таррагуа", "METAL", 1, 6, 6, 7, 3, 8, 0)
    insert3("Таррагуа", "METAL", 2, 10, 6, 14, 3, 18, 0)
    insert3("Таррагуа", "METAL", 3, 16, 6, 18, 3, 22, 0)

    // Фелаксир (Кристалл)
    insert3("Фелаксир", "CRYSTAL", 0, 2, 7, 3, 3, 4, 0)
    insert3("Фелаксир", "CRYSTAL", 1, 6, 7, 7, 3, 9, 0)
    insert3("Фелаксир", "CRYSTAL", 2, 12, 7, 14, 4, 20, 0)
    insert3("Фелаксир", "CRYSTAL", 3, 18, 7, 25, 4, 28, 0)

    // Оруксен (Коралл)
    insert3("Оруксен", "CORAL", 0, 2, 6, 3, 3, 4, 0)
    insert3("Оруксен", "CORAL", 1, 6, 6, 7, 3, 8, 0)
    insert3("Оруксен", "CORAL", 2, 10, 6, 15, 3, 20, 0)
    insert3("Оруксен", "CORAL", 3, 20, 6, 22, 3, 26, 0)

    // Пазис (Перо)
    insert3("Пазис", "FEATHER", 0, 2, 8, 2, 5, 3, 0)
    insert3("Пазис", "FEATHER", 1, 4, 7, 5, 4, 7, 0)
    insert3("Пазис", "FEATHER", 2, 10, 8, 13, 5, 17, 0)
    insert3("Пазис", "FEATHER", 3, 18, 8, 20, 5, 25, 0)

    // Нагарджас (Перо)
    insert3("Нагарджас", "FEATHER", 0, 4, 7, 4, 4, 5, 0)
    insert3("Нагарджас", "FEATHER", 1, 5, 7, 6, 4, 7, 0)
    insert3("Нагарджас", "FEATHER", 2, 11, 7, 15, 4, 16, 0)
    insert3("Нагарджас", "FEATHER", 3, 18, 7, 22, 4, 28, 0)

    // Зекалит (Молния)
    insert3("Зекалит", "LIGHTNING", 0, 2, 6, 3, 2, 3, 0)
    insert3("Зекалит", "LIGHTNING", 1, 4, 6, 7, 2, 8, 0)
    insert3("Зекалит", "LIGHTNING", 2, 10, 6, 16, 2, 18, 0)
    insert3("Зекалит", "LIGHTNING", 3, 15, 6, 24, 2, 28, 0)

    // Зекат (Молния) — идентичен Зекалиту
    insert3("Зекат", "LIGHTNING", 0, 2, 6, 3, 2, 3, 0)
    insert3("Зекат", "LIGHTNING", 1, 4, 6, 7, 2, 8, 0)
    insert3("Зекат", "LIGHTNING", 2, 10, 6, 16, 2, 18, 0)
    insert3("Зекат", "LIGHTNING", 3, 15, 6, 24, 2, 28, 0)

    // Тараск (Огонь)
    insert3("Тараск", "FIRE", 0, 5, 8, 3, 4, 3, 0)
    insert3("Тараск", "FIRE", 1, 9, 8, 8, 4, 7, 0)
    insert3("Тараск", "FIRE", 2, 20, 8, 16, 4, 14, 0)
    insert3("Тараск", "FIRE", 3, 28, 8, 25, 4, 22, 0)

    // Кситерос (Перо) — 2-я стойка: смена по запросу (hsc = NULL)
    insert3NullHsc2("Кситерос", "FEATHER", 0, 3, 7, 4, 5, 0)
    insert3NullHsc2("Кситерос", "FEATHER", 1, 7, 7, 8, 12, 0)
    insert3NullHsc2("Кситерос", "FEATHER", 2, 15, 7, 20, 25, 0)
    insert3NullHsc2("Кситерос", "FEATHER", 3, 20, 7, 25, 35, 0)

    // Гидар (Яд)
    insert3("Гидар", "POISON", 0, 2, 7, 3, 4, 3, 0)
    insert3("Гидар", "POISON", 1, 4, 7, 6, 4, 8, 0)
    insert3("Гидар", "POISON", 2, 12, 7, 15, 4, 18, 0)
    insert3("Гидар", "POISON", 3, 18, 7, 22, 4, 28, 0)

    // Рейкал (Яд)
    insert3("Рейкал", "POISON", 0, 2, 7, 3, 3, 5, 0)
    insert3("Рейкал", "POISON", 1, 6, 7, 8, 3, 10, 0)
    insert3("Рейкал", "POISON", 2, 12, 7, 16, 3, 20, 0)
    insert3("Рейкал", "POISON", 3, 18, 7, 22, 4, 30, 0)

    // Сиркаадж (Лёд)
    insert3("Сиркаадж", "ICE", 0, 2, 7, 3, 2, 3, 0)
    insert3("Сиркаадж", "ICE", 1, 5, 7, 6, 2, 9, 0)
    insert3("Сиркаадж", "ICE", 2, 13, 7, 16, 3, 20, 0)
    insert3("Сиркаадж", "ICE", 3, 22, 7, 25, 3, 28, 0)

    // Мумараак (Лёд)
    insert3("Мумараак", "ICE", 0, 3, 7, 4, 4, 5, 0)
    insert3("Мумараак", "ICE", 1, 6, 7, 8, 4, 10, 0)
    insert3("Мумараак", "ICE", 2, 12, 7, 18, 4, 20, 0)
    insert3("Мумараак", "ICE", 3, 20, 7, 25, 4, 30, 0)
}

@Database(
    entities = [
        CampaignEntity::class,
        HunterEntity::class,
        SkillEntity::class,
        ResourceEntity::class,
        AchievementEntity::class,
        TrophyEntity::class,
        QuestEntity::class,
        BossEntity::class,
        TaskInfoEntity::class,
        ChapterInfoEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class PrimalDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun hunterDao(): HunterDao
    abstract fun skillDao(): SkillDao
    abstract fun resourceDao(): ResourceDao
    abstract fun achievementDao(): AchievementDao
    abstract fun trophyDao(): TrophyDao
    abstract fun questDao(): QuestDao
    abstract fun bossDao(): BossDao
    abstract fun taskInfoDao(): TaskInfoDao
    abstract fun chapterInfoDao(): ChapterInfoDao
}
