package com.primalapp.catalog

import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance

/**
 * Босс каталога для экрана боя: стойки уровня враждебности [difficulty] в формате движка боя — прочность за
 * охотника и здоровье смены стойки (`0` — последняя стойка, `null` — смена по запросу).
 */
fun BossDef.toBattleBoss(difficulty: Int): Boss = Boss(
    name = name,
    element = element,
    difficulty = difficulty,
    stances = stances(difficulty).sortedBy { it.stance }.map { stance ->
        BossStance(
            damageForWound = stance.toughnessPerHunter,
            healthForStanceChange = when (stance.changeMode) {
                StanceChangeMode.HEALTH -> stance.changeAtHealth
                StanceChangeMode.FINAL -> 0
                StanceChangeMode.ON_DEMAND -> null
            }
        )
    }
)
