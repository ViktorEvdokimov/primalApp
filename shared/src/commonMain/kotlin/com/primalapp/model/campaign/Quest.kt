package com.primalapp.model.campaign

data class Quest(
    val id: String,
    val name: String,
    val chapter: Int,
    val element: Element? = null,
    val isCompleted: Boolean = false,
    val isAvailable: Boolean = false
)
