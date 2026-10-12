package com.primalapp.campaign

import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import kotlinx.serialization.Serializable

/** Кампания идёт; ждёт перехода главы (принята победа); пройдена (побеждён финальный босс). */
enum class CampaignStatus { ACTIVE, CHAPTER_TRANSITION, COMPLETED }

enum class QuestStatus { OPEN, COMPLETED, EXPIRED }

/** Карта инвентаря: снаряжение, зелье или карта награды. */
enum class ItemKind(val displayName: String) {
    EQUIPMENT("Снаряжение"),
    POTION("Зелья"),
    REWARD("Карты наград")
}

/** Откуда достижение: награда задания, глава, решение главы или введено вручную. */
enum class AchievementSource { QUEST, CHAPTER, DECISION, MANUAL }

/** Зачем бой: пролог (глава 0), задание, бой без задания, финальный бой главы 11. */
enum class BattlePurpose { PROLOGUE, QUEST, FREE, FINAL }

/** Бой идёт; итог принят; итог отклонён; бой брошен. */
enum class BattleStatus { IN_PROGRESS, APPLIED, DISMISSED, ABANDONED }

enum class BattleResult { VICTORY, DEFEAT }

/** Поражение: закончились раунды или отряд сдался. */
enum class DefeatReason { ROUNDS, SURRENDER }

enum class ActorKind { USER, GUEST }

/** Кто начал бой или отправил итог: имя для истории боёв. */
@Serializable
data class Actor(val kind: ActorKind, val name: String)

@Serializable
data class SkillStep(val branch: SkillBranch, val tier: Int)

/**
 * Карта в инвентаре охотника.
 *
 * @param key ключ в приложении: `s<id>` — карта с сервера, иначе — новая карта приложения
 * @param id идентификатор на сервере; у новой карты — `null`
 * @param level 1–3; у карты награды — `null`
 * @param element стихия кузни снаряжения — её можно получить, сбросив карту
 * @param source код каталога (`FIRE_01`, `LAB_02`) или номер карты награды; `null` — добавлена вручную
 */
@Serializable
data class InventoryItem(
    val key: String,
    val id: Long? = null,
    val kind: ItemKind,
    val name: String,
    val level: Int? = null,
    val element: Element? = null,
    val source: String? = null
)

/** Охотник отряда; класс в кампании уникален — по нему охотник и находится. */
@Serializable
data class HunterState(
    val id: Long? = null,
    val hunterClass: HunterClass,
    val playerName: String,
    val position: Int,
    val skills: List<SkillStep> = emptyList(),
    val resources: Map<ResourceCode, Int> = emptyMap(),
    val items: List<InventoryItem> = emptyList()
) {
    fun quantity(code: ResourceCode): Int = resources[code] ?: 0
}

@Serializable
data class QuestState(
    val number: Int,
    val status: QuestStatus,
    val openedInChapter: Int,
    val closedInChapter: Int? = null
)

/** Достижение кампании; [code] — у достижения каталога, своё (введённое вручную) кода не имеет. */
@Serializable
data class AchievementState(
    val key: String,
    val id: Long? = null,
    val code: String? = null,
    val name: String,
    val source: AchievementSource = AchievementSource.MANUAL,
    val grantedInChapter: Int = 0
)

/** Трофей: босс, побеждённый в главе; [battleId] — бой, который его дал. */
@Serializable
data class TrophyState(val bossCode: String, val chapter: Int, val battleId: String? = null)

/**
 * Бой кампании в истории: отметка о начале и итог. Время — миллисекунды эпохи.
 *
 * @param chapter глава и [progressSeq] — снимок кампании при старте боя: результат принимается, только если
 *   глава с тех пор не закрыта (первая принятая победа закрывает главу)
 */
@Serializable
data class BattleRecord(
    val id: String,
    val purpose: BattlePurpose,
    val questNumber: Int? = null,
    val bossCode: String? = null,
    val difficulty: Int,
    val chapter: Int,
    val progressSeq: Int,
    val status: BattleStatus = BattleStatus.IN_PROGRESS,
    val result: BattleResult? = null,
    val defeatReason: DefeatReason? = null,
    val roundsPlayed: Int? = null,
    val startedBy: Actor? = null,
    val startedAt: Long,
    val submittedBy: Actor? = null,
    val submittedAt: Long? = null,
    val finishedAt: Long? = null
) {
    val isFinished: Boolean get() = status == BattleStatus.APPLIED || status == BattleStatus.DISMISSED
}

/**
 * Состояние кампании — один документ, как снимок сервера (`web/doc/api.md` §11). Глава — книжная: 0 — «Пролог»,
 * 1–11. [progressSeq] растёт при принятой победе, переходе и ручной правке главы: бои, начатые раньше, устаревают.
 */
@Serializable
data class CampaignState(
    val name: String,
    val chapter: Int = 0,
    val status: CampaignStatus = CampaignStatus.ACTIVE,
    val forgeLevel: Int = 1,
    val labLevel: Int = 1,
    val finalBossCode: String? = null,
    val progressSeq: Int = 0,
    val notes: String = "",
    val hunters: List<HunterState> = emptyList(),
    val quests: List<QuestState> = emptyList(),
    val achievements: List<AchievementState> = emptyList(),
    val trophies: List<TrophyState> = emptyList(),
    val battles: List<BattleRecord> = emptyList()
) {
    val openQuests: List<QuestState> get() = quests.filter { it.status == QuestStatus.OPEN }.sortedBy { it.number }
    val completedQuests: List<QuestState> get() = quests.filter { it.status == QuestStatus.COMPLETED }.sortedBy { it.number }
    val expiredQuests: List<QuestState> get() = quests.filter { it.status == QuestStatus.EXPIRED }.sortedBy { it.number }
    val pendingTransition: Boolean get() = status == CampaignStatus.CHAPTER_TRANSITION
    val squad: List<HunterState> get() = hunters.sortedBy { it.position }

    fun quest(number: Int): QuestState? = quests.firstOrNull { it.number == number }
    fun hunter(hunterClass: HunterClass): HunterState? = hunters.firstOrNull { it.hunterClass == hunterClass }
    fun battle(id: String): BattleRecord? = battles.firstOrNull { it.id == id }
}
