package com.primal.catalog;

import com.primal.rules.model.Element;
import com.primal.rules.model.ForgeSlot;
import com.primal.rules.model.Material;
import java.util.List;
import java.util.Map;

/**
 * Предмет планшета кузни ({@code catalog/forge.yaml}).
 *
 * @param code    {@code FIRE_01}…{@code FIRE_12}: стихия и место на планшете — одинаковы на всех уровнях
 * @param costs   материи по уровням кузни: элемент 0 — 1-й уровень; кроме них, создание стоит 1 стихию
 */
public record ForgeItemDef(
        String code,
        Element element,
        String name,
        ForgeSlot slot,
        List<Map<Material, Integer>> costs) {

    public static final int LEVELS = 3;

    /** Материи на планшете кузни уровня {@code level} (1–3). */
    public Map<Material, Integer> cost(int level) {
        return costs.get(level - 1);
    }
}
