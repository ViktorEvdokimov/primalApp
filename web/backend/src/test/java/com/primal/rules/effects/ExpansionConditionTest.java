package com.primal.rules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.catalog.CatalogService;
import com.primal.rules.effects.EffectDescriber.Context;
import com.primal.rules.model.Expansion;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Условие «есть дополнение» (qa № 138)")
class ExpansionConditionTest {

    private static final CatalogService CATALOG = new CatalogService();

    /** Если есть «Перо» — задание 36, иначе — задание 6. */
    private static final List<Effect> EFFECTS = List.of(new Effect.Conditional(
            new Condition.HasExpansion(Expansion.FEATHER),
            List.of(new Effect.OpenQuest(36, null)),
            List.of(new Effect.OpenQuest(6, null)),
            null));

    private static Plan plan(Set<Expansion> expansions) {
        return CATALOG.planner().plan(EFFECTS, new CampaignFacts(Set.of(), 1, Set.of(), expansions), Context.QUEST);
    }

    @Test
    @DisplayName("есть «Перо» — ветвь «то», нет — «иначе»; по умолчанию дополнения все")
    void branches() {
        // вызов и проверка
        assertThat(plan(EnumSet.of(Expansion.FEATHER)).actions()).containsExactly(new Effect.OpenQuest(36, null));
        assertThat(plan(EnumSet.of(Expansion.ICE)).actions()).containsExactly(new Effect.OpenQuest(6, null));
        assertThat(new CampaignFacts(Set.of(), 1, Set.of()).expansions()).containsExactlyInAnyOrder(Expansion.values());
    }

    @Test
    @DisplayName("формулировка: «Если есть дополнение «Перо», добавить задание 36, иначе добавить задание 6»")
    void text() {
        // вызов и проверка
        assertThat(CATALOG.describer().rules(EFFECTS, Context.QUEST))
                .containsExactly("Если есть дополнение «Перо», добавить задание 36, иначе добавить задание 6");
        assertThat(CATALOG.describer().condition(new Condition.Not(new Condition.HasExpansion(Expansion.NIGHTMARE))))
                .isEqualTo("нет дополнения «Кошмар»");
    }
}
