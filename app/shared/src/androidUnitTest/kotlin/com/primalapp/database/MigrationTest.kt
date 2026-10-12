package com.primalapp.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_DB = "migration-test.db"

/**
 * Тесты миграций БД Room (Robolectric + MigrationTestHelper).
 * Покрывают задачи testTasks.md 24.2 и 27.2.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PrimalDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun SupportSQLiteDatabase.queryInt(sql: String): Int =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.bossNameCount(): Int =
        queryInt("SELECT COUNT(DISTINCT name) FROM bosses")

    private fun SupportSQLiteDatabase.isColumnNull(sql: String): Boolean =
        query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.isNull(0) else false
        }

    //region 24.2. Миграция 5→6→7→8→9 (регресс SQL-ошибки NOT NULL)

    @Test
    fun `миграция с v5 до v9 выполняется без ошибок и bosses содержит 23 босса`() {
        // Подготовка: создаём БД на версии 5 (схема v5 из assets, dfw NOT NULL — как до фикса 24.1)
        helper.createDatabase(TEST_DB, 5).close()

        // Вызов проверяемого кода: последовательно применяем миграции до v9
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

        // Проверка: после миграции в bosses 23 босса (общий seedBosses, задача 40.1),
        // включая Коровона с NULL dfw на Ст2
        val bossCount = migrated.bossNameCount()
        assertEquals(23, bossCount, "Таблица bosses должна содержать 23 босса")
        val korovonNullDfw = migrated.isColumnNull(
            "SELECT stance2_dfw FROM bosses WHERE name = 'Коровон' LIMIT 1"
        )
        assertTrue(korovonNullDfw, "Коровон должен иметь stance2_dfw = NULL")
        migrated.close()
    }

    @Test
    fun `миграция с v4 до v9 выполняется без ошибок`() {
        // Подготовка: создаём БД на версии 4 (схема v4 из assets, element NOT NULL)
        helper.createDatabase(TEST_DB, 4).close()

        // Вызов проверяемого кода: применяем цепочку миграций 4→5→6→7→8→9
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

        // Проверка: миграция прошла без исключений, bosses содержит 23 босса
        val bossCount = migrated.bossNameCount()
        assertEquals(23, bossCount, "Таблица bosses должна содержать 23 босса")
        migrated.close()
    }

    @Test
    fun `миграция с v5 до v9 создаёт уникальный индекс quests`() {
        // Подготовка: БД на версии 5
        helper.createDatabase(TEST_DB, 5).close()

        // Вызов проверяемого кода: миграции до v9 (MIGRATION_8_9 добавляет уникальный индекс)
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

        // Проверка: уникальный индекс quests(campaign_id, quest_id) существует
        val indexCount = migrated.queryInt(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'index' AND name = 'index_quests_campaign_id_quest_id'"
        )
        assertEquals(1, indexCount, "После миграции 8→9 должен существовать уникальный индекс заданий")
        migrated.close()
    }

    //endregion

    //region 42.1. Миграция 13→14: канонические названия достижений

    private fun SupportSQLiteDatabase.queryStrings(sql: String): List<String> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    @Test
    fun `миграция 13→14 приводит достижения к каноническому написанию и убирает дубли`() {
        // Подготовка: БД версии 13, кампания с достижениями в прежнем написании каталога заданий и дублем
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO campaigns (id, name, current_chapter, forge_level, lab_level, notes, created_at, updated_at) " +
                    "VALUES (1, 'Тест', 4, 1, 1, '', 0, 0)"
            )
            listOf("Яд пазиса", "Народ золотых гор", "Хозяйка фонаря", "Хозяйка фонаря").forEach { name ->
                execSQL(
                    "INSERT INTO achievements (campaign_id, achievement_id, name, description, unlocked) " +
                        "VALUES (1, '$name', '$name', '', 1)"
                )
            }
            close()
        }

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Проверка: написание совпадает с условиями каталога глав, дубль удалён
        assertEquals(
            listOf("Яд Пазиса", "Народ Золотых гор", "Хозяйка фонаря"),
            migrated.queryStrings("SELECT name FROM achievements WHERE campaign_id = 1 ORDER BY id"),
            "Достижения должны быть в каноническом написании и без дублей"
        )
        assertEquals(
            listOf("Яд Пазиса", "Народ Золотых гор"),
            migrated.queryStrings("SELECT achievement_id FROM achievements WHERE campaign_id = 1 AND achievement_id <> 'Хозяйка фонаря' ORDER BY id"),
            "achievement_id тоже должен быть в каноническом написании"
        )
        migrated.close()
    }

    @Test
    fun `миграция 13→14 пересевает каталог заданий с каноническими названиями достижений`() {
        // Подготовка: БД версии 13 (каталог заданий пуст — схема из assets без seed)
        helper.createDatabase(TEST_DB, 13).close()

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Проверка: 49 заданий, награды заданий 5, 18 и 36 — в каноническом написании
        assertEquals(49, migrated.queryInt("SELECT COUNT(*) FROM task_info"), "Каталог должен содержать 49 заданий")
        assertEquals(
            listOf("Пыль аркеума;Народ Золотых гор"),
            migrated.queryStrings("SELECT victory_achievements FROM task_info WHERE quest_number = 5")
        )
        assertEquals(
            listOf("Копьё драконоборца"),
            migrated.queryStrings("SELECT victory_achievements FROM task_info WHERE quest_number = 18")
        )
        assertEquals(
            listOf("Лагерь в джунглях;Яд Пазиса"),
            migrated.queryStrings("SELECT victory_achievements FROM task_info WHERE quest_number = 36")
        )
        migrated.close()
    }

    //endregion

    //region Миграция 14→15: решения defects.md (C-5 – C-9, R-6)

    @Test
    fun `миграция 14→15 добавляет колонки финала и условного улучшения набора в каталог глав`() {
        // Подготовка: БД версии 14
        helper.createDatabase(TEST_DB, 14).close()

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        // Проверка: главы 8 и 10 — улучшение набора при «Голос Волтьяра», глава 11 — финал
        assertEquals(11, migrated.queryInt("SELECT COUNT(*) FROM chapter_info"))
        assertEquals(
            listOf("Голос Волтьяра", "Голос Волтьяра"),
            migrated.queryStrings("SELECT hunter_kit_upgrade_achievement FROM chapter_info WHERE chapter IN (8, 10) ORDER BY chapter")
        )
        assertEquals(1, migrated.queryInt("SELECT expire_all_quests FROM chapter_info WHERE chapter = 11"))
        assertEquals(listOf("Пробуждённый"), migrated.queryStrings("SELECT final_boss FROM chapter_info WHERE chapter = 11"))
        assertEquals(1, migrated.queryInt("SELECT COUNT(*) FROM chapter_info WHERE final_boss <> ''"))
        migrated.close()
    }

    @Test
    fun `миграция 14→15 пересевает каталог заданий с исправленными наградами`() {
        // Подготовка: БД версии 14
        helper.createDatabase(TEST_DB, 14).close()

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        // Проверка: зад. 2 — ИРИДИЯ 1 и задание 5 при поражении в главе книги 1–2; зад. 28 — Иридия в материях
        assertEquals(49, migrated.queryInt("SELECT COUNT(*) FROM task_info"))
        assertEquals(
            listOf("BLOOD:2;ZIMIA:1;IRIDIA:1"),
            migrated.queryStrings("SELECT victory_materials FROM task_info WHERE quest_number = 2")
        )
        assertTrue(
            migrated.queryStrings("SELECT victory_materials FROM task_info WHERE quest_number = 28").single().contains("IRIDIA:3")
        )
        assertFalse(
            migrated.queryStrings("SELECT victory_plants FROM task_info WHERE quest_number = 28").single().contains("IRIDIA")
        )
        migrated.close()
    }

    @Test
    fun `цепочка миграций 9→17 проходит — ранние миграции сеют только существующие колонки`() {
        // Подготовка: БД версии 9 (до появления каталогов заданий и глав)
        helper.createDatabase(TEST_DB, 9).close()

        // Вызов проверяемого кода: 10→11 и 12→13 вызывают актуальный seed при старой схеме
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 17, true,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
            MIGRATION_15_16, MIGRATION_16_17
        )

        // Проверка: каталоги засеяны полностью, финал и условное улучшение набора на месте
        assertEquals(49, migrated.queryInt("SELECT COUNT(*) FROM task_info"))
        assertEquals(11, migrated.queryInt("SELECT COUNT(*) FROM chapter_info"))
        assertEquals(listOf("Пробуждённый"), migrated.queryStrings("SELECT final_boss FROM chapter_info WHERE chapter = 11"))
        migrated.close()
    }

    //endregion

    //region D-9. Миграция 15→16: уникальность достижения в кампании

    private fun SupportSQLiteDatabase.insertAchievementRow(campaignId: Long, name: String, orIgnore: Boolean = false) {
        execSQL(
            "INSERT ${if (orIgnore) "OR IGNORE " else ""}INTO achievements " +
                "(campaign_id, achievement_id, name, description, unlocked) VALUES ($campaignId, '$name', '$name', '', 1)"
        )
    }

    @Test
    fun `миграция 15→16 удаляет дубли достижений и создаёт уникальный индекс`() {
        // Подготовка: БД версии 15, две кампании; в первой «Затишье» записано дважды
        helper.createDatabase(TEST_DB, 15).apply {
            listOf(1, 2).forEach { id ->
                execSQL(
                    "INSERT INTO campaigns (id, name, current_chapter, forge_level, lab_level, notes, created_at, updated_at) " +
                        "VALUES ($id, 'Кампания $id', 2, 1, 1, '', 0, 0)"
                )
            }
            insertAchievementRow(1, "Затишье")
            insertAchievementRow(1, "Гербарий")
            insertAchievementRow(1, "Затишье")
            insertAchievementRow(2, "Затишье")
            close()
        }

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        // Проверка: дубль удалён (осталась первая запись), одинаковое достижение в другой кампании сохранено
        assertEquals(
            listOf("Затишье", "Гербарий"),
            migrated.queryStrings("SELECT achievement_id FROM achievements WHERE campaign_id = 1 ORDER BY id")
        )
        assertEquals(1, migrated.queryInt("SELECT COUNT(*) FROM achievements WHERE campaign_id = 2"))
        assertEquals(
            1,
            migrated.queryInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_achievements_campaign_id_achievement_id'"
            )
        )
        migrated.close()
    }

    @Test
    fun `после миграции 15→16 повторное достижение кампании не записывается`() {
        // Подготовка
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO campaigns (id, name, current_chapter, forge_level, lab_level, notes, created_at, updated_at) " +
                    "VALUES (1, 'Тест', 2, 1, 1, '', 0, 0)"
            )
            insertAchievementRow(1, "Затишье")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        // Вызов проверяемого кода: INSERT OR IGNORE (как DAO с OnConflictStrategy.IGNORE) и обычный INSERT
        migrated.insertAchievementRow(1, "Затишье", orIgnore = true)
        val plainInsert = runCatching { migrated.insertAchievementRow(1, "Затишье") }

        // Проверка: дубль не записан, обычная вставка нарушает уникальность
        assertEquals(1, migrated.queryInt("SELECT COUNT(*) FROM achievements WHERE campaign_id = 1"))
        assertTrue(plainInsert.isFailure, "Уникальный индекс должен запрещать дубль достижения")
        migrated.close()
    }

    //endregion

    //region Миграция 16→17: формулировка решения главы 7

    @Test
    fun `миграция 16→17 пересевает решение главы 7 с новой формулировкой`() {
        // Подготовка: БД версии 16 с прежней формулировкой решения
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                "INSERT INTO chapter_info (chapter, rewards, reward_plants, open_quests, conditional_open_quests, expire_quests, " +
                    "forge_upgrade, lab_upgrade, hunter_kit_upgrade, decisions, messages, conditional_messages, " +
                    "hunter_kit_upgrade_achievement, expire_all_quests, final_boss) " +
                    "VALUES (7, '', '', '', '', '', 0, 0, 1, 'Тренироваться у подножья Волтьяра?Да|Нет?Голос Волтьяра?Да', '', '', '', 0, '')"
            )
            close()
        }

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        // Проверка
        assertEquals(
            listOf("Хотите ли вы тренироваться в лагере у Волтьяра?Да|Нет?Голос Волтьяра?Да"),
            migrated.queryStrings("SELECT decisions FROM chapter_info WHERE chapter = 7")
        )
        assertEquals(11, migrated.queryInt("SELECT COUNT(*) FROM chapter_info"))
        migrated.close()
    }

    //endregion

    //region Миграция 17→18: кампании — документами, справочники — из файла приложения

    @Test
    fun `миграция 17→18 переносит кампанию в новый формат с книжной главой и кодами каталога`() {
        // Подготовка: БД версии 17 — кампания в главе 3 приложения (глава 2 книги), охотник с навыками и ресурсами,
        // задания (открытое, выполненное, истёкшее), достижение в старом написании и трофеи
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO campaigns (id, name, current_chapter, forge_level, lab_level, notes, created_at, updated_at) " +
                    "VALUES (1, 'Старая', 3, 2, 1, 'заметка', 100, 200)"
            )
            execSQL("INSERT INTO hunters (id, campaign_id, player_name, class_name) VALUES (10, 1, 'Иван', 'DAREON')")
            execSQL("INSERT INTO hunters (id, campaign_id, player_name, class_name) VALUES (11, 1, 'Мира', 'MIRA')")
            execSQL("INSERT INTO skills (hunter_id, branch, tier, unlocked) VALUES (10, 'A', 1, 1)")
            execSQL("INSERT INTO skills (hunter_id, branch, tier, unlocked) VALUES (10, 'A', 2, 1)")
            execSQL("INSERT INTO skills (hunter_id, branch, tier, unlocked) VALUES (10, 'B', 1, 0)")
            execSQL("INSERT INTO resources (hunter_id, resource_type, resource_name, quantity) VALUES (10, 'MATERIAL', 'BONES', 3)")
            execSQL("INSERT INTO resources (hunter_id, resource_type, resource_name, quantity) VALUES (10, 'ELEMENT', 'FIRE', 2)")
            execSQL(
                "INSERT INTO quests (campaign_id, quest_id, name, chapter, quest_number, is_completed, is_available) VALUES " +
                    "(1, '1', 'Память пустыни', 2, 1, 1, 1), (1, '4', 'Вожак стаи', 3, 4, 0, 1), (1, '3', 'Рёв моря', 2, 3, 0, 0)"
            )
            execSQL("INSERT INTO achievements (campaign_id, achievement_id, name, description, unlocked) VALUES (1, 'яд  пазиса', 'яд  пазиса', '', 1)")
            execSQL("INSERT INTO achievements (campaign_id, achievement_id, name, description, unlocked) VALUES (1, 'Своё', 'Своё', '', 1)")
            execSQL("INSERT INTO trophies (campaign_id, boss_name, element, chapter, acquired_at) VALUES (1, 'Вираксен', 'FIRE', 1, 0)")
            execSQL("INSERT INTO trophies (campaign_id, boss_name, element, chapter, acquired_at) VALUES (1, 'Торамат', 'HORN', 2, 0)")
            close()
        }

        // Вызов проверяемого кода
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)

        // Проверка: старые таблицы удалены, кампания — документом «только на устройстве»
        assertEquals(1, migrated.queryInt("SELECT COUNT(*) FROM campaign_store WHERE access = 'LOCAL'"))
        assertEquals(0, migrated.queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('campaigns', 'bosses', 'task_info')"))
        val json = migrated.queryStrings("SELECT state_json FROM campaign_store").single()
        val state = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(com.primalapp.campaign.CampaignState.serializer(), json)
        assertEquals("Старая", state.name)
        assertEquals(2, state.chapter, "Глава книги = глава приложения − 1")
        assertEquals(2, state.forgeLevel)
        assertEquals("заметка", state.notes)
        assertEquals(listOf("Иван", "Мира"), state.squad.map { it.playerName })
        val dareon = state.squad.first()
        assertEquals(2, dareon.skills.size, "Открытые ступени: А1 и А2")
        assertEquals(3, dareon.resources[com.primalapp.model.campaign.ResourceCode.BONES])
        assertEquals(
            listOf(1 to com.primalapp.campaign.QuestStatus.COMPLETED, 3 to com.primalapp.campaign.QuestStatus.EXPIRED,
                4 to com.primalapp.campaign.QuestStatus.OPEN),
            state.quests.map { it.number to it.status }
        )
        assertEquals(listOf("YAD_PAZISA" to "Яд Пазиса", null to "Своё"), state.achievements.map { it.code to it.name })
        assertEquals(listOf("VIRAXEN" to 0, "TORAMAT" to 1), state.trophies.map { it.bossCode to it.chapter })
        migrated.close()
    }

    @Test
    fun `чистая установка версии 18 — пустые кампании и настройки`() {
        // Подготовка
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createPrimalDatabase(PlatformContext(context))

        // Вызов проверяемого кода
        val campaigns = runBlocking { db.campaignStoreDao().all() }
        val setting = runBlocking { db.keyValueDao().get("mode") }

        // Проверка
        assertTrue(campaigns.isEmpty())
        assertNull(setting)
        db.close()
    }

    //endregion
}
