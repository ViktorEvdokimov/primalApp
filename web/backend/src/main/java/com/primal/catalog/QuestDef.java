package com.primal.catalog;

import com.primal.rules.effects.Effect;
import com.primal.rules.model.Expansion;
import java.util.List;

/**
 * Задание каталога.
 *
 * @param victory     эффекты победы (без общих правил: трофей, 2 стихии босса, выполнение задания)
 * @param expired     последствия невыполненного задания — применяются, когда истекает его время (правила,
 *                    «Последствия невыполненных заданий»), а не при поражении
 * @param victoryJson эффекты победы в JSON — копия для {@code quest_def.victory_effects}
 * @param expiredJson последствия в JSON — копия для {@code quest_def.expired_effects}
 */
public record QuestDef(
        int number,
        String name,
        String bossCode,
        Expansion expansion,
        List<Effect> victory,
        List<Effect> expired,
        String victoryJson,
        String expiredJson) {
}
