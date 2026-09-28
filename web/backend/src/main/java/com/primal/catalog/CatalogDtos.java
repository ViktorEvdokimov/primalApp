package com.primal.catalog;

import com.primal.common.api.ApiNullable;
import com.primal.rules.model.StanceChangeMode;
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

    public record Achievement(String code, String name) {

        static Achievement of(AchievementDef def) {
            return new Achievement(def.code(), def.name());
        }
    }
}
