package com.primalapp.database.mapper

import com.primalapp.database.entity.AchievementEntity
import com.primalapp.database.entity.CampaignEntity
import com.primalapp.database.entity.HunterEntity
import com.primalapp.database.entity.QuestEntity
import com.primalapp.database.entity.ResourceEntity
import com.primalapp.database.entity.SkillEntity
import com.primalapp.database.entity.TrophyEntity
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceEntry
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
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
    element = Element.valueOf(element),
    chapter = chapter,
    acquiredAt = acquiredAt
)

fun Trophy.toEntity(campaignId: Long) = TrophyEntity(
    campaignId = campaignId,
    bossName = bossName,
    element = element.name,
    chapter = chapter,
    acquiredAt = acquiredAt
)

fun QuestEntity.toDomain() = Quest(
    id = questId,
    name = name,
    chapter = chapter,
    element = element?.let { Element.valueOf(it) },
    isCompleted = isCompleted,
    isAvailable = isAvailable
)

fun Quest.toEntity(campaignId: Long) = QuestEntity(
    campaignId = campaignId,
    questId = id,
    name = name,
    chapter = chapter,
    element = element?.name,
    isCompleted = isCompleted,
    isAvailable = isAvailable
)
