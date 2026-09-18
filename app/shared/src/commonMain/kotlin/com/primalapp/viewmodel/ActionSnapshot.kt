package com.primalapp.viewmodel

data class ActionSnapshot(
    val monster: MonsterSnapshot,
    val phase: FightPhase,
    val actionType: ActionType,
    val description: String
)

enum class ActionType { DAMAGE, RAGE, PHASE_CHANGE, ROUND_END }
