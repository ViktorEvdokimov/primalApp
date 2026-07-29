package com.primalapp.model.campaign

data class Achievement(
    val id: String,
    val name: String,
    val description: String = "",
    val unlocked: Boolean = false
)
