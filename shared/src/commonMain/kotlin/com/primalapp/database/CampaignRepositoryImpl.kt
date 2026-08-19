package com.primalapp.database

import com.primalapp.database.entity.CampaignEntity
import com.primalapp.database.entity.HunterEntity
import com.primalapp.database.entity.ResourceEntity
import com.primalapp.database.entity.SkillEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import com.primalapp.domain.ChapterProgression
import com.primalapp.domain.ChapterProgressionImpl
import com.primalapp.domain.ChapterState
import com.primalapp.domain.ExchangeResult
import com.primalapp.domain.ResourceExchangeValidator
import com.primalapp.domain.ResourceExchangeValidatorImpl
import com.primalapp.domain.SkillValidatorImpl
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
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

    private val skillValidator = SkillValidatorImpl()
    private val exchangeValidator = ResourceExchangeValidatorImpl()
    private val chapterProgression = ChapterProgressionImpl()

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
            .associate { Material.valueOf(it.resourceName) to it.quantity }

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

    override suspend fun getHuntersWithResource(campaignId: Long, resourceName: String, resourceType: ResourceType): List<CampaignHunter> {
        val resources = resourceDao.getAlliesWithResourceList(campaignId, resourceName, resourceType.name)
        return resources.mapNotNull { res ->
            val entity = hunterDao.getHunter(res.hunterId) ?: return@mapNotNull null
            entity.toDomain()
        }
    }

    override suspend fun exchangeResources(
        fromHunterId: Long,
        toHunterId: Long,
        fromResources: List<Pair<String, Int>>,
        toResources: List<Pair<String, Int>>,
        resourceType: ResourceType
    ): ExchangeResult {
        val fromMap = fromResources.toMap()
        val toMap = toResources.toMap()

        val validationResult = when (resourceType) {
            ResourceType.MATERIAL -> {
                val fromMat = fromMap.mapKeys { Material.valueOf(it.key) }
                val toMat = toMap.mapKeys { Material.valueOf(it.key) }
                exchangeValidator.canExchangeMaterials(fromMat, toMat)
            }
            ResourceType.PLANT -> {
                val fromPl = fromMap.mapKeys { Plant.valueOf(it.key) }
                val toPl = toMap.mapKeys { Plant.valueOf(it.key) }
                exchangeValidator.canExchangePlants(fromPl, toPl)
            }
            ResourceType.ELEMENT -> {
                val fromEl = fromMap.mapKeys { Element.valueOf(it.key) }
                val toEl = toMap.mapKeys { Element.valueOf(it.key) }
                exchangeValidator.canExchangeElements(fromEl, toEl)
            }
        }

        if (validationResult is ExchangeResult.Invalid) return validationResult

        val rtName = resourceType.name
        for ((name, qty) in fromResources) {
            val res = resourceDao.getResource(fromHunterId, rtName, name)
                ?: return ExchangeResult.Invalid("У охотника нет ресурса $name")
            if (res.quantity < qty) return ExchangeResult.Invalid("Недостаточно $name: нужно $qty, есть ${res.quantity}")
        }
        for ((name, qty) in toResources) {
            val res = resourceDao.getResource(toHunterId, rtName, name)
                ?: return ExchangeResult.Invalid("У союзника нет ресурса $name")
            if (res.quantity < qty) return ExchangeResult.Invalid("У союзника недостаточно $name: нужно $qty, есть ${res.quantity}")
        }

        for ((name, qty) in fromResources) {
            val res = resourceDao.getResource(fromHunterId, rtName, name)!!
            resourceDao.updateQuantity(fromHunterId, rtName, name, res.quantity - qty)
            addResource(toHunterId, resourceType, name, qty)
        }
        for ((name, qty) in toResources) {
            val res = resourceDao.getResource(toHunterId, rtName, name)!!
            resourceDao.updateQuantity(toHunterId, rtName, name, res.quantity - qty)
            addResource(fromHunterId, resourceType, name, qty)
        }

        return ExchangeResult.Valid()
    }

    override suspend fun advanceChapter(campaignId: Long) {
        val campaign = campaignDao.getCampaign(campaignId) ?: return
        val current = ChapterState(
            chapter = campaign.currentChapter,
            forgeLevel = campaign.forgeLevel,
            labLevel = campaign.labLevel
        )
        val next = chapterProgression.advanceChapter(current, bossDefeated = true)
        campaignDao.updateCampaign(
            campaign.copy(
                currentChapter = next.chapter,
                forgeLevel = next.forgeLevel,
                labLevel = next.labLevel,
                updatedAt = currentTimeMillis()
            )
        )
    }

    override suspend fun updateChapter(campaignId: Long, chapter: Int) {
        val campaign = campaignDao.getCampaign(campaignId) ?: return
        campaignDao.updateCampaign(
            campaign.copy(currentChapter = chapter, updatedAt = currentTimeMillis())
        )
    }

    override suspend fun getForgeLevel(campaignId: Long): Int =
        campaignDao.getCampaign(campaignId)?.forgeLevel ?: 1

    override suspend fun getLabLevel(campaignId: Long): Int =
        campaignDao.getCampaign(campaignId)?.labLevel ?: 1

    override suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?) {
        saveTrophy(campaignId, trophy)
        completeQuest(campaignId, completedQuestId)
        advanceChapter(campaignId)
        if (nextQuestId != null) {
            questDao.makeQuestAvailable(campaignId, nextQuestId)
        }
    }

    override suspend fun getAchievements(campaignId: Long): List<Achievement> =
        achievementDao.getAchievementsList(campaignId).map { it.toDomain() }

    override suspend fun saveAchievement(campaignId: Long, achievement: Achievement) {
        achievementDao.insertAchievement(achievement.toEntity(campaignId))
    }

    override suspend fun getTrophies(campaignId: Long): List<Trophy> =
        trophyDao.getTrophiesList(campaignId).map { it.toDomain() }

    override suspend fun saveTrophy(campaignId: Long, trophy: Trophy) {
        trophyDao.insertTrophy(trophy.toEntity(campaignId))
    }

    override suspend fun getQuests(campaignId: Long): List<Quest> =
        questDao.getQuestsList(campaignId).map { it.toDomain() }

    override suspend fun saveQuest(campaignId: Long, quest: Quest) {
        questDao.insertQuest(quest.toEntity(campaignId))
    }

    override suspend fun completeQuest(campaignId: Long, questId: String) {
        questDao.completeQuest(campaignId, questId)
    }

    override suspend fun getCompletedQuests(campaignId: Long): List<Quest> =
        questDao.getCompletedQuestsList(campaignId).map { it.toDomain() }

    override suspend fun getAvailableQuests(campaignId: Long): List<Quest> =
        questDao.getAvailableQuestsList(campaignId).map { it.toDomain() }

    override suspend fun getAllBosses(): List<Boss> =
        database.bossDao().getAllBosses().map { it.toDomain() }

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
