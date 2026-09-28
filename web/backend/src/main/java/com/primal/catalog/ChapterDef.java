package com.primal.catalog;

import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import java.util.List;

/**
 * Глава кампании: инструкции этапа сюжета, применяются при переходе в главу (doc/data-model.md §4.3).
 *
 * @param effectsJson   эффекты в JSON — копия для {@code chapter_def.effects}
 * @param decisionsJson решения в JSON — копия для {@code chapter_def.decisions}
 */
public record ChapterDef(int chapter, List<Effect> effects, List<Decision> decisions, String effectsJson, String decisionsJson) {
}
