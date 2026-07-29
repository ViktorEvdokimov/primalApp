package com.primalapp.repository

import com.primalapp.model.campaign.Achievement
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
import com.primalapp.domain.ExchangeResult

interface CampaignRepository {

    suspend fun getAllCampaigns(): List<Campaign>
    suspend fun getCampaign(id: Long): Campaign?
    suspend fun createCampaign(name: String): Long
    suspend fun saveCampaign(campaign: Campaign)
    suspend fun deleteCampaign(id: Long)
    suspend fun getCampaignCount(): Int
    suspend fun getMaxCampaigns(): Int

    suspend fun getHunters(campaignId: Long): List<CampaignHunter>
    suspend fun addHunters(campaignId: Long, hunters: List<CampaignHunter>)

    suspend fun getSkills(hunterId: Long): List<SkillNode>
    suspend fun unlockSkill(hunterId: Long, branch: SkillBranch, tier: Int)
    suspend fun getAvailableSkillBranches(hunterId: Long): List<SkillBranch>

    suspend fun getMaterials(hunterId: Long): Map<Material, Int>
    suspend fun getPlants(hunterId: Long): Map<Plant, Int>
    suspend fun getElements(hunterId: Long): Map<Element, Int>
    suspend fun updateResource(hunterId: Long, resourceType: ResourceType, resourceName: String, quantity: Int)
    suspend fun addResource(hunterId: Long, resourceType: ResourceType, resourceName: String, amount: Int)
    suspend fun getHuntersWithResource(campaignId: Long, resourceName: String, resourceType: ResourceType): List<CampaignHunter>
    suspend fun exchangeResources(
        fromHunterId: Long, toHunterId: Long,
        fromResources: List<Pair<String, Int>>,
        toResources: List<Pair<String, Int>>,
        resourceType: ResourceType
    ): ExchangeResult

    suspend fun advanceChapter(campaignId: Long)
    suspend fun updateChapter(campaignId: Long, chapter: Int)
    suspend fun getForgeLevel(campaignId: Long): Int
    suspend fun getLabLevel(campaignId: Long): Int

    suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?)

    suspend fun getAchievements(campaignId: Long): List<Achievement>
    suspend fun saveAchievement(campaignId: Long, achievement: Achievement)

    suspend fun getTrophies(campaignId: Long): List<Trophy>
    suspend fun saveTrophy(campaignId: Long, trophy: Trophy)

    suspend fun getQuests(campaignId: Long): List<Quest>
    suspend fun saveQuest(campaignId: Long, quest: Quest)
    suspend fun completeQuest(campaignId: Long, questId: String)
    suspend fun getCompletedQuests(campaignId: Long): List<Quest>
    suspend fun getAvailableQuests(campaignId: Long): List<Quest>
}
