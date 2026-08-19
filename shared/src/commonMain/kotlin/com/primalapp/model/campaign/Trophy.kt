package com.primalapp.model.campaign

data class Trophy(
    val bossName: String,
    val element: Element?,
    val chapter: Int,
    val acquiredAt: Long = 0L
)
