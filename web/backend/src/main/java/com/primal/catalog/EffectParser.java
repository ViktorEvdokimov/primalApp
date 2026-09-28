package com.primal.catalog;

import com.primal.rules.effects.Condition;
import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import com.primal.rules.model.Expansion;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.ResourceKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Разбор языка эффектов из YAML (doc/data-model.md §4.3): вид эффекта определяется по имени ключа
 * ({@code openQuest}, {@code if}, …). Модуль {@code rules} остаётся чистой Java — разбором занимается каталог.
 */
final class EffectParser {

    private static final Set<String> COMMON_KEYS = Set.of("expansion");

    private EffectParser() {
    }

    static List<Effect> effects(JsonNode list, String where) {
        if (list == null || list.isMissingNode() || list.isNull()) {
            return List.of();
        }
        if (!list.isArray()) {
            throw new CatalogException(where + ": ожидается список эффектов");
        }
        List<Effect> effects = new ArrayList<>();
        int index = 1;
        for (JsonNode node : list) {
            effects.add(effect(node, where + ", эффект " + index++));
        }
        return List.copyOf(effects);
    }

    static Effect effect(JsonNode node, String where) {
        if (!node.isObject()) {
            throw new CatalogException(where + ": эффект должен быть объектом");
        }
        Expansion expansion = expansion(node.path("expansion"), where);
        List<String> keys = node.propertyNames().stream().filter(key -> !COMMON_KEYS.contains(key)).toList();
        if (keys.contains("if")) {
            return new Effect.Conditional(
                    condition(node.get("if"), where + ", if"),
                    effects(node.path("then"), where + ", then"),
                    effects(node.path("else"), where + ", else"),
                    expansion);
        }
        if (keys.size() != 1) {
            throw new CatalogException(where + ": у эффекта должен быть ровно один вид, а указано " + keys);
        }
        String kind = keys.getFirst();
        JsonNode value = node.get(kind);
        return switch (kind) {
            case "resources" -> new Effect.Resources(resources(value, where), expansion);
            case "openQuest" -> new Effect.OpenQuest(integer(value, where), expansion);
            case "expireQuests" -> new Effect.ExpireQuests(integers(value, where), expansion);
            case "expireAllQuests" -> flag(value, where, new Effect.ExpireAllQuests(expansion));
            case "grantAchievement" -> new Effect.GrantAchievement(string(value, where), expansion);
            case "forgeLevelUp" -> flag(value, where, new Effect.ForgeLevelUp(expansion));
            case "labLevelUp" -> flag(value, where, new Effect.LabLevelUp(expansion));
            case "hunterKitUpgrade" -> flag(value, where, new Effect.HunterKitUpgrade(expansion));
            case "rewardCards" -> new Effect.RewardCards(strings(value, where), expansion);
            case "message" -> new Effect.Message(string(value, where), expansion);
            case "finalBattle" -> new Effect.FinalBattle(string(value, where), expansion);
            default -> throw new CatalogException(where + ": неизвестный вид эффекта " + kind);
        };
    }

    static Condition condition(JsonNode node, String where) {
        if (node == null || !node.isObject() || node.size() != 1) {
            throw new CatalogException(where + ": условие — объект с одним ключом");
        }
        String kind = node.propertyNames().iterator().next();
        JsonNode value = node.get(kind);
        return switch (kind) {
            case "achievement" -> new Condition.HasAchievement(string(value, where));
            case "chapterIn" -> new Condition.ChapterIn(integers(value, where));
            case "questAvailable" -> new Condition.QuestAvailable(integer(value, where));
            case "not" -> new Condition.Not(condition(value, where + ", not"));
            case "all" -> new Condition.All(conditions(value, where + ", all"));
            case "any" -> new Condition.Any(conditions(value, where + ", any"));
            default -> throw new CatalogException(where + ": неизвестное условие " + kind);
        };
    }

    static List<Decision> decisions(JsonNode list, String where) {
        if (list == null || list.isMissingNode() || list.isNull()) {
            return List.of();
        }
        List<Decision> decisions = new ArrayList<>();
        for (JsonNode node : list) {
            String code = string(node.path("code"), where + ", код решения");
            List<Decision.Option> options = new ArrayList<>();
            for (JsonNode option : node.path("options")) {
                String optionCode = string(option.path("code"), where + ", " + code);
                options.add(new Decision.Option(optionCode, string(option.path("label"), where + ", " + code),
                        effects(option.path("effects"), where + ", " + code + "." + optionCode)));
            }
            if (options.size() < 2) {
                throw new CatalogException(where + ", " + code + ": у решения должно быть не меньше двух вариантов");
            }
            decisions.add(new Decision(code, string(node.path("question"), where + ", " + code), options));
        }
        return List.copyOf(decisions);
    }

    private static List<Condition> conditions(JsonNode list, String where) {
        if (!list.isArray() || list.isEmpty()) {
            throw new CatalogException(where + ": ожидается непустой список условий");
        }
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode node : list) {
            conditions.add(condition(node, where));
        }
        return conditions;
    }

    private static Map<ResourceCode, Integer> resources(JsonNode node, String where) {
        Map<ResourceCode, Integer> items = new EnumMap<>(ResourceCode.class);
        for (String key : node.propertyNames()) {
            ResourceCode code;
            try {
                code = ResourceCode.valueOf(key);
            } catch (IllegalArgumentException e) {
                throw new CatalogException(where + ": неизвестный ресурс " + key, e);
            }
            if (code.kind() == ResourceKind.ELEMENT) {
                throw new CatalogException(where + ": стихии даёт общее правило победы, а не эффект задания (" + key + ")");
            }
            int quantity = integer(node.get(key), where);
            if (quantity <= 0) {
                throw new CatalogException(where + ": количество " + key + " должно быть больше 0");
            }
            items.put(code, quantity);
        }
        return items;
    }

    private static Effect flag(JsonNode value, String where, Effect effect) {
        if (!value.isBoolean() || !value.asBoolean()) {
            throw new CatalogException(where + ": флаг эффекта записывается как true");
        }
        return effect;
    }

    private static Expansion expansion(JsonNode value, String where) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return Expansion.valueOf(value.asString());
        } catch (IllegalArgumentException e) {
            throw new CatalogException(where + ": неизвестное дополнение " + value.asString(), e);
        }
    }

    private static int integer(JsonNode value, String where) {
        if (!value.isInt()) {
            throw new CatalogException(where + ": ожидается целое число, а указано " + value);
        }
        return value.asInt();
    }

    private static List<Integer> integers(JsonNode value, String where) {
        if (!value.isArray()) {
            throw new CatalogException(where + ": ожидается список чисел");
        }
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode item : value) {
            numbers.add(integer(item, where));
        }
        return numbers;
    }

    private static String string(JsonNode value, String where) {
        if (value == null || !(value.isString() || value.isNumber()) || value.asString().isBlank()) {
            throw new CatalogException(where + ": ожидается строка");
        }
        return value.asString();
    }

    private static List<String> strings(JsonNode value, String where) {
        if (!value.isArray()) {
            throw new CatalogException(where + ": ожидается список строк");
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            items.add(string(item, where));
        }
        return items;
    }
}
