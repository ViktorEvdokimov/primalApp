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
    fun `миграция с v5 до v9 выполняется без ошибок и bosses содержит 19 боссов`() {
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

        // Проверка: после миграции в bosses 19 боссов, включая Коровона с NULL dfw на Ст2
        val bossCount = migrated.bossNameCount()
        assertEquals(19, bossCount, "Таблица bosses должна содержать 19 боссов")
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

        // Проверка: миграция прошла без исключений, bosses содержит 19 боссов
        val bossCount = migrated.bossNameCount()
        assertEquals(19, bossCount, "Таблица bosses должна содержать 19 боссов")
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

    //region 27.2. onCreate-seed на чистой установке (версия 9)

    @Test
    fun `onCreate seed на чистой установке содержит 19 боссов с NULL dfw и NULL hsc`() {
        // Подготовка: открываем реальную БД через билдер с onCreate-колбэком (seedBosses)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createPrimalDatabase(PlatformContext(context))
        val bossDao = db.bossDao()

        // Вызов проверяемого кода: читаем всех боссов из свежесозданной БД
        val bosses = runBlocking { bossDao.getAllBosses() }

        // Проверка: 19 уникальных имён боссов (73 строки: 18 боссов × 4 сложности + «Пробуждённый»),
        // включая Коровона (NULL dfw) и Кситероса (NULL hsc на Ст2)
        assertEquals(19, bosses.map { it.name }.distinct().size,
            "onCreate-seed должен создать 19 уникальных имён боссов")
        val korovon = bosses.firstOrNull { it.name == "Коровон" }
        assertTrue(korovon != null, "Коровон должен быть в seed")
        assertNull(korovon!!.stance2Dfw, "Коровон: stance2_dfw должен быть NULL")
        val ksiteros = bosses.firstOrNull { it.name == "Кситерос" }
        assertTrue(ksiteros != null, "Кситерос должен быть в seed")
        assertNull(ksiteros!!.stance2Hsc, "Кситерос: stance2_hsc (Ст2) должен быть NULL")
        db.close()
    }

    //endregion
}
