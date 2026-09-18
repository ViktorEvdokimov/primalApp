package com.primalapp.domain

import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode

interface SkillValidator {
    fun canUnlock(branch: SkillBranch, tier: Int, existingSkills: List<SkillNode>): Boolean
    fun getAvailableBranches(existingSkills: List<SkillNode>): List<SkillBranch>
}
