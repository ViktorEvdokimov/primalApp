package com.primalapp.model.campaign

data class CampaignHunter(
    val id: Long = 0,
    val campaignId: Long = 0,
    val playerName: String,
    val className: HunterClass
)
