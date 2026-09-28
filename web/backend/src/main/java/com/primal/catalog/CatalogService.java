package com.primal.catalog;

import com.primal.rules.effects.EffectDescriber;
import com.primal.rules.effects.EffectPlanner;
import com.primal.rules.model.AchievementNames;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Каталог игры в памяти: загружается один раз при старте из тех же YAML, что и таблицы БД. */
@Service
public class CatalogService {

    private final Catalog catalog = CatalogLoader.load();

    private final EffectDescriber describer = new EffectDescriber(this::achievementName, this::bossName);

    private final EffectPlanner planner = new EffectPlanner(describer, this::achievementName);

    public List<BossDef> bosses() {
        return catalog.bosses();
    }

    public Optional<BossDef> boss(String code) {
        return catalog.boss(code);
    }

    public List<AchievementDef> achievements() {
        return catalog.achievements();
    }

    public List<QuestDef> quests() {
        return catalog.quests();
    }

    public Optional<QuestDef> quest(int number) {
        return catalog.quest(number);
    }

    public List<ChapterDef> chapters() {
        return catalog.chapters();
    }

    public Optional<ChapterDef> chapter(int number) {
        return catalog.chapter(number);
    }

    /** Формулировки правил для игроков (названия достижений и боссов — из каталога). */
    public EffectDescriber describer() {
        return describer;
    }

    /** Планировщик эффектов: что сделают награды для этой кампании и почему. */
    public EffectPlanner planner() {
        return planner;
    }

    /** Достижение каталога по названию в любом написании (регистр, ё/е, пробелы — 42.1). */
    public Optional<AchievementDef> achievementByName(String name) {
        return catalog.achievements().stream().filter(def -> AchievementNames.matches(def.name(), name)).findFirst();
    }

    public String achievementName(String code) {
        return catalog.achievement(code).map(AchievementDef::name).orElse(code);
    }

    public String bossName(String code) {
        return catalog.boss(code).map(BossDef::name).orElse(code);
    }

    /** Контрольная сумма каталога — ETag ответов API каталога. */
    public String checksum() {
        return catalog.checksum();
    }
}
