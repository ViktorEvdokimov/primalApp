package com.primalapp.database.mapper

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
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.ChapterDecision
import com.primalapp.model.campaign.ChapterInfo
import com.primalapp.model.campaign.ConditionalMessage
import com.primalapp.model.campaign.ConditionalQuestOpen
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceEntry
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
import com.primalapp.model.campaign.TaskCondition
import com.primalapp.model.campaign.TaskConditionKind
import com.primalapp.model.campaign.TaskInfo
import com.primalapp.model.campaign.Trophy

fun CampaignEntity.toDomain(
    hunters: List<CampaignHunter> = emptyList(),
    achievements: List<Achievement> = emptyList(),
    trophies: List<Trophy> = emptyList(),
    quests: List<Quest> = emptyList()
) = Campaign(
    id = id,
    name = name,
    currentChapter = currentChapter,
    forgeLevel = forgeLevel,
    labLevel = labLevel,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Campaign.toEntity() = CampaignEntity(
    id = id,
    name = name,
    currentChapter = currentChapter,
    forgeLevel = forgeLevel,
    labLevel = labLevel,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HunterEntity.toDomain(
    skills: List<SkillNode> = emptyList(),
    materials: Map<Material, Int> = emptyMap(),
    plants: Map<Plant, Int> = emptyMap(),
    elements: Map<Element, Int> = emptyMap()
) = CampaignHunter(
    id = id,
    campaignId = campaignId,
    playerName = playerName,
    className = HunterClass.valueOf(className)
)

fun CampaignHunter.toEntity() = HunterEntity(
    id = id,
    campaignId = campaignId,
    playerName = playerName,
    className = className.name
)

fun SkillEntity.toDomain() = SkillNode(
    branch = SkillBranch.valueOf(branch),
    tier = tier,
    unlocked = unlocked
)

fun SkillNode.toEntity(hunterId: Long) = SkillEntity(
    hunterId = hunterId,
    branch = branch.name,
    tier = tier,
    unlocked = unlocked
)

fun ResourceEntity.toDomain() = ResourceEntry(
    resourceType = ResourceType.valueOf(resourceType),
    resourceName = resourceName,
    quantity = quantity
)

fun AchievementEntity.toDomain() = Achievement(
    id = achievementId,
    name = name,
    description = description,
    unlocked = unlocked
)

fun Achievement.toEntity(campaignId: Long) = AchievementEntity(
    campaignId = campaignId,
    achievementId = id,
    name = name,
    description = description,
    unlocked = unlocked
)

fun TrophyEntity.toDomain() = Trophy(
    bossName = bossName,
    element = element?.let { Element.valueOf(it) },
    chapter = chapter,
    acquiredAt = acquiredAt
)

fun Trophy.toEntity(campaignId: Long) = TrophyEntity(
    campaignId = campaignId,
    bossName = bossName,
    element = element?.name,
    chapter = chapter,
    acquiredAt = acquiredAt
)

fun QuestEntity.toDomain() = Quest(
    id = questId,
    name = name,
    chapter = chapter,
    element = element?.let { Element.valueOf(it) },
    questNumber = questNumber,
    isCompleted = isCompleted,
    isAvailable = isAvailable
)

fun Quest.toEntity(campaignId: Long) = QuestEntity(
    campaignId = campaignId,
    questId = id,
    name = name,
    chapter = chapter,
    element = element?.name,
    questNumber = questNumber,
    isCompleted = isCompleted,
    isAvailable = isAvailable
)

fun BossEntity.toDomain() = Boss(
    id = id,
    name = name,
    element = element?.let { Element.valueOf(it) },
    difficulty = difficulty,
    stances = buildList {
        add(BossStance(stance1Dfw, stance1Hsc))
        add(BossStance(stance2Dfw, stance2Hsc))
        add(BossStance(stance3Dfw, stance3Hsc))
        if (stance4Dfw > 0) add(BossStance(stance4Dfw, stance4Hsc))
        if (stance5Dfw > 0) add(BossStance(stance5Dfw, stance5Hsc))
    }
)

// ---------- TaskInfo ----------

private const val RESOURCE_SEPARATOR = ";"
private const val RESOURCE_KEY_VALUE = ":"
private const val LIST_SEPARATOR = ","
private const val CONDITION_SEPARATOR = "|"
private const val DECISION_SEPARATOR = "?"

private fun encodeResourceMap(values: Map<out Any, Int>): String =
    values.entries.joinToString(RESOURCE_SEPARATOR) { (key, qty) -> "$key$RESOURCE_KEY_VALUE$qty" }

private fun encodeIntList(values: List<Int>): String =
    values.joinToString(LIST_SEPARATOR)

private fun encodeStringList(values: List<String>): String =
    values.joinToString(RESOURCE_SEPARATOR)

private fun parseResourceMap(value: String, nameToValue: (String) -> Any): Map<Any, Int> {
    if (value.isBlank()) return emptyMap()
    return value.split(RESOURCE_SEPARATOR).mapNotNull { entry ->
        val parts = entry.split(RESOURCE_KEY_VALUE)
        if (parts.size != 2) return@mapNotNull null
        runCatching {
            val key = nameToValue(parts[0].trim())
            val qty = parts[1].trim().toInt()
            key to qty
        }.getOrNull()
    }.toMap()
}

private fun parseIntList(value: String): List<Int> =
    if (value.isBlank()) emptyList()
    else value.split(LIST_SEPARATOR).mapNotNull { it.trim().toIntOrNull() }

private fun parseStringList(value: String): List<String> =
    if (value.isBlank()) emptyList()
    else value.split(RESOURCE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

// ---------- TaskCondition ----------

private fun TaskCondition.encode(): String {
    val parts = listOf(
        kind.name,
        achievementName.orEmpty(),
        chapterSet.joinToString(LIST_SEPARATOR),
        questNumber?.toString().orEmpty(),
        elseQuestNumber?.toString().orEmpty(),
        rewardAchievement.orEmpty()
    )
    return parts.joinToString(CONDITION_SEPARATOR)
}

private fun encodeConditions(conditions: List<TaskCondition>): String =
    conditions.joinToString(RESOURCE_SEPARATOR) { it.encode() }

private fun parseConditions(value: String): List<TaskCondition> {
    if (value.isBlank()) return emptyList()
    return value.split(RESOURCE_SEPARATOR).mapNotNull { raw ->
        val parts = raw.split(CONDITION_SEPARATOR)
        if (parts.size < 5 || parts.size > 6) return@mapNotNull null
        runCatching {
            TaskCondition(
                kind = TaskConditionKind.valueOf(parts[0]),
                achievementName = parts[1].ifBlank { null },
                chapterSet = parts[2].let { if (it.isBlank()) emptyList() else it.split(LIST_SEPARATOR).map(String::toInt) },
                questNumber = parts[3].toIntOrNull(),
                elseQuestNumber = parts[4].toIntOrNull(),
                rewardAchievement = parts.getOrNull(5)?.ifBlank { null }
            )
        }.getOrNull()
    }
}

// ---------- ChapterInfo ----------

private fun ConditionalQuestOpen.encode(): String {
    val achievements = achievements.joinToString(LIST_SEPARATOR)
    val elsePart = elseQuestNumber?.toString().orEmpty()
    val requireAllPart = if (requireAll) "1" else "0"
    val negatedPart = if (negated) "1" else "0"
    return "$achievements$RESOURCE_KEY_VALUE$questNumber$RESOURCE_KEY_VALUE$elsePart$RESOURCE_KEY_VALUE$requireAllPart$RESOURCE_KEY_VALUE$negatedPart"
}

private fun encodeConditionalQuestOpens(values: List<ConditionalQuestOpen>): String =
    values.joinToString(RESOURCE_SEPARATOR) { it.encode() }

private fun parseConditionalQuestOpens(value: String): List<ConditionalQuestOpen> {
    if (value.isBlank()) return emptyList()
    return value.split(RESOURCE_SEPARATOR).mapNotNull { raw ->
        val parts = raw.split(RESOURCE_KEY_VALUE)
        if (parts.size < 2) return@mapNotNull null
        runCatching {
            ConditionalQuestOpen(
                achievements = parts[0].let { if (it.isBlank()) emptyList() else it.split(LIST_SEPARATOR).map(String::trim) },
                questNumber = parts[1].toInt(),
                elseQuestNumber = parts.getOrNull(2)?.toIntOrNull(),
                requireAll = parts.getOrNull(3) == "1",
                negated = parts.getOrNull(4) == "1"
            )
        }.getOrNull()
    }
}

private fun ChapterDecision.encode(): String {
    val options = options.joinToString(CONDITION_SEPARATOR)
    val achievement = achievementOnOption.orEmpty()
    val optionFor = optionForAchievement.orEmpty()
    return "$question$DECISION_SEPARATOR$options$DECISION_SEPARATOR$achievement$DECISION_SEPARATOR$optionFor"
}

private fun encodeDecisions(values: List<ChapterDecision>): String =
    values.joinToString(RESOURCE_SEPARATOR) { it.encode() }

private fun parseDecisions(value: String): List<ChapterDecision> {
    if (value.isBlank()) return emptyList()
    return value.split(RESOURCE_SEPARATOR).mapNotNull { raw ->
        val parts = raw.split(DECISION_SEPARATOR)
        if (parts.size != 4) return@mapNotNull null
        runCatching {
            ChapterDecision(
                question = parts[0],
                options = parts[1].split(CONDITION_SEPARATOR).filter { it.isNotEmpty() },
                achievementOnOption = parts[2].ifBlank { null },
                optionForAchievement = parts[3].ifBlank { null }
            )
        }.getOrNull()
    }
}

private fun ConditionalMessage.encode(): String =
    "$achievementName$RESOURCE_KEY_VALUE$message"

private fun encodeConditionalMessages(values: List<ConditionalMessage>): String =
    values.joinToString(RESOURCE_SEPARATOR) { it.encode() }

private fun parseConditionalMessages(value: String): List<ConditionalMessage> {
    if (value.isBlank()) return emptyList()
    return value.split(RESOURCE_SEPARATOR).mapNotNull { raw ->
        val parts = raw.split(RESOURCE_KEY_VALUE, limit = 2)
        if (parts.size != 2) return@mapNotNull null
        runCatching { ConditionalMessage(achievementName = parts[0], message = parts[1]) }.getOrNull()
    }
}

fun TaskInfoEntity.toDomain(): TaskInfo {
    val materials = parseResourceMap(victoryMaterials) { Material.valueOf(it) }
        .mapKeys { it.key as Material }
    val plants = parseResourceMap(victoryPlants) { Plant.valueOf(it) }
        .mapKeys { it.key as Plant }
return TaskInfo(
        questNumber = questNumber,
        name = name,
        bossName = bossName,
        bossElement = bossElement?.let { Element.valueOf(it) },
        victoryMaterials = materials,
        victoryPlants = plants,
        victoryOpenQuests = parseIntList(victoryOpenQuests),
        victoryOpenQuestConditions = parseConditions(victoryOpenQuestConditions),
        victoryAchievements = parseStringList(victoryAchievements),
        victoryRewardCards = parseIntList(victoryRewardCards).map { it.toString() },
        victorySpecial = victorySpecial,
        defeatOpenQuests = parseIntList(defeatOpenQuests),
        defeatOpenQuestConditions = parseConditions(defeatOpenQuestConditions),
        defeatAchievements = parseStringList(defeatAchievements)
    )
}

fun TaskInfo.toEntity() = TaskInfoEntity(
    questNumber = questNumber,
    name = name,
    bossName = bossName,
    bossElement = bossElement?.name,
    victoryMaterials = encodeResourceMap(victoryMaterials),
    victoryPlants = encodeResourceMap(victoryPlants),
    victoryOpenQuests = encodeIntList(victoryOpenQuests),
    victoryOpenQuestConditions = encodeConditions(victoryOpenQuestConditions),
    victoryAchievements = encodeStringList(victoryAchievements),
    victoryRewardCards = encodeStringList(victoryRewardCards),
    victorySpecial = victorySpecial,
    defeatOpenQuests = encodeIntList(defeatOpenQuests),
    defeatOpenQuestConditions = encodeConditions(defeatOpenQuestConditions),
    defeatAchievements = encodeStringList(defeatAchievements)
)

fun ChapterInfoEntity.toDomain(): ChapterInfo {
    val rewards = parseResourceMap(rewards) { Material.valueOf(it) }
        .mapKeys { it.key as Material }
    val plants = parseResourceMap(rewardPlants) { Plant.valueOf(it) }
        .mapKeys { it.key as Plant }
    return ChapterInfo(
        chapter = chapter,
        rewards = rewards,
        rewardPlants = plants,
        openQuests = parseIntList(openQuests),
        conditionalOpenQuests = parseConditionalQuestOpens(conditionalOpenQuests),
        expireQuests = parseIntList(expireQuests),
        forgeUpgrade = forgeUpgrade,
        labUpgrade = labUpgrade,
        hunterKitUpgrade = hunterKitUpgrade,
        decisions = parseDecisions(decisions),
        messages = parseStringList(messages),
        conditionalMessages = parseConditionalMessages(conditionalMessages)
    )
}

fun ChapterInfo.toEntity() = ChapterInfoEntity(
    chapter = chapter,
    rewards = encodeResourceMap(rewards),
    rewardPlants = encodeResourceMap(rewardPlants),
    openQuests = encodeIntList(openQuests),
    conditionalOpenQuests = encodeConditionalQuestOpens(conditionalOpenQuests),
    expireQuests = encodeIntList(expireQuests),
    forgeUpgrade = forgeUpgrade,
    labUpgrade = labUpgrade,
    hunterKitUpgrade = hunterKitUpgrade,
    decisions = encodeDecisions(decisions),
    messages = encodeStringList(messages),
    conditionalMessages = encodeConditionalMessages(conditionalMessages)
)
