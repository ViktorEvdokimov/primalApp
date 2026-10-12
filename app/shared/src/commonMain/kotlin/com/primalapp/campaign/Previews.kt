package com.primalapp.campaign

import com.primalapp.catalog.RuleExplanation
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import kotlinx.serialization.Serializable

/** Ошибка правил кампании: действие недопустимо в текущем состоянии (текст — для игрока). */
open class CampaignRuleException(message: String) : RuntimeException(message)

/** Итог боя расходится с кампанией: пока шёл бой, глава закрыта, сменилась или задание закрыто. */
class CampaignChangedException(val reasons: List<String>) : CampaignRuleException(reasons.joinToString(" "))

/** Условия действия: дополнения владельца кампании, текущее время и кто действует (для истории боёв). */
data class RuleContext(
    val expansions: Set<com.primalapp.model.campaign.Expansion>,
    val now: Long,
    val actor: Actor?,
    val newKey: () -> String
)

/** Итог боя от экрана боя: поля старта (снимок подготовки) и результат. */
@Serializable
data class BattleReport(
    val battleId: String,
    val purpose: BattlePurpose,
    val questNumber: Int? = null,
    val bossCode: String? = null,
    val difficulty: Int,
    val chapter: Int,
    val progressSeq: Int,
    val result: BattleResult,
    val defeatReason: DefeatReason? = null,
    val roundsPlayed: Int? = null,
    val startedAt: Long,
    val finishedAt: Long
) {
    val victory: Boolean get() = result == BattleResult.VICTORY
}

/** Награды, исправленные через «Редактировать»: применяются ровно они, правила не вычисляются. */
@Serializable
data class ResultOverrides(
    val bossCode: String?,
    val perHunter: Map<ResourceCode, Int>,
    val openQuests: List<Int>,
    val achievements: List<String>
)

/** Куда дальше после итога боя. */
enum class NextStep { CHAPTER_TRANSITION, CAMPAIGN_SHEET, CAMPAIGN_COMPLETED }

/** Достижение в окне наград: код и название. */
data class AchievementRef(val code: String, val name: String)

/** Награды за бой (превью). */
data class BattleRewards(
    val trophyBoss: String?,
    val perHunter: Map<ResourceCode, Int>,
    val achievements: List<AchievementRef>,
    val openQuests: List<Int>,
    val rewardCards: List<String>,
    val messages: List<String>
)

/** Превью итога боя: награды, условные правила, куда дальше и что не будет применено при «Отклонить». */
data class ResultPreview(
    val result: BattleResult,
    val purpose: BattlePurpose,
    val questNumber: Int?,
    val questName: String?,
    val rewards: BattleRewards,
    val rules: List<RuleExplanation>,
    val next: NextStep,
    val otherActiveBattles: List<BattleRecord>,
    val dismissConsequences: List<String>
)

/** Задание, у которого истекает время при переходе; [consequences] — последствия невыполненного задания. */
data class ExpiringQuest(val number: Int, val name: String, val wasOpen: Boolean, val consequences: List<String>)

data class TransitionDecisionView(
    val code: String,
    val question: String,
    val options: List<Pair<String, String>>,
    val selected: String?
)

/** Превью перехода главы (с учётом ответов на решения). */
data class TransitionPreview(
    val fromChapter: Int,
    val toChapter: Int,
    val decisions: List<TransitionDecisionView>,
    val decisionsComplete: Boolean,
    val perHunter: Map<ResourceCode, Int>,
    val openQuests: List<Int>,
    val expireQuests: List<ExpiringQuest>,
    val achievements: List<AchievementRef>,
    val forgeLevelUp: Boolean,
    val labLevelUp: Boolean,
    val hunterKitUpgrade: Boolean,
    val rewardCards: List<String>,
    val messages: List<String>,
    val finalBattle: String?,
    val rules: List<RuleExplanation>,
    val rejectConsequences: List<String>
)

/** Итог «Выполнено» вручную: открытые задания и пояснения правил, которые открывают задания. */
data class CompletedQuest(val number: Int, val opened: List<Int>, val rules: List<RuleExplanation>)

/** Держатель карты награды: охотник по классу. */
typealias RewardCardHolders = List<HunterClass?>
