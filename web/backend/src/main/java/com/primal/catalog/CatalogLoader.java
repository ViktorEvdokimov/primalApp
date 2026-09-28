package com.primal.catalog;

import com.primal.rules.model.Element;
import com.primal.rules.model.Expansion;
import com.primal.rules.model.StanceChangeMode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Читает каталог из YAML в {@code classpath:catalog/} (doc/data-model.md §4). Им пользуются и миграция
 * {@code R__Catalog} (каталог → таблицы БД), и {@link CatalogService} (каталог в памяти), поэтому данные в БД
 * и в памяти всегда одни и те же. Ошибка в данных — {@link CatalogException} с указанием файла и места.
 */
public final class CatalogLoader {

    /** Файлы каталога; все входят в контрольную сумму. */
    public static final List<String> FILES = List.of(
            "catalog/bosses.yaml", "catalog/achievements.yaml", "catalog/quests.yaml", "catalog/chapters.yaml");

    private static final YAMLMapper YAML = new YAMLMapper();
    private static final JsonMapper JSON = new JsonMapper();

    private CatalogLoader() {
    }

    public static Catalog load() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String file : FILES) {
            files.put(file, read(file));
        }
        Catalog catalog = new Catalog(
                parseBosses(tree(files, "catalog/bosses.yaml")),
                parseAchievements(tree(files, "catalog/achievements.yaml")),
                parseQuests(tree(files, "catalog/quests.yaml")),
                parseChapters(tree(files, "catalog/chapters.yaml")),
                checksum(files));
        validate(catalog);
        return catalog;
    }

    /** Ссылочная целостность: все задания, достижения и боссы, на которые есть ссылки, существуют. */
    static void validate(Catalog catalog) {
        List<String> problems = CatalogIntegrity.problems(catalog);
        if (!problems.isEmpty()) {
            throw new CatalogException("Каталог не целостен: " + String.join("; ", problems));
        }
    }

    static List<QuestDef> parseQuests(JsonNode root) {
        List<QuestDef> quests = new ArrayList<>();
        for (JsonNode node : list(root, "quests.yaml")) {
            int number = node.path("number").asInt();
            String where = "quests.yaml, задание " + number;
            quests.add(new QuestDef(
                    number,
                    text(node, "name"),
                    text(node, "boss"),
                    enumOrNull(Expansion.class, node.path("expansion"), where),
                    EffectParser.effects(node.path("victory"), where + ", победа"),
                    EffectParser.effects(node.path("defeat"), where + ", поражение"),
                    json(node.path("victory")),
                    json(node.path("defeat"))));
        }
        return List.copyOf(quests);
    }

    static List<ChapterDef> parseChapters(JsonNode root) {
        List<ChapterDef> chapters = new ArrayList<>();
        for (JsonNode node : list(root, "chapters.yaml")) {
            int chapter = node.path("chapter").asInt();
            String where = "chapters.yaml, глава " + chapter;
            chapters.add(new ChapterDef(
                    chapter,
                    EffectParser.effects(node.path("effects"), where),
                    EffectParser.decisions(node.path("decisions"), where),
                    json(node.path("effects")),
                    json(node.path("decisions"))));
        }
        return List.copyOf(chapters);
    }

    /** JSON-копия эффектов для {@code jsonb}; отсутствующий список — пустой массив. */
    private static String json(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "[]" : JSON.writeValueAsString(node);
    }

    static List<BossDef> parseBosses(JsonNode root) {
        List<BossDef> bosses = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode node : list(root, "bosses.yaml")) {
            String code = text(node, "code");
            String where = "bosses.yaml, " + code;
            if (!codes.add(code)) {
                throw new CatalogException(where + ": код босса повторяется");
            }
            SortedMap<Integer, List<StanceDef>> stances = new TreeMap<>();
            JsonNode stancesNode = node.path("stances");
            for (String difficulty : stancesNode.propertyNames()) {
                int level = Integer.parseInt(difficulty);
                if (level < 0 || level > 3) {
                    throw new CatalogException(where + ": уровень враждебности " + level + " вне 0–3");
                }
                stances.put(level, parseStances(stancesNode.get(difficulty), where + ", сложность " + level));
            }
            if (stances.isEmpty()) {
                throw new CatalogException(where + ": нет стоек");
            }
            bosses.add(new BossDef(
                    code,
                    text(node, "name"),
                    enumOrNull(Element.class, node.path("element"), where),
                    enumOrNull(Expansion.class, node.path("expansion"), where),
                    node.path("sortOrder").asInt(),
                    Collections.unmodifiableSortedMap(stances)));
        }
        return List.copyOf(bosses);
    }

    private static List<StanceDef> parseStances(JsonNode list, String where) {
        List<StanceDef> stances = new ArrayList<>();
        int number = 1;
        for (JsonNode stance : list) {
            JsonNode toughness = stance.path("t");
            Integer toughnessPerHunter = isEmpty(toughness) ? null : toughness.asInt();
            if (toughnessPerHunter != null && toughnessPerHunter <= 0) {
                throw new CatalogException(where + ", стойка " + number + ": прочность должна быть больше 0");
            }
            JsonNode change = stance.path("change");
            if (change.isInt()) {
                int health = change.asInt();
                if (health < 1 || health > 9) {
                    throw new CatalogException(where + ", стойка " + number + ": порог смены " + health + " вне 1–9");
                }
                stances.add(new StanceDef(number, toughnessPerHunter, StanceChangeMode.HEALTH, health));
            } else {
                StanceChangeMode mode = enumOrNull(StanceChangeMode.class, change, where + ", стойка " + number);
                if (mode == null || mode == StanceChangeMode.HEALTH) {
                    throw new CatalogException(where + ", стойка " + number + ": change — число, FINAL или ON_DEMAND");
                }
                stances.add(new StanceDef(number, toughnessPerHunter, mode, null));
            }
            number++;
        }
        if (stances.size() < 3 || stances.size() > 9) {
            throw new CatalogException(where + ": стоек должно быть от 3 до 9, а их " + stances.size());
        }
        return List.copyOf(stances);
    }

    static List<AchievementDef> parseAchievements(JsonNode root) {
        List<AchievementDef> achievements = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode node : list(root, "achievements.yaml")) {
            AchievementDef achievement = new AchievementDef(text(node, "code"), text(node, "name"));
            if (!codes.add(achievement.code())) {
                throw new CatalogException("achievements.yaml: код " + achievement.code() + " повторяется");
            }
            achievements.add(achievement);
        }
        return List.copyOf(achievements);
    }

    private static byte[] read(String file) {
        try (InputStream in = CatalogLoader.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new CatalogException("Нет файла каталога " + file);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode tree(Map<String, byte[]> files, String file) {
        try {
            return YAML.readTree(files.get(file));
        } catch (RuntimeException e) {
            throw new CatalogException(file + ": не удалось разобрать YAML — " + e.getMessage(), e);
        }
    }

    private static Iterable<JsonNode> list(JsonNode root, String file) {
        if (root == null || !root.isArray()) {
            throw new CatalogException(file + ": ожидается список");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() && !value.isNumber() || value.asString().isBlank()) {
            throw new CatalogException("Нет обязательного поля " + field + " в " + node);
        }
        return value.asString();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, JsonNode value, String where) {
        if (isEmpty(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.asString());
        } catch (IllegalArgumentException e) {
            throw new CatalogException(where + ": неизвестное значение " + value.asString() + " для " + type.getSimpleName(), e);
        }
    }

    /**
     * Пустое значение: нет поля, {@code null} или {@code ~}. Jackson 3 читает YAML по схеме 1.2, где {@code ~} —
     * строка, а в файлах каталога {@code ~} означает «нет значения» (doc/data-model.md §4.2).
     */
    private static boolean isEmpty(JsonNode value) {
        return value.isMissingNode() || value.isNull() || value.isString() && "~".equals(value.asString());
    }

    static String checksum(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.forEach((name, content) -> {
                digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(content);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
