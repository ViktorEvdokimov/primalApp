package com.primalapp.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.primalapp.campaign.AchievementSource
import com.primalapp.campaign.AchievementState
import com.primalapp.campaign.CampaignState
import com.primalapp.campaign.CampaignStatus
import com.primalapp.campaign.HunterState
import com.primalapp.campaign.QuestState
import com.primalapp.campaign.QuestStatus
import com.primalapp.campaign.SkillStep
import com.primalapp.campaign.TrophyState
import com.primalapp.campaign.newLocalKey
import com.primalapp.domain.normalizeAchievementName
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import kotlinx.serialization.json.Json

internal val CREATE_CAMPAIGN_STORE = """
    CREATE TABLE IF NOT EXISTS `campaign_store` (`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `server_id` INTEGER, `account` TEXT, `access` TEXT NOT NULL, `owner_name` TEXT, `owner_expansions` TEXT,
    `base_version` INTEGER, `base_json` TEXT, `state_json` TEXT NOT NULL, `pending_ops` TEXT NOT NULL,
    `pending_delete` INTEGER NOT NULL, `upload` INTEGER NOT NULL, `sync_message` TEXT, `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL)
""".trimIndent().replace("\n", " ")

internal val CREATE_KV_STORE = "CREATE TABLE IF NOT EXISTS `kv_store` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"

internal val CREATE_CACHE_BLOB =
    "CREATE TABLE IF NOT EXISTS `cache_blob` (`key` TEXT NOT NULL, `etag` TEXT, `data` BLOB NOT NULL, PRIMARY KEY(`key`))"

private val storeJson = Json { encodeDefaults = true }

/**
 * Версия 1.3: кампании хранятся документами (как снимок сервера), справочники — из файла по умолчанию или с сервера.
 * Кампании старых версий переносятся: глава — книжная (`current_chapter − 1`), боссы трофеев и достижения — по
 * кодам каталога, задания — со статусом (открыто, выполнено, истекло). Старые таблицы удаляются.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(CREATE_CAMPAIGN_STORE)
        db.execSQL(CREATE_KV_STORE)
        db.execSQL(CREATE_CACHE_BLOB)
        legacyCampaigns(db).forEach { (state, times) ->
            db.prepare(
                "INSERT INTO campaign_store (access, state_json, pending_ops, pending_delete, upload, created_at, updated_at) " +
                    "VALUES ('LOCAL', ?, '[]', 0, 0, ?, ?)"
            ).use { statement ->
                statement.bindText(1, storeJson.encodeToString(CampaignState.serializer(), state))
                statement.bindLong(2, times.first)
                statement.bindLong(3, times.second)
                statement.step()
            }
        }
        listOf("resources", "skills", "achievements", "trophies", "quests", "hunters", "campaigns", "bosses", "task_info", "chapter_info")
            .forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
    }
}

/** Кампании старой схемы (версия 17) в новом формате; время создания и изменения. */
internal fun legacyCampaigns(db: SQLiteConnection): List<Pair<CampaignState, Pair<Long, Long>>> {
    val result = mutableListOf<Pair<CampaignState, Pair<Long, Long>>>()
    val campaigns = db.rows("SELECT id, name, current_chapter, forge_level, lab_level, notes, created_at, updated_at FROM campaigns") {
        listOf(it.getLong(0), it.getText(1), it.getLong(2), it.getLong(3), it.getLong(4), it.text(5), it.getLong(6), it.getLong(7))
    }
    for (row in campaigns) {
        val campaignId = row[0] as Long
        val chapter = ((row[2] as Long).toInt() - 1).coerceIn(0, 11)
        val hunters = db.rows("SELECT id, player_name, class_name FROM hunters WHERE campaign_id = $campaignId ORDER BY id") {
            Triple(it.getLong(0), it.getText(1), it.getText(2))
        }.mapNotNull { (hunterId, playerName, className) ->
            val hunterClass = HunterClass.entries.firstOrNull { it.name == className || it.displayName == className } ?: return@mapNotNull null
            hunterId to (hunterClass to playerName)
        }.distinctBy { it.second.first }
        val squad = hunters.mapIndexed { index, (hunterId, value) ->
            val skills = db.rows("SELECT branch, tier FROM skills WHERE hunter_id = $hunterId AND unlocked = 1") {
                it.getText(0) to it.getLong(1).toInt()
            }.mapNotNull { (branch, tier) ->
                SkillBranch.entries.firstOrNull { it.name == branch || it.letter == branch }?.let { SkillStep(it, tier) }
            }.filter { it.tier in 1..2 }.distinct()
            val validSkills = skills.filter { it.tier == 1 || SkillStep(it.branch, 1) in skills }
                .sortedWith(compareBy({ it.branch.ordinal }, { it.tier }))
            val stock = mutableMapOf<ResourceCode, Int>()
            db.rows("SELECT resource_name, quantity FROM resources WHERE hunter_id = $hunterId") { it.getText(0) to it.getLong(1).toInt() }
                .forEach { (name, quantity) ->
                    val code = ResourceCode.parse(name) ?: return@forEach
                    if (quantity > 0) stock[code] = (stock[code] ?: 0) + quantity
                }
            HunterState(
                hunterClass = value.first,
                playerName = value.second.ifBlank { value.first.displayName },
                position = index + 1,
                skills = validSkills,
                resources = ResourceCode.entries.mapNotNull { code -> stock[code]?.let { code to it } }.toMap()
            )
        }
        val quests = db.rows("SELECT quest_number, is_completed, is_available, chapter FROM quests WHERE campaign_id = $campaignId") {
            QuestRow(it.getLong(0).toInt(), it.getLong(1) != 0L, it.getLong(2) != 0L, it.getLong(3).toInt())
        }.filter { it.number > 0 }.distinctBy { it.number }.map { quest ->
            val status = when {
                quest.completed -> QuestStatus.COMPLETED
                quest.available -> QuestStatus.OPEN
                else -> QuestStatus.EXPIRED
            }
            val opened = (quest.chapter - 1).coerceIn(0, 11)
            QuestState(quest.number, status, opened, if (status == QuestStatus.OPEN) null else chapter)
        }.sortedBy { it.number }
        val seenAchievements = mutableSetOf<String>()
        val achievements = db.rows("SELECT achievement_id, name FROM achievements WHERE campaign_id = $campaignId ORDER BY id") {
            it.text(0).ifBlank { it.text(1) }
        }.mapNotNull { name ->
            val canonical = LegacyCatalogCodes.achievementCodes.entries
                .firstOrNull { normalizeAchievementName(it.key) == normalizeAchievementName(name) }
            val title = canonical?.key ?: name.trim()
            if (title.isEmpty() || !seenAchievements.add(normalizeAchievementName(title))) return@mapNotNull null
            AchievementState(newLocalKey(), code = canonical?.value, name = title, source = AchievementSource.MANUAL, grantedInChapter = chapter)
        }
        val trophies = db.rows("SELECT boss_name, chapter FROM trophies WHERE campaign_id = $campaignId ORDER BY id") {
            it.getText(0) to it.getLong(1).toInt()
        }.mapNotNull { (bossName, trophyChapter) ->
            val code = LegacyCatalogCodes.bossCodes.entries.firstOrNull { it.key.equals(bossName.trim(), ignoreCase = true) }?.value
                ?: return@mapNotNull null
            TrophyState(code, (trophyChapter - 1).coerceIn(0, 11))
        }
        val completed = trophies.any { it.bossCode == AWAKENED }
        val state = CampaignState(
            name = (row[1] as String).ifBlank { "Кампания" },
            chapter = chapter,
            status = if (completed) CampaignStatus.COMPLETED else CampaignStatus.ACTIVE,
            forgeLevel = (row[3] as Long).toInt().coerceIn(1, 3),
            labLevel = (row[4] as Long).toInt().coerceIn(1, 3),
            finalBossCode = if (chapter == 11) AWAKENED else null,
            notes = row[5] as String,
            hunters = squad,
            quests = quests,
            achievements = achievements,
            trophies = trophies
        )
        result += state to ((row[6] as Long) to (row[7] as Long))
    }
    return result
}

private const val AWAKENED = "AWAKENED"

private data class QuestRow(val number: Int, val completed: Boolean, val available: Boolean, val chapter: Int)

private fun <T> SQLiteConnection.rows(sql: String, map: (SQLiteStatement) -> T): List<T> = prepare(sql).use { statement ->
    val rows = mutableListOf<T>()
    while (statement.step()) rows += map(statement)
    rows
}

private fun SQLiteStatement.text(index: Int): String = if (isNull(index)) "" else getText(index)
