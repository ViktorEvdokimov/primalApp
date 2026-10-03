package com.primal.catalog;

import com.primal.rules.model.Plant;
import java.util.List;
import java.util.Set;

/**
 * Зелье планшета лаборатории ({@code catalog/lab.yaml}). Цена одинакова на всех уровнях.
 *
 * @param code  {@code LAB_01}…{@code LAB_06} — место на планшете
 * @param units растения по одному: каждое — набор допустимых растений (одно на выбор; все 6 — любое)
 */
public record LabPotionDef(String code, String name, List<Set<Plant>> units) {

    /** Растение подходит для единицы цены {@code index}. */
    public boolean accepts(int index, Plant plant) {
        return units.get(index).contains(plant);
    }
}
