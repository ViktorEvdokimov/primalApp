package com.primalapp.net

import kotlinx.serialization.Serializable

/** Ответы сервера (`web/doc/api.md`) — только поля, нужные приложению. */

@Serializable
data class UserDto(
    val id: Long,
    val login: String,
    val displayName: String? = null,
    val passwordSet: Boolean = true,
    val expansions: List<String>? = null
)

@Serializable
data class DeviceDto(val id: String, val displayName: String? = null)

/** «Кто я»: пользователь (`USER`) или гость по ссылке (`GUEST`). */
@Serializable
data class MeDto(val kind: String = "USER", val user: UserDto? = null, val device: DeviceDto? = null)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class RegisterRequest(val login: String, val password: String, val displayName: String)

@Serializable
data class DisplayNameRequest(val displayName: String)

@Serializable
data class ExpansionsRequest(val expansions: List<String>)

@Serializable
data class HunterSummaryDto(val `class`: String, val playerName: String)

/** Строка списка кампаний: свои и открытые по ссылке. */
@Serializable
data class CampaignSummaryDto(
    val id: Long,
    val name: String,
    val chapter: Int,
    val status: String,
    val access: String,
    val ownerName: String,
    val hunters: List<HunterSummaryDto> = emptyList(),
    val pendingTransition: Boolean = false,
    val updatedAt: String? = null,
    /** основной персонаж этого входа (код класса); `null` — не выбран (web api.md §5.7) */
    val mainHunter: String? = null
)

// region Снимок кампании (api.md §11)

@Serializable
data class SnapshotSkillDto(val branch: String, val tier: Int)

@Serializable
data class SnapshotItemDto(
    val id: Long? = null,
    val kind: String,
    val name: String,
    val level: Int? = null,
    val element: String? = null,
    val source: String? = null
)

@Serializable
data class SnapshotHunterDto(
    val id: Long? = null,
    val `class`: String,
    val playerName: String,
    val position: Int,
    val skills: List<SnapshotSkillDto> = emptyList(),
    val resources: Map<String, Int> = emptyMap(),
    val items: List<SnapshotItemDto> = emptyList()
)

@Serializable
data class SnapshotQuestDto(val number: Int, val status: String, val openedInChapter: Int, val closedInChapter: Int? = null)

@Serializable
data class SnapshotAchievementDto(
    val id: Long? = null,
    val code: String? = null,
    val name: String,
    val source: String,
    val grantedInChapter: Int
)

@Serializable
data class SnapshotTrophyDto(val bossCode: String, val chapter: Int, val battleId: String? = null)

@Serializable
data class ActorDto(val kind: String, val name: String)

@Serializable
data class SnapshotBattleDto(
    val id: String,
    val purpose: String,
    val questNumber: Int? = null,
    val bossCode: String? = null,
    val difficulty: Int,
    val chapter: Int,
    val progressSeq: Int,
    val status: String,
    val result: String? = null,
    val defeatReason: String? = null,
    val roundsPlayed: Int? = null,
    val startedBy: ActorDto? = null,
    val startedAt: String,
    val submittedBy: ActorDto? = null,
    val submittedAt: String? = null,
    val finishedAt: String? = null
)

@Serializable
data class SnapshotStateDto(
    val name: String,
    val chapter: Int,
    val status: String,
    val forgeLevel: Int,
    val labLevel: Int,
    val finalBossCode: String? = null,
    val progressSeq: Int,
    val notes: String = "",
    val hunters: List<SnapshotHunterDto> = emptyList(),
    val quests: List<SnapshotQuestDto> = emptyList(),
    val achievements: List<SnapshotAchievementDto> = emptyList(),
    val trophies: List<SnapshotTrophyDto> = emptyList(),
    val battles: List<SnapshotBattleDto> = emptyList()
)

@Serializable
data class CampaignSnapshotDto(
    val id: Long,
    val version: Int,
    val access: String,
    val ownerName: String,
    val ownerExpansions: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val state: SnapshotStateDto
)

@Serializable
data class SnapshotUpdateDto(val expectedVersion: Int, val state: SnapshotStateDto)

@Serializable
data class SnapshotImportDto(val state: SnapshotStateDto)

// endregion

// region Совместная игра (api.md §9)

@Serializable
data class JoinedDto(val kind: String, val name: String, val joinedAt: String? = null)

@Serializable
data class ShareLinkDto(val url: String, val createdAt: String? = null, val joined: List<JoinedDto> = emptyList())

@Serializable
data class ShareInfoDto(
    val kind: String,
    val name: String,
    val ownerName: String,
    /** отряд — для выбора своего персонажа (старый сервер его не присылает) */
    val hunters: List<HunterSummaryDto> = emptyList()
)

/** [hunterClass] — свой персонаж: становится основным; у гостя без имени имя его игрока — имя в истории боёв. */
@Serializable
data class JoinRequest(val displayName: String? = null, val hunterClass: String? = null)

/** Основной персонаж: [hunterClass] — код класса охотника отряда, `null` — снять выбор. */
@Serializable
data class MainHunterRequest(val hunterClass: String?)

@Serializable
data class MainHunterDto(val mainHunter: String? = null)

@Serializable
data class JoinResultDto(val kind: String, val id: Long)

// endregion

/** Ошибка сервера в формате RFC 9457 (`code` — машинный код). */
@Serializable
data class ProblemDto(
    val status: Int = 0,
    val code: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val retryAfter: Int? = null,
    val errors: List<FieldErrorDto> = emptyList()
)

@Serializable
data class FieldErrorDto(val field: String? = null, val message: String? = null)
