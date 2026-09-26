package com.primalapp.database

import com.primalapp.database.entity.CampaignEntity
import com.primalapp.database.entity.HunterEntity
import com.primalapp.database.entity.ResourceEntity
import com.primalapp.database.entity.SkillEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import com.primalapp.domain.SkillValidatorImpl
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.ChapterInfo
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
import com.primalapp.model.campaign.TaskInfo
import com.primalapp.model.campaign.Trophy
import com.primalapp.repository.CampaignRepository

class CampaignRepositoryImpl(
    private val database: PrimalDatabase
) : CampaignRepository {

    private val campaignDao get() = database.campaignDao()
    private val hunterDao get() = database.hunterDao()
    private val skillDao get() = database.skillDao()
    private val resourceDao get() = database.resourceDao()
    private val achievementDao get() = database.achievementDao()
    private val trophyDao get() = database.trophyDao()
    private val questDao get() = database.questDao()
    private val taskInfoDao get() = database.taskInfoDao()
    private val chapterInfoDao get() = database.chapterInfoDao()

    private val skillValidator = SkillValidatorImpl()

    override suspend fun getAllCampaigns(): List<Campaign> =
        campaignDao.getAllCampaignsList().map { it.toDomain() }

    override suspend fun getCampaign(id: Long): Campaign? =
        campaignDao.getCampaign(id)?.toDomain()

    override suspend fun createCampaign(name: String): Long {
        val entity = CampaignEntity(
            name = name,
            currentChapter = 1,
            forgeLevel = 1,
            labLevel = 1
        )
        return campaignDao.insertCampaign(entity)
    }

    override suspend fun saveCampaign(campaign: Campaign) {
        campaignDao.updateCampaign(
            campaign.copy(updatedAt = currentTimeMillis()).toEntity()
        )
    }

    override suspend fun deleteCampaign(id: Long) = campaignDao.deleteCampaignById(id)
    override suspend fun getCampaignCount(): Int = campaignDao.getCount()
    override suspend fun getMaxCampaigns(): Int = 10

    override suspend fun getHunters(campaignId: Long): List<CampaignHunter> =
        hunterDao.getHuntersList(campaignId).map { entity ->
            entity.toDomain(skills = getSkills(entity.id))
        }

    override suspend fun addHunters(campaignId: Long, hunters: List<CampaignHunter>) {
        hunters.forEach { hunter ->
            val entity = hunter.copy(campaignId = campaignId).toEntity()
            val hunterId = hunterDao.insertHunterReturningId(entity)
            initSkillTree(hunterId)
            initResources(hunterId)
        }
    }

    override suspend fun getSkills(hunterId: Long): List<SkillNode> =
        skillDao.getSkillsList(hunterId).map { it.toDomain() }

    override suspend fun unlockSkill(hunterId: Long, branch: SkillBranch, tier: Int) {
        val skills = skillDao.getSkillsList(hunterId).map { it.toDomain() }
        if (skillValidator.canUnlock(branch, tier, skills)) {
            skillDao.setUnlocked(hunterId, branch.name, tier, true)
        }
    }

    override suspend fun getAvailableSkillBranches(hunterId: Long): List<SkillBranch> {
        val skills = skillDao.getSkillsList(hunterId).map { it.toDomain() }
        return skillValidator.getAvailableBranches(skills)
    }

    override suspend fun getMaterials(hunterId: Long): Map<Material, Int> =
        resourceDao.getResourcesByTypeList(hunterId, "MATERIAL")
            .mapNotNull { entry ->
                val material = runCatching { Material.valueOf(entry.resourceName) }.getOrNull()
                    ?: return@mapNotNull null
                material to entry.quantity
            }
            .toMap()

    override suspend fun getPlants(hunterId: Long): Map<Plant, Int> =
        resourceDao.getResourcesByTypeList(hunterId, "PLANT")
            .associate { Plant.valueOf(it.resourceName) to it.quantity }

    override suspend fun getElements(hunterId: Long): Map<Element, Int> =
        resourceDao.getResourcesByTypeList(hunterId, "ELEMENT")
            .associate { Element.valueOf(it.resourceName) to it.quantity }

    override suspend fun updateResource(hunterId: Long, resourceType: ResourceType, resourceName: String, quantity: Int) {
        resourceDao.updateQuantity(hunterId, resourceType.name, resourceName, quantity)
    }

    override suspend fun addResource(hunterId: Long, resourceType: ResourceType, resourceName: String, amount: Int) {
        val existing = resourceDao.getResource(hunterId, resourceType.name, resourceName)
        if (existing != null) {
            resourceDao.updateQuantity(hunterId, resourceType.name, resourceName, existing.quantity + amount)
        } else {
            resourceDao.insertResource(
                ResourceEntity(
                    hunterId = hunterId,
                    resourceType = resourceType.name,
                    resourceName = resourceName,
                    quantity = amount
                )
            )
        }
    }

    override suspend fun updateChapter(campaignId: Long, chapter: Int) {
        val campaign = campaignDao.getCampaign(campaignId) ?: return
        campaignDao.updateCampaign(
            campaign.copy(currentChapter = chapter, updatedAt = currentTimeMillis())
        )
    }

    override suspend fun updateForgeLevel(campaignId: Long, level: Int) {
        val campaign = campaignDao.getCampaign(campaignId) ?: return
        campaignDao.updateCampaign(
            campaign.copy(forgeLevel = level, updatedAt = currentTimeMillis())
        )
    }

    override suspend fun updateLabLevel(campaignId: Long, level: Int) {
        val campaign = campaignDao.getCampaign(campaignId) ?: return
        campaignDao.updateCampaign(
            campaign.copy(labLevel = level, updatedAt = currentTimeMillis())
        )
    }

    override suspend fun getForgeLevel(campaignId: Long): Int =
        campaignDao.getCampaign(campaignId)?.forgeLevel ?: 1

    override suspend fun getLabLevel(campaignId: Long): Int =
        campaignDao.getCampaign(campaignId)?.labLevel ?: 1

    override suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?) {
        saveTrophy(campaignId, trophy)
        completeQuest(campaignId, completedQuestId)
        if (nextQuestId != null) {
            questDao.makeQuestAvailable(campaignId, nextQuestId)
        }
    }

    override suspend fun getAchievements(campaignId: Long): List<Achievement> =
        achievementDao.getAchievementsList(campaignId).map { it.toDomain() }

    override suspend fun saveAchievement(campaignId: Long, achievement: Achievement) {
        achievementDao.insertAchievement(achievement.toEntity(campaignId))
    }

    override suspend fun deleteAchievement(campaignId: Long, achievementId: String) {
        achievementDao.deleteAchievement(campaignId, achievementId)
    }

    override suspend fun getTrophies(campaignId: Long): List<Trophy> =
        trophyDao.getTrophiesList(campaignId).map { it.toDomain() }

    override suspend fun saveTrophy(campaignId: Long, trophy: Trophy) {
        trophyDao.insertTrophy(trophy.toEntity(campaignId))
    }

    override suspend fun getQuests(campaignId: Long): List<Quest> =
        questDao.getQuestsList(campaignId).map { it.toDomain() }

    override suspend fun saveQuest(campaignId: Long, quest: Quest) {
        val entity = quest.toEntity(campaignId)
        questDao.upsertQuest(
            campaignId = entity.campaignId,
            questId = entity.questId,
            name = entity.name,
            chapter = entity.chapter,
            element = entity.element,
            questNumber = entity.questNumber,
            isCompleted = entity.isCompleted,
            isAvailable = entity.isAvailable
        )
    }

    override suspend fun completeQuest(campaignId: Long, questId: String) {
        questDao.completeQuest(campaignId, questId)
    }

    override suspend fun uncompleteQuest(campaignId: Long, questId: String) {
        questDao.uncompleteQuest(campaignId, questId)
    }

    override suspend fun setQuestUnavailable(campaignId: Long, questId: String) {
        questDao.setQuestUnavailable(campaignId, questId)
    }

    override suspend fun getCompletedQuests(campaignId: Long): List<Quest> =
        questDao.getCompletedQuestsList(campaignId).map { it.toDomain() }

    override suspend fun getAvailableQuests(campaignId: Long): List<Quest> =
        questDao.getAvailableQuestsList(campaignId).map { it.toDomain() }

    override suspend fun getAllBosses(): List<Boss> =
        database.bossDao().getAllBosses().map { it.toDomain() }

    override suspend fun getAllTaskInfo(): List<TaskInfo> =
        taskInfoDao.getAllTaskInfo().map { it.toDomain() }

    override suspend fun getTaskInfo(questNumber: Int): TaskInfo? =
        taskInfoDao.getTaskInfo(questNumber)?.toDomain()

    override suspend fun getAllChapterInfo(): List<ChapterInfo> =
        chapterInfoDao.getAllChapterInfo().map { it.toDomain() }

    override suspend fun getChapterInfo(chapter: Int): ChapterInfo? =
        chapterInfoDao.getChapterInfo(chapter)?.toDomain()

    private suspend fun initSkillTree(hunterId: Long) {
        val skills = SkillBranch.entries.flatMap { branch ->
            listOf(
                SkillEntity(hunterId = hunterId, branch = branch.name, tier = 1, unlocked = false),
                SkillEntity(hunterId = hunterId, branch = branch.name, tier = 2, unlocked = false)
            )
        }
        skillDao.insertSkills(skills)
    }

    private suspend fun initResources(hunterId: Long) {
        val resources = mutableListOf<ResourceEntity>()
        Material.entries.forEach { m ->
            resources.add(ResourceEntity(hunterId = hunterId, resourceType = "MATERIAL", resourceName = m.name, quantity = 0))
        }
        Plant.entries.forEach { p ->
            resources.add(ResourceEntity(hunterId = hunterId, resourceType = "PLANT", resourceName = p.name, quantity = 0))
        }
        Element.entries.forEach { e ->
            resources.add(ResourceEntity(hunterId = hunterId, resourceType = "ELEMENT", resourceName = e.name, quantity = 0))
        }
        resourceDao.insertResources(resources)
    }
}
