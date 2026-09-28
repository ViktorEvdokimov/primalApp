package com.primal.catalog;

import com.primal.rules.model.Element;
import com.primal.rules.model.Expansion;
import java.util.List;
import java.util.SortedMap;

/**
 * Босс каталога.
 *
 * @param element    стихия; {@code null} — без стихии (Пробуждённый)
 * @param expansion  дополнение; {@code null} — базовая игра или не указано
 * @param sortOrder  порядок в списке выбора (как в app)
 * @param stances    стойки по уровням враждебности 0–3; есть только доступные уровни
 */
public record BossDef(
        String code,
        String name,
        Element element,
        Expansion expansion,
        int sortOrder,
        SortedMap<Integer, List<StanceDef>> stances) {

    public List<StanceDef> stances(int difficulty) {
        return stances.getOrDefault(difficulty, List.of());
    }
}
