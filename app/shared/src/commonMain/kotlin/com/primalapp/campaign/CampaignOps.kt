package com.primalapp.campaign

import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Действие игрока над кампанией — запись журнала. Кампания с сервера хранит последний снимок сервера и журнал
 * действий после него: при синхронизации журнал перенакатывается на свежий снимок (если кампанию тем временем
 * изменил другой участник), поэтому правки разных устройств складываются, а не затирают друг друга.
 *
 * [at] и [actor] — время и автор действия: при повторе они те же, что при первом выполнении.
 */
@Serializable
sealed class CampaignOp {
    abstract val at: Long
    abstract val actor: Actor?

    /** Выполнить действие над состоянием; недопустимое — [CampaignRuleException]. */
    abstract fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext): CampaignState

    @Serializable
    @SerialName("rename")
    data class Rename(val name: String, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.rename(state, name)
    }

    @Serializable
    @SerialName("notes")
    data class SetNotes(val notes: String, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.setNotes(state, notes)
    }

    @Serializable
    @SerialName("chapter")
    data class ChangeChapter(val chapter: Int, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.changeChapter(state, chapter)
    }

    @Serializable
    @SerialName("hunterName")
    data class RenameHunter(
        val hunter: HunterClass,
        val playerName: String,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.renameHunter(state, hunter, playerName)
    }

    @Serializable
    @SerialName("skill")
    data class SetSkill(
        val hunter: HunterClass,
        val branch: SkillBranch,
        val tier: Int,
        val unlocked: Boolean,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = if (unlocked) {
            rules.unlockSkill(state, hunter, branch, tier)
        } else {
            rules.lockSkill(state, hunter, branch, tier)
        }
    }

    @Serializable
    @SerialName("resources")
    data class AdjustResources(
        val hunter: HunterClass,
        val changes: Map<ResourceCode, Int>,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.adjustResources(state, hunter, changes)
    }

    @Serializable
    @SerialName("forge")
    data class Craft(
        val hunter: HunterClass,
        val item: String,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.craft(state, hunter, item, ctx)
    }

    @Serializable
    @SerialName("lab")
    data class Brew(
        val hunter: HunterClass,
        val potion: String,
        val plants: List<Plant>,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.brew(state, hunter, potion, plants, ctx)
    }

    @Serializable
    @SerialName("addItem")
    data class AddItem(
        val hunter: HunterClass,
        val key: String,
        val kind: ItemKind,
        val name: String,
        val level: Int?,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) =
            rules.addItem(state, hunter, kind, name, level, key)
    }

    @Serializable
    @SerialName("editItem")
    data class EditItem(
        val hunter: HunterClass,
        val key: String,
        val name: String,
        val level: Int?,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.editItem(state, hunter, key, name, level)
    }

    @Serializable
    @SerialName("removeItem")
    data class RemoveItem(
        val hunter: HunterClass,
        val key: String,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.removeItem(state, hunter, key)
    }

    @Serializable
    @SerialName("exchange")
    data class Exchange(
        val from: HunterClass,
        val to: HunterClass,
        val give: Map<ResourceCode, Int>,
        val receive: Map<ResourceCode, Int>,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.exchange(state, from, to, give, receive)
    }

    @Serializable
    @SerialName("convert")
    data class Convert(
        val hunter: HunterClass,
        val spend: List<ResourceCode>,
        val gain: ResourceCode,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.convert(state, hunter, spend, gain)
    }

    @Serializable
    @SerialName("sell")
    data class Sell(
        val hunter: HunterClass,
        val key: String,
        val gain: ResourceCode,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.sell(state, hunter, key, gain)
    }

    @Serializable
    @SerialName("completeQuest")
    data class CompleteQuest(val number: Int, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.completeQuest(state, number, ctx).first
    }

    @Serializable
    @SerialName("reopenQuest")
    data class ReopenQuest(val number: Int, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.reopenQuest(state, number)
    }

    @Serializable
    @SerialName("openQuests")
    data class SetOpenQuests(val numbers: Set<Int>, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.setOpenQuests(state, numbers)
    }

    @Serializable
    @SerialName("addAchievement")
    data class AddAchievement(
        val name: String,
        val key: String,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.addAchievement(state, name, key)
    }

    /** Удаление достижения: по коду каталога или названию — ключ мог смениться после синхронизации. */
    @Serializable
    @SerialName("deleteAchievement")
    data class DeleteAchievement(
        val key: String,
        val name: String,
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext): CampaignState {
            val target = state.achievements.firstOrNull { it.key == key }
                ?: state.achievements.firstOrNull { com.primalapp.domain.achievementMatches(it.name, name) }
                ?: return state
            return rules.deleteAchievement(state, target.key)
        }
    }

    @Serializable
    @SerialName("startBattle")
    data class StartBattle(val record: BattleRecord, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.startBattle(state, record)
    }

    @Serializable
    @SerialName("abandonBattle")
    data class AbandonBattle(val battleId: String, override val at: Long, override val actor: Actor? = null) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = rules.abandonBattle(state, battleId)
    }

    /**
     * Итог боя: «Принять» (в том числе с правками) или «Отклонить». Если при повторе на свежем снимке сервера итог
     * больше не согласуется с кампанией (главу закрыла чужая победа), он записывается отклонённым — как на сайте.
     */
    @Serializable
    @SerialName("battleResult")
    data class SubmitResult(
        val report: BattleReport,
        val accept: Boolean,
        val overrides: ResultOverrides? = null,
        val rewardCardHolders: List<HunterClass?> = emptyList(),
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) =
            rules.submitResult(state, report, accept, overrides, rewardCardHolders, ctx)
    }

    @Serializable
    @SerialName("transition")
    data class Transition(
        val accept: Boolean,
        val decisions: Map<String, String> = emptyMap(),
        override val at: Long,
        override val actor: Actor? = null
    ) : CampaignOp() {
        override fun apply(rules: CampaignRules, state: CampaignState, ctx: RuleContext) = if (accept) {
            rules.acceptTransition(state, decisions, ctx)
        } else {
            rules.rejectTransition(state)
        }
    }
}
