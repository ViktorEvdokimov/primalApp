package com.primal.catalog;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Правки каталога администратором: награды заданий и глав (qa № 137), цены кузни и лаборатории, стойки боссов
 * (qa № 138). Хранятся в {@code quest_override}, {@code chapter_override}, {@code forge_override},
 * {@code lab_override}, {@code boss_override} на
 * языке YAML каталога и накладываются на каталог из YAML при старте и после каждой правки. Правка проверяется тем
 * же разбором (и для наград — той же ссылочной целостностью), что и YAML, — неверная не сохраняется. Права
 * проверяет вызывающий ({@code admin}).
 */
@Service
public class CatalogEditor {

    /** Материй на уровне цены кузни при правке: на планшетах — по 2. */
    static final int FORGE_UNITS_MAX = 4;
    /** Растений в цене зелья при правке: на планшете — по 2. */
    static final int LAB_UNITS_MAX = 4;

    /** Что изменено администратором: номера заданий и глав, коды предметов кузни и зелий. */
    public record Edited(Set<Integer> quests, Set<Integer> chapters, Set<String> forge, Set<String> lab,
                         Set<String> bosses) {
    }

    /** Правка задания: награды за победу и последствия невыполненного задания. */
    record QuestEffects(String victory, String expired) {
    }

    /** Все правки из БД, JSON — как в YAML. */
    record Overrides(Map<Integer, QuestEffects> quests, Map<Integer, String> chapters, Map<String, String> forge,
                     Map<String, String> lab, Map<String, String> bosses) {

        boolean isEmpty() {
            return quests.isEmpty() && chapters.isEmpty() && forge.isEmpty() && lab.isEmpty() && bosses.isEmpty();
        }
    }

    private final CatalogService catalog;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    CatalogEditor(CatalogService catalog, JdbcTemplate jdbc, Clock clock) {
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PostConstruct
    void load() {
        catalog.replace(build(overrides()));
    }

    public Edited edited() {
        Overrides overrides = overrides();
        return new Edited(Set.copyOf(overrides.quests().keySet()), Set.copyOf(overrides.chapters().keySet()),
                Set.copyOf(overrides.forge().keySet()), Set.copyOf(overrides.lab().keySet()),
                Set.copyOf(overrides.bosses().keySet()));
    }

    /** Новые награды задания; {@code victory} и {@code expired} — списки эффектов на языке каталога. */
    @Transactional
    public QuestDef editQuest(int number, JsonNode victory, JsonNode expired, long adminId) {
        quest(number);
        parse(() -> EffectParser.effects(victory, "Победа"), "victory");
        parse(() -> EffectParser.effects(expired, "Истечение"), "expired");
        Overrides overrides = overrides();
        overrides.quests().put(number, new QuestEffects(CatalogLoader.json(victory), CatalogLoader.json(expired)));
        Catalog next = checked(build(overrides));
        jdbc.update("""
                insert into quest_override (number, victory_effects, expired_effects, updated_at, updated_by)
                values (?, ?::jsonb, ?::jsonb, ?, ?)
                on conflict (number) do update set victory_effects = excluded.victory_effects,
                    expired_effects = excluded.expired_effects, updated_at = excluded.updated_at,
                    updated_by = excluded.updated_by""",
                number, CatalogLoader.json(victory), CatalogLoader.json(expired), Timestamp.from(clock.instant()), adminId);
        catalog.replace(next);
        return next.quest(number).orElseThrow();
    }

    /** «Вернуть исходные»: награды задания снова из YAML. */
    @Transactional
    public QuestDef resetQuest(int number) {
        quest(number);
        jdbc.update("delete from quest_override where number = ?", number);
        load();
        return catalog.quest(number).orElseThrow();
    }

    /** Новые эффекты главы (решения главы не меняются). */
    @Transactional
    public ChapterDef editChapter(int number, JsonNode effects, long adminId) {
        chapter(number);
        parse(() -> EffectParser.effects(effects, "Глава"), "effects");
        Overrides overrides = overrides();
        overrides.chapters().put(number, CatalogLoader.json(effects));
        Catalog next = checked(build(overrides));
        jdbc.update("""
                insert into chapter_override (chapter, effects, updated_at, updated_by) values (?, ?::jsonb, ?, ?)
                on conflict (chapter) do update set effects = excluded.effects, updated_at = excluded.updated_at,
                    updated_by = excluded.updated_by""",
                number, CatalogLoader.json(effects), Timestamp.from(clock.instant()), adminId);
        catalog.replace(next);
        return next.chapter(number).orElseThrow();
    }

    @Transactional
    public ChapterDef resetChapter(int number) {
        chapter(number);
        jdbc.update("delete from chapter_override where chapter = ?", number);
        load();
        return catalog.chapter(number).orElseThrow();
    }

    /**
     * Новая цена предмета кузни: {@code cost} — как в forge.yaml, материи по уровням
     * {@code [[BONES, BLOOD], [SCALES, BLOOD], [BONES, BLOOD]]}; на уровне от 1 до {@value #FORGE_UNITS_MAX} материй.
     */
    @Transactional
    public ForgeItemDef editForge(String code, JsonNode cost, long adminId) {
        forgeItem(code);
        parse(() -> CatalogLoader.parseCosts(cost, "Цена", 1, FORGE_UNITS_MAX), "costs");
        Overrides overrides = overrides();
        overrides.forge().put(code, CatalogLoader.json(cost));
        Catalog next = build(overrides);
        jdbc.update("""
                insert into forge_override (code, cost, updated_at, updated_by) values (?, ?::jsonb, ?, ?)
                on conflict (code) do update set cost = excluded.cost, updated_at = excluded.updated_at,
                    updated_by = excluded.updated_by""",
                code, CatalogLoader.json(cost), Timestamp.from(clock.instant()), adminId);
        catalog.replace(next);
        return next.forgeItem(code).orElseThrow();
    }

    @Transactional
    public ForgeItemDef resetForge(String code) {
        forgeItem(code);
        jdbc.update("delete from forge_override where code = ?", code);
        load();
        return catalog.forgeItem(code).orElseThrow();
    }

    /**
     * Новая цена зелья: {@code cost} — как в lab.yaml, растения по одному: {@code ["TARMARET", "ANTHEMON/MELLIS"]},
     * {@code ANY} — любое; от 1 до {@value #LAB_UNITS_MAX} растений.
     */
    @Transactional
    public LabPotionDef editLab(String code, JsonNode cost, long adminId) {
        labPotion(code);
        parse(() -> CatalogLoader.parseLabCost(cost, "Цена", 1, LAB_UNITS_MAX), "units");
        Overrides overrides = overrides();
        overrides.lab().put(code, CatalogLoader.json(cost));
        Catalog next = build(overrides);
        jdbc.update("""
                insert into lab_override (code, cost, updated_at, updated_by) values (?, ?::jsonb, ?, ?)
                on conflict (code) do update set cost = excluded.cost, updated_at = excluded.updated_at,
                    updated_by = excluded.updated_by""",
                code, CatalogLoader.json(cost), Timestamp.from(clock.instant()), adminId);
        catalog.replace(next);
        return next.labPotion(code).orElseThrow();
    }

    @Transactional
    public LabPotionDef resetLab(String code) {
        labPotion(code);
        jdbc.update("delete from lab_override where code = ?", code);
        load();
        return catalog.labPotion(code).orElseThrow();
    }

    /**
     * Новые стойки босса: {@code stances} — как в bosses.yaml, по уровням враждебности; уровни — те же, что в
     * каталоге, на уровне от 3 до 9 стоек.
     */
    @Transactional
    public BossDef editBoss(String code, JsonNode stances, long adminId) {
        BossDef original = boss(code);
        parse(() -> {
            var parsed = CatalogLoader.parseBossStances(stances, "Стойки");
            if (!parsed.keySet().equals(original.stances().keySet())) {
                throw new CatalogException("Стойки: уровни враждебности должны быть " + original.stances().keySet()
                        + ", а указаны " + parsed.keySet());
            }
        }, "stances");
        Overrides overrides = overrides();
        overrides.bosses().put(code, CatalogLoader.json(stances));
        Catalog next = build(overrides);
        jdbc.update("""
                insert into boss_override (code, stances, updated_at, updated_by) values (?, ?::jsonb, ?, ?)
                on conflict (code) do update set stances = excluded.stances, updated_at = excluded.updated_at,
                    updated_by = excluded.updated_by""",
                code, CatalogLoader.json(stances), Timestamp.from(clock.instant()), adminId);
        catalog.replace(next);
        return next.boss(code).orElseThrow();
    }

    @Transactional
    public BossDef resetBoss(String code) {
        boss(code);
        jdbc.update("delete from boss_override where code = ?", code);
        load();
        return catalog.boss(code).orElseThrow();
    }

    private Catalog build(Overrides overrides) {
        Catalog base = catalog.base();
        if (overrides.isEmpty()) {
            return base;
        }
        List<BossDef> bosses = new ArrayList<>();
        for (BossDef boss : base.bosses()) {
            String edited = overrides.bosses().get(boss.code());
            bosses.add(edited == null ? boss : new BossDef(boss.code(), boss.name(), boss.element(), boss.expansion(),
                    boss.sortOrder(), CatalogLoader.parseBossStances(CatalogLoader.readJson(edited), boss.code())));
        }
        List<QuestDef> quests = new ArrayList<>();
        for (QuestDef quest : base.quests()) {
            QuestEffects edited = overrides.quests().get(quest.number());
            quests.add(edited == null ? quest : new QuestDef(quest.number(), quest.name(), quest.bossCode(),
                    quest.expansion(),
                    EffectParser.effects(CatalogLoader.readJson(edited.victory()), "задание " + quest.number() + ", победа"),
                    EffectParser.effects(CatalogLoader.readJson(edited.expired()), "задание " + quest.number() + ", истечение"),
                    edited.victory(), edited.expired()));
        }
        List<ChapterDef> chapters = new ArrayList<>();
        for (ChapterDef chapter : base.chapters()) {
            String edited = overrides.chapters().get(chapter.chapter());
            chapters.add(edited == null ? chapter : new ChapterDef(chapter.chapter(),
                    EffectParser.effects(CatalogLoader.readJson(edited), "глава " + chapter.chapter()),
                    chapter.decisions(), edited, chapter.decisionsJson()));
        }
        List<ForgeItemDef> forge = new ArrayList<>();
        for (ForgeItemDef item : base.forge()) {
            String edited = overrides.forge().get(item.code());
            forge.add(edited == null ? item : new ForgeItemDef(item.code(), item.element(), item.name(), item.slot(),
                    CatalogLoader.parseCosts(CatalogLoader.readJson(edited), item.code(), 1, FORGE_UNITS_MAX)));
        }
        List<LabPotionDef> lab = new ArrayList<>();
        for (LabPotionDef potion : base.lab()) {
            String edited = overrides.lab().get(potion.code());
            lab.add(edited == null ? potion : new LabPotionDef(potion.code(), potion.name(),
                    CatalogLoader.parseLabCost(CatalogLoader.readJson(edited), potion.code(), 1, LAB_UNITS_MAX)));
        }
        String revision = Integer.toHexString(overrides.toString().hashCode());
        return base.with(List.copyOf(bosses), List.copyOf(quests), List.copyOf(chapters), List.copyOf(forge),
                List.copyOf(lab), base.checksum() + "." + revision);
    }

    /** Ссылки на задания, достижения и боссов существуют — иначе {@code 400} с перечнем. */
    private static Catalog checked(Catalog next) {
        List<String> problems = CatalogIntegrity.problems(next);
        if (!problems.isEmpty()) {
            throw invalid("effects", "Неверные ссылки: " + String.join("; ", problems) + ".");
        }
        return next;
    }

    /** Разбор правки тем же кодом, что и YAML; ошибка разбора — {@code 400} с полем. */
    private static void parse(Runnable parser, String field) {
        try {
            parser.run();
        } catch (CatalogException e) {
            throw invalid(field, e.getMessage());
        }
    }

    private Overrides overrides() {
        Map<Integer, QuestEffects> quests = new LinkedHashMap<>();
        jdbc.query("select number, victory_effects::text, expired_effects::text from quest_override order by number",
                rs -> {
                    quests.put(rs.getInt(1), new QuestEffects(rs.getString(2), rs.getString(3)));
                });
        return new Overrides(quests,
                texts("select chapter, effects::text from chapter_override order by chapter", Integer.class),
                texts("select code, cost::text from forge_override order by code", String.class),
                texts("select code, cost::text from lab_override order by code", String.class),
                texts("select code, stances::text from boss_override order by code", String.class));
    }

    private <K> Map<K, String> texts(String sql, Class<K> keyType) {
        Map<K, String> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getObject(1, keyType), rs.getString(2));
        });
        return result;
    }

    private QuestDef quest(int number) {
        return catalog.originalQuest(number)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Задания " + number + " нет."));
    }

    private ChapterDef chapter(int number) {
        return catalog.originalChapter(number)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Главы " + number + " нет."));
    }

    private ForgeItemDef forgeItem(String code) {
        return catalog.base().forgeItem(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Предмета кузни " + code + " нет."));
    }

    private BossDef boss(String code) {
        return catalog.base().boss(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Босса " + code + " нет."));
    }

    private LabPotionDef labPotion(String code) {
        return catalog.base().labPotion(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Зелья " + code + " нет."));
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
