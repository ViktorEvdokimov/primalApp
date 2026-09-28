package com.primal.rules.effects;

import java.util.List;

/**
 * Результат планировщика: действия без условий (условия уже вычислены) и пояснения к условным правилам.
 * Превью и применение используют один и тот же план.
 */
public record Plan(List<Effect> actions, List<RuleExplanation> explanations) {

    public Plan {
        actions = List.copyOf(actions);
        explanations = List.copyOf(explanations);
    }

    /** Задания, которые откроет план, по порядку. */
    public List<Integer> openedQuests() {
        return actions.stream()
                .filter(Effect.OpenQuest.class::isInstance)
                .map(effect -> ((Effect.OpenQuest) effect).quest())
                .toList();
    }

    /** Только действия заданного типа — например, открытие заданий при «Выполнено» вручную (qa 70). */
    public Plan only(Class<? extends Effect> type) {
        return new Plan(actions.stream().filter(type::isInstance).toList(), explanations);
    }
}
