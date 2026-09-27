package com.primalapp.viewmodel

data class ActionSnapshot(
    val monster: MonsterSnapshot,
    val phase: FightPhase,
    val actionType: ActionType,
    val description: String,
    /** Номер раунда до действия — отмена «Закончить раунд» возвращает и его (D-4). */
    val round: Int = 1
)

enum class ActionType { DAMAGE, RAGE, PHASE_CHANGE, ROUND_END, HEAL }
