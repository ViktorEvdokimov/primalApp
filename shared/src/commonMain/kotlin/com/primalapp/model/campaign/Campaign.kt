package com.primalapp.model.campaign

data class Campaign(
    val id: Long = 0,
    val name: String,
    val currentChapter: Int = 1,
    val forgeLevel: Int = 1,
    val labLevel: Int = 1,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
