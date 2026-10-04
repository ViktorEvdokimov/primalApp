package com.primal.catalog;

import com.primal.rules.model.Element;
import com.primal.rules.model.Expansion;
import com.primal.rules.model.ForgeSlot;
import com.primal.rules.model.Material;
import com.primal.rules.model.Plant;
import com.primal.rules.model.StanceChangeMode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            "catalog/bosses.yaml", "catalog/achievements.yaml", "catalog/quests.yaml", "catalog/chapters.yaml",
            "catalog/forge.yaml", "catalog/lab.yaml");

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
                parseForge(tree(files, "catalog/forge.yaml")),
                parseLab(tree(files, "catalog/lab.yaml")),
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
                    EffectParser.effects(node.path("expired"), where + ", истечение"),
                    json(node.path("victory")),
                    json(node.path("expired"))));
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

    /** Состав планшета: 8 видов оружия по одному, шлем, доспех и 2 предмета (правила, «Кузня»). */
    private static final Map<ForgeSlot, Integer> BOARD = boardSlots();

    private static Map<ForgeSlot, Integer> boardSlots() {
        Map<ForgeSlot, Integer> slots = new EnumMap<>(ForgeSlot.class);
        for (ForgeSlot slot : ForgeSlot.values()) {
            slots.put(slot, slot == ForgeSlot.ITEM ? 2 : 1);
        }
        return Collections.unmodifiableMap(slots);
    }

    /**
     * Планшеты кузни: каждая стихия ровно один раз, на планшете — состав {@link #BOARD}, у каждого предмета
     * цены на 3 уровня по 2 материи. Код предмета — стихия и место на планшете: {@code FIRE_01}.
     */
    static List<ForgeItemDef> parseForge(JsonNode root) {
        List<ForgeItemDef> items = new ArrayList<>();
        Set<Element> elements = new HashSet<>();
        for (JsonNode board : list(root, "forge.yaml")) {
            Element element = enumOrNull(Element.class, board.path("element"), "forge.yaml");
            String where = "forge.yaml, " + element;
            if (element == null || !elements.add(element)) {
                throw new CatalogException(where + ": стихия не указана или повторяется");
            }
            Map<ForgeSlot, Integer> slots = new EnumMap<>(ForgeSlot.class);
            int position = 0;
            for (JsonNode node : board.path("items")) {
                position++;
                String name = text(node, "name");
                String at = where + ", " + name;
                ForgeSlot slot = enumOrNull(ForgeSlot.class, node.path("slot"), at);
                if (slot == null) {
                    throw new CatalogException(at + ": не указан slot");
                }
                slots.merge(slot, 1, Integer::sum);
                items.add(new ForgeItemDef("%s_%02d".formatted(element, position), element, name, slot,
                        parseCosts(node.path("cost"), at)));
            }
            if (!slots.equals(BOARD)) {
                throw new CatalogException(where + ": на планшете должны быть " + BOARD + ", а есть " + slots);
            }
        }
        if (elements.size() != Element.values().length) {
            throw new CatalogException("forge.yaml: нужны планшеты всех " + Element.values().length + " стихий, а есть "
                    + elements.size());
        }
        return List.copyOf(items);
    }

    private static List<Map<Material, Integer>> parseCosts(JsonNode cost, String where) {
        if (!cost.isArray() || cost.size() != ForgeItemDef.LEVELS) {
            throw new CatalogException(where + ": cost — цены на " + ForgeItemDef.LEVELS + " уровня");
        }
        List<Map<Material, Integer>> costs = new ArrayList<>();
        int level = 0;
        for (JsonNode units : cost) {
            level++;
            if (!units.isArray() || units.size() != 2) {
                throw new CatalogException(where + ", уровень " + level + ": на планшете 2 материи");
            }
            Map<Material, Integer> materials = new EnumMap<>(Material.class);
            for (JsonNode unit : units) {
                Material material = enumOrNull(Material.class, unit, where + ", уровень " + level);
                if (material == null) {
                    throw new CatalogException(where + ", уровень " + level + ": пустая материя");
                }
                materials.merge(material, 1, Integer::sum);
            }
            costs.add(Collections.unmodifiableMap(materials));
        }
        return List.copyOf(costs);
    }

    /** Зелий на планшете лаборатории (правила, «Лаборатория»). */
    static final int LAB_POTIONS = 6;

    /**
     * Планшет лаборатории: 6 зелий, у каждого 2 растения. Растение — код, «A/B» (одно на выбор) или {@code ANY}
     * (любое). Код зелья — место на планшете: {@code LAB_01}.
     */
    static List<LabPotionDef> parseLab(JsonNode root) {
        List<LabPotionDef> potions = new ArrayList<>();
        for (JsonNode node : list(root, "lab.yaml")) {
            String name = text(node, "name");
            String where = "lab.yaml, " + name;
            JsonNode cost = node.path("cost");
            if (!cost.isArray() || cost.size() != 2) {
                throw new CatalogException(where + ": на планшете 2 растения");
            }
            List<Set<Plant>> units = new ArrayList<>();
            for (JsonNode unit : cost) {
                units.add(parsePlants(unit.asString(), where));
            }
            potions.add(new LabPotionDef("LAB_%02d".formatted(potions.size() + 1), name, List.copyOf(units)));
        }
        if (potions.size() != LAB_POTIONS) {
            throw new CatalogException("lab.yaml: зелий должно быть " + LAB_POTIONS + ", а их " + potions.size());
        }
        return List.copyOf(potions);
    }

    private static Set<Plant> parsePlants(String unit, String where) {
        if ("ANY".equals(unit)) {
            return Collections.unmodifiableSet(EnumSet.allOf(Plant.class));
        }
        // Порядок планшета: «Антемон / Меллис» — так и подпись, и выбор по умолчанию
        Set<Plant> plants = new LinkedHashSet<>();
        for (String code : unit.split("/")) {
            try {
                plants.add(Plant.valueOf(code.strip()));
            } catch (IllegalArgumentException e) {
                throw new CatalogException(where + ": неизвестное растение " + code, e);
            }
        }
        return Collections.unmodifiableSet(plants);
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
