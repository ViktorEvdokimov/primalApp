package com.primalapp.model.campaign

data class SkillNode(
    val branch: SkillBranch,
    val tier: Int,
    val unlocked: Boolean = false
)
