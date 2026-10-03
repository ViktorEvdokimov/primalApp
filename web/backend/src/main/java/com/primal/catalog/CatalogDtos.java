package com.primal.catalog;

import com.primal.common.api.ApiNullable;
import com.primal.rules.model.Element;
import com.primal.rules.model.ForgeSlot;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.Plant;
import com.primal.rules.model.StanceChangeMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ответы API каталога (doc/api.md §4). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** Код и русское название значения перечисления; {@code expansion} — только у стихий. */
    public record Named(String code, String name, @ApiNullable String expansion) {
    }

    public record DifficultyRange(List<Integer> chapters, int difficulty) {
    }

    public record Dictionaries(
            List<Named> elements,
            List<Named> materials,
            List<Named> plants,
            List<Named> hunterClasses,
            List<Named> skillBranches,
            List<DifficultyRange> difficultyByChapter) {
    }

    public record StanceChange(StanceChangeMode mode, @ApiNullable Integer atHealth) {
    }

    public record Stance(int stance, @ApiNullable Integer toughnessPerHunter, StanceChange stanceChange) {

        public static Stance of(StanceDef def) {
            return new Stance(def.stance(), def.toughnessPerHunter(), new StanceChange(def.changeMode(), def.changeAtHealth()));
        }
    }

    /** @param difficulties стойки по уровням враждебности: ключ — «0»…«3», только доступные уровни */
    public record Boss(String code, String name, @ApiNullable String element, @ApiNullable String expansion, int sortOrder,
                       Map<String, List<Stance>> difficulties) {

        static Boss of(BossDef def) {
            Map<String, List<Stance>> difficulties = new LinkedHashMap<>();
            def.stances().forEach((level, stances) ->
                    difficulties.put(String.valueOf(level), stances.stream().map(Stance::of).toList()));
            return new Boss(def.code(), def.name(),
                    def.element() == null ? null : def.element().name(),
                    def.expansion() == null ? null : def.expansion().name(),
                    def.sortOrder(), difficulties);
        }
    }

    /** Материи предмета на планшете уровня {@code level}; кроме них, создание стоит 1 стихию кузни. */
    public record ForgeCost(int level, Map<String, Integer> materials) {
    }

    /** @param hunterClass класс, которому принадлежит оружие; {@code null} — шлем, доспех, предмет */
    public record ForgeItem(String code, String name, ForgeSlot slot, @ApiNullable HunterClass hunterClass,
                            List<ForgeCost> costs) {

        static ForgeItem of(ForgeItemDef def) {
            List<ForgeCost> costs = new ArrayList<>();
            for (int level = 1; level <= ForgeItemDef.LEVELS; level++) {
                Map<String, Integer> materials = new LinkedHashMap<>();
                def.cost(level).forEach((material, quantity) -> materials.put(material.name(), quantity));
                costs.add(new ForgeCost(level, materials));
            }
            return new ForgeItem(def.code(), def.name(), def.slot(), def.slot().hunterClass(), costs);
        }
    }

    /** Планшет кузни стихии: 12 предметов в порядке планшета. */
    public record ForgeBoard(Element element, List<ForgeItem> items) {
    }

    /** Растение цены зелья: {@code options} — допустимые растения (одно на выбор); {@code any} — любое. */
    public record LabUnit(List<Plant> options, boolean any) {
    }

    /** Зелье лаборатории: 2 растения, одинаково на всех уровнях. */
    public record LabPotion(String code, String name, List<LabUnit> units) {

        static LabPotion of(LabPotionDef def) {
            return new LabPotion(def.code(), def.name(), def.units().stream()
                    .map(unit -> new LabUnit(List.copyOf(unit), unit.size() == Plant.values().length))
                    .toList());
        }
    }

    public record Achievement(String code, String name) {

        static Achievement of(AchievementDef def) {
            return new Achievement(def.code(), def.name());
        }
    }
}
