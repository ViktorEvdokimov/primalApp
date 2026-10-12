package com.primalapp.sync

import com.primalapp.campaign.AchievementSource
import com.primalapp.campaign.AchievementState
import com.primalapp.campaign.Actor
import com.primalapp.campaign.ActorKind
import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleRecord
import com.primalapp.campaign.BattleResult
import com.primalapp.campaign.BattleStatus
import com.primalapp.campaign.CampaignState
import com.primalapp.campaign.CampaignStatus
import com.primalapp.campaign.DefeatReason
import com.primalapp.campaign.HunterState
import com.primalapp.campaign.InventoryItem
import com.primalapp.campaign.ItemKind
import com.primalapp.campaign.QuestState
import com.primalapp.campaign.QuestStatus
import com.primalapp.campaign.SkillStep
import com.primalapp.campaign.TrophyState
import com.primalapp.campaign.newLocalKey
import com.primalapp.campaign.serverKey
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.net.ActorDto
import com.primalapp.net.SnapshotAchievementDto
import com.primalapp.net.SnapshotBattleDto
import com.primalapp.net.SnapshotHunterDto
import com.primalapp.net.SnapshotItemDto
import com.primalapp.net.SnapshotQuestDto
import com.primalapp.net.SnapshotSkillDto
import com.primalapp.net.SnapshotStateDto
import com.primalapp.net.SnapshotTrophyDto
import kotlinx.datetime.Instant

/**
 * Снимок сервера ↔ состояние кампании приложения (`web/doc/api.md` §11). Карты и достижения с сервера получают
 * ключи `s<id>`; новые записи приложения отправляются без id — сервер выдаёт его сам.
 */
object SnapshotMapper {

    fun toState(dto: SnapshotStateDto): CampaignState = CampaignState(
        name = dto.name,
        chapter = dto.chapter,
        status = enumOr(dto.status, CampaignStatus.ACTIVE),
        forgeLevel = dto.forgeLevel,
        labLevel = dto.labLevel,
        finalBossCode = dto.finalBossCode,
        progressSeq = dto.progressSeq,
        notes = dto.notes,
        hunters = dto.hunters.mapNotNull { hunter ->
            val hunterClass = enumOrNull<HunterClass>(hunter.`class`) ?: return@mapNotNull null
            HunterState(
                id = hunter.id,
                hunterClass = hunterClass,
                playerName = hunter.playerName,
                position = hunter.position,
                skills = hunter.skills.mapNotNull { skill ->
                    enumOrNull<SkillBranch>(skill.branch)?.let { SkillStep(it, skill.tier) }
                }.sortedWith(compareBy({ it.branch.ordinal }, { it.tier })),
                resources = ResourceCode.entries.mapNotNull { code -> hunter.resources[code.name]?.takeIf { it > 0 }?.let { code to it } }.toMap(),
                items = hunter.items.mapNotNull { item ->
                    val kind = enumOrNull<ItemKind>(item.kind) ?: return@mapNotNull null
                    InventoryItem(
                        key = item.id?.let { serverKey(it) } ?: newLocalKey(),
                        id = item.id,
                        kind = kind,
                        name = item.name,
                        level = item.level,
                        element = Element.parse(item.element),
                        source = item.source
                    )
                }
            )
        }.sortedBy { it.position },
        quests = dto.quests.mapNotNull { quest ->
            val status = enumOrNull<QuestStatus>(quest.status) ?: return@mapNotNull null
            QuestState(quest.number, status, quest.openedInChapter, quest.closedInChapter)
        }.sortedBy { it.number },
        achievements = dto.achievements.map { achievement ->
            AchievementState(
                key = achievement.id?.let { serverKey(it) } ?: newLocalKey(),
                id = achievement.id,
                code = achievement.code,
                name = achievement.name,
                source = enumOr(achievement.source, AchievementSource.MANUAL),
                grantedInChapter = achievement.grantedInChapter
            )
        },
        trophies = dto.trophies.map { TrophyState(it.bossCode, it.chapter, it.battleId) },
        battles = dto.battles.mapNotNull { battle ->
            BattleRecord(
                id = battle.id,
                purpose = enumOrNull<BattlePurpose>(battle.purpose) ?: return@mapNotNull null,
                questNumber = battle.questNumber,
                bossCode = battle.bossCode,
                difficulty = battle.difficulty,
                chapter = battle.chapter,
                progressSeq = battle.progressSeq,
                status = enumOr(battle.status, BattleStatus.IN_PROGRESS),
                result = battle.result?.let { enumOrNull<BattleResult>(it) },
                defeatReason = battle.defeatReason?.let { enumOrNull<DefeatReason>(it) },
                roundsPlayed = battle.roundsPlayed,
                startedBy = battle.startedBy?.let { actor(it) },
                startedAt = millis(battle.startedAt) ?: 0,
                submittedBy = battle.submittedBy?.let { actor(it) },
                submittedAt = millis(battle.submittedAt),
                finishedAt = millis(battle.finishedAt)
            )
        }
    )

    fun toDto(state: CampaignState): SnapshotStateDto = SnapshotStateDto(
        name = state.name,
        chapter = state.chapter,
        status = state.status.name,
        forgeLevel = state.forgeLevel,
        labLevel = state.labLevel,
        finalBossCode = state.finalBossCode,
        progressSeq = state.progressSeq,
        notes = state.notes,
        hunters = state.hunters.map { hunter ->
            SnapshotHunterDto(
                id = hunter.id,
                `class` = hunter.hunterClass.name,
                playerName = hunter.playerName,
                position = hunter.position,
                skills = hunter.skills.map { SnapshotSkillDto(it.branch.name, it.tier) },
                resources = hunter.resources.filterValues { it > 0 }.mapKeys { it.key.name },
                items = hunter.items.map { item ->
                    SnapshotItemDto(item.id, item.kind.name, item.name, item.level, item.element?.name, item.source)
                }
            )
        },
        quests = state.quests.map { SnapshotQuestDto(it.number, it.status.name, it.openedInChapter, it.closedInChapter) },
        achievements = state.achievements.map {
            SnapshotAchievementDto(it.id, it.code, it.name, it.source.name, it.grantedInChapter)
        },
        trophies = state.trophies.map { SnapshotTrophyDto(it.bossCode, it.chapter, it.battleId) },
        battles = state.battles.map { battle ->
            SnapshotBattleDto(
                id = battle.id,
                purpose = battle.purpose.name,
                questNumber = battle.questNumber,
                bossCode = battle.bossCode,
                difficulty = battle.difficulty,
                chapter = battle.chapter,
                progressSeq = battle.progressSeq,
                status = battle.status.name,
                result = battle.result?.name,
                defeatReason = battle.defeatReason?.name,
                roundsPlayed = battle.roundsPlayed,
                startedBy = battle.startedBy?.let { ActorDto(it.kind.name, it.name) },
                startedAt = iso(battle.startedAt),
                submittedBy = battle.submittedBy?.let { ActorDto(it.kind.name, it.name) },
                submittedAt = battle.submittedAt?.let { iso(it) },
                finishedAt = battle.finishedAt?.let { iso(it) }
            )
        }
    )

    fun expansions(codes: List<String>): Set<Expansion> = codes.mapNotNull { enumOrNull<Expansion>(it) }.toSet()

    fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    fun iso(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

    private fun actor(dto: ActorDto) = Actor(enumOr(dto.kind, ActorKind.USER), dto.name)

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumOrNull<E>(name) ?: default
}
