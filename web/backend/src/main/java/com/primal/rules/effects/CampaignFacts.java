package com.primal.rules.effects;

import com.primal.rules.model.Expansion;
import java.util.EnumSet;
import java.util.Set;

/**
 * Снимок кампании до применения набора эффектов: по нему вычисляются условия ({@code doc/data-model.md} §4.3).
 *
 * @param achievements    коды достижений отряда
 * @param chapter         текущая глава книги кампании
 * @param availableQuests добавленные задания: открытые и выполненные
 * @param expansions      дополнения владельца кампании (настройка аккаунта)
 */
public record CampaignFacts(Set<String> achievements, int chapter, Set<Integer> availableQuests, Set<Expansion> expansions) {

    public CampaignFacts {
        achievements = Set.copyOf(achievements);
        availableQuests = Set.copyOf(availableQuests);
        expansions = Set.copyOf(expansions);
    }

    /** Со всеми дополнениями — как по умолчанию в настройках. */
    public CampaignFacts(Set<String> achievements, int chapter, Set<Integer> availableQuests) {
        this(achievements, chapter, availableQuests, EnumSet.allOf(Expansion.class));
    }
}
