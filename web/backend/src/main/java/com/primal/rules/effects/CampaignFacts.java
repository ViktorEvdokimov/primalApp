package com.primal.rules.effects;

import java.util.Set;

/**
 * Снимок кампании до применения набора эффектов: по нему вычисляются условия ({@code doc/data-model.md} §4.3).
 *
 * @param achievements    коды достижений отряда
 * @param chapter         текущая глава книги кампании
 * @param availableQuests добавленные задания: открытые и выполненные
 */
public record CampaignFacts(Set<String> achievements, int chapter, Set<Integer> availableQuests) {

    public CampaignFacts {
        achievements = Set.copyOf(achievements);
        availableQuests = Set.copyOf(availableQuests);
    }
}
