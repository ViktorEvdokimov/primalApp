package com.primalapp.domain

import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode

class SkillValidatorImpl : SkillValidator {

    override fun canUnlock(branch: SkillBranch, tier: Int, existingSkills: List<SkillNode>): Boolean {
        val branchSkill = existingSkills.find { it.branch == branch && it.tier == tier }
            ?: return false
        if (branchSkill.unlocked) return false
        if (tier == 1) return true
        if (tier == 2) {
            val tier1 = existingSkills.find { it.branch == branch && it.tier == 1 }
            return tier1?.unlocked == true
        }
        return false
    }

    override fun getAvailableBranches(existingSkills: List<SkillNode>): List<SkillBranch> {
        return SkillBranch.entries.filter { branch ->
            val tier1 = existingSkills.find { it.branch == branch && it.tier == 1 }
            val tier2 = existingSkills.find { it.branch == branch && it.tier == 2 }
            when {
                tier1 == null -> false
                tier2 == null -> false
                tier2.unlocked -> false // fully completed
                !tier1.unlocked -> true // tier 1 available
                tier1.unlocked && !tier2.unlocked -> true // tier 2 available
                else -> false
            }
        }
    }
}
