package com.primal.catalog;

import com.primal.rules.effects.Effect;
import com.primal.rules.model.Expansion;
import java.util.List;

/**
 * Задание каталога.
 *
 * @param victory     эффекты победы (без общих правил: трофей, 2 стихии босса, выполнение задания)
 * @param defeat      эффекты поражения
 * @param victoryJson эффекты победы в JSON — копия для {@code quest_def.victory_effects}
 * @param defeatJson  эффекты поражения в JSON — копия для {@code quest_def.defeat_effects}
 */
public record QuestDef(
        int number,
        String name,
        String bossCode,
        Expansion expansion,
        List<Effect> victory,
        List<Effect> defeat,
        String victoryJson,
        String defeatJson) {
}
