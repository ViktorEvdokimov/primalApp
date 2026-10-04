package com.primal.catalog;

import static com.primal.catalog.AppSeeds.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.primal.rules.effects.Condition;
import com.primal.rules.effects.Effect;
import com.primal.rules.model.Expansion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Каталог: задания")
class QuestCatalogTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    @Nested
    @DisplayName("Состав")
    class Contents {

        @Test
        @DisplayName("49 заданий с номерами 1–49")
        void allQuests() {
            // вызов
            List<Integer> numbers = CATALOG.quests().stream().map(QuestDef::number).toList();

            // проверка
            assertThat(numbers).isEqualTo(IntStream.rangeClosed(1, 49).boxed().toList());
        }

        @Test
        @DisplayName("задание 25: при «Горящем угольке» в главе 8 и с «Кошмаром» — задание 34, иначе 27 (qa № 139)")
        void quest25NestedCondition() {
            // вызов
            Effect.Conditional rule = conditionals(quest(25).victory()).getFirst();

            // проверка
            assertThat(rule.condition()).isEqualTo(new Condition.HasAchievement("GORYASHCHIY_UGOLEK"));
            Effect.Conditional nested = (Effect.Conditional) rule.then().getFirst();
            assertThat(nested.condition()).isEqualTo(new Condition.All(List.of(
                    new Condition.ChapterIn(List.of(8)), new Condition.HasExpansion(Expansion.NIGHTMARE))));
            assertThat(nested.then()).containsExactly(new Effect.OpenQuest(34, null));
            assertThat(nested.otherwise()).containsExactly(new Effect.OpenQuest(27, null));
        }

        @Test
        @DisplayName("задание 2 (qa № 139): при истечении — задание 31")
        void quest2Expired() {
            // вызов и проверка
            assertThat(quest(2).expired()).containsExactly(new Effect.OpenQuest(31, null));
        }

        @Test
        @DisplayName("задание 12 (qa № 139): задание 18 — только если задание 45 ещё не доступно; пометка «Яд»")
        void quest12QuestNotAvailable() {
            // вызов
            Effect.Conditional rule = conditionals(quest(12).victory()).getFirst();

            // проверка
            assertThat(rule.condition()).isEqualTo(new Condition.Not(new Condition.QuestAvailable(45)));
            assertThat(rule.then()).containsExactly(new Effect.OpenQuest(18, null));
            assertThat(rule.expansion()).isEqualTo(Expansion.POISON);
            assertThat(openQuests(quest(12).victory())).isEmpty();
        }

        @Test
        @DisplayName("задание 42: задание 45 открывается, только если задание 18 ещё не доступно")
        void quest42QuestNotAvailable() {
            // вызов
            Effect.Conditional rule = conditionals(quest(42).victory()).getFirst();

            // проверка
            assertThat(rule.condition()).isEqualTo(new Condition.Not(new Condition.QuestAvailable(18)));
            assertThat(rule.then()).containsExactly(new Effect.OpenQuest(45, null));
        }

        @Test
        @DisplayName("задания 47 и 48: при истечении выдаётся «Оледенение» (R-7, qa 135)")
        void expiryAchievements() {
            // вызов и проверка
            for (int number : new int[] {47, 48}) {
                assertThat(quest(number).expired()).contains(new Effect.GrantAchievement("OLEDENENIE", null));
            }
        }

        @Test
        @DisplayName("дополнение задания — по номеру: 1–30 базовая, 31–35 «Кошмар», 36–40 «Перо», 41–45 «Яд», 46–50 «Лёд»")
        void expansionByNumber() {
            // вызов и проверка
            for (QuestDef quest : CATALOG.quests()) {
                int n = quest.number();
                Expansion expected = n <= 30 ? null : n <= 35 ? Expansion.NIGHTMARE : n <= 40 ? Expansion.FEATHER
                        : n <= 45 ? Expansion.POISON : Expansion.ICE;
                assertThat(quest.expansion()).as("задание " + n).isEqualTo(expected);
            }
        }
    }

    @Nested
    @DisplayName("Паритет с TaskInfoSeed.kt мобильного приложения")
    class Parity {

        /**
         * Награды, намеренно изменённые после переноса из app (qa № 139): задание 2 при истечении добавляет
         * задание 31; задание 12 добавляет задание 18, только если задание 45 ещё не доступно. Их проверяют
         * тесты содержимого выше.
         */
        private static final Set<Integer> CHANGED_AFTER_APP = Set.of(2, 12);

        @Test
        @DisplayName("строки app прочитаны: 49 заданий")
        void seedRead() {
            // подготовка
            Optional<List<List<String>>> seed = AppSeeds.taskRows();
            assumeTrue(seed.isPresent(), "исходники app рядом с web/");

            // вызов и проверка
            assertThat(seed.get()).hasSize(49);
        }

        @Test
        @DisplayName("босс, ресурсы, карты наград, открываемые задания и достижения совпадают у всех заданий")
        void unconditionalRewards() {
            // подготовка
            Optional<List<List<String>>> seed = AppSeeds.taskRows();
            assumeTrue(seed.isPresent(), "исходники app рядом с web/");

            // вызов и проверка
            for (List<String> row : seed.get()) {
                QuestDef quest = quest(Integer.parseInt(row.get(0)));
                if (CHANGED_AFTER_APP.contains(quest.number())) {
                    continue;
                }
                String where = "задание " + quest.number();
                assertThat(quest.name()).as(where).isEqualTo(row.get(1));
                assertThat(CATALOG.boss(quest.bossCode()).orElseThrow().name()).as(where).isEqualTo(row.get(2));
                assertThat(resources(quest.victory())).as(where + ": ресурсы")
                        .isEqualTo(seedResources(row.get(4), row.get(5)));
                assertThat(topLevel(quest.victory(), Effect.RewardCards.class).stream().flatMap(c -> c.cards().stream()).toList())
                        .as(where + ": карты наград").isEqualTo(split(row.get(9)));
                assertThat(openQuests(quest.victory())).as(where + ": задания победы").isEqualTo(numbers(row.get(6)));
                assertThat(achievementNames(quest.victory())).as(where + ": достижения победы").isEqualTo(split(row.get(8)));
                assertThat(topLevel(quest.victory(), Effect.Message.class).stream().map(Effect.Message::text).toList())
                        .as(where + ": особая награда").isEqualTo(split(row.get(10)));
                assertThat(openQuests(quest.expired())).as(where + ": задания при истечении").isEqualTo(numbers(row.get(11)));
                assertThat(achievementNames(quest.expired())).as(where + ": достижения при истечении")
                        .isEqualTo(split(column(row, 13)));
            }
        }

        @Test
        @DisplayName("условия открывают те же задания и выдают те же достижения, что в app")
        void conditionsHaveSameTargets() {
            // подготовка
            Optional<List<List<String>>> seed = AppSeeds.taskRows();
            assumeTrue(seed.isPresent(), "исходники app рядом с web/");

            // вызов и проверка
            for (List<String> row : seed.get()) {
                QuestDef quest = quest(Integer.parseInt(row.get(0)));
                if (CHANGED_AFTER_APP.contains(quest.number())) {
                    continue;
                }
                assertThat(conditionTargets(quest.victory())).as("задание " + quest.number() + ", победа")
                        .isEqualTo(seedConditionTargets(row.get(7)));
                assertThat(conditionTargets(quest.expired())).as("задание " + quest.number() + ", истечение")
                        .isEqualTo(seedConditionTargets(row.get(12)));
            }
        }
    }

    private static QuestDef quest(int number) {
        return CATALOG.quest(number).orElseThrow();
    }

    static List<Effect.Conditional> conditionals(List<Effect> effects) {
        return topLevel(effects, Effect.Conditional.class);
    }

    static <T extends Effect> List<T> topLevel(List<Effect> effects, Class<T> type) {
        return effects.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static Map<String, Integer> resources(List<Effect> effects) {
        Map<String, Integer> items = new LinkedHashMap<>();
        topLevel(effects, Effect.Resources.class).forEach(r -> r.items().forEach((k, v) -> items.put(k.name(), v)));
        return items;
    }

    private static Map<String, Integer> seedResources(String... columns) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String column : columns) {
            for (String part : split(column)) {
                String[] pair = part.split(":");
                items.put(pair[0].trim(), Integer.parseInt(pair[1].trim()));
            }
        }
        return items;
    }

    private static List<Integer> openQuests(List<Effect> effects) {
        return topLevel(effects, Effect.OpenQuest.class).stream().map(Effect.OpenQuest::quest).toList();
    }

    private static List<String> achievementNames(List<Effect> effects) {
        return topLevel(effects, Effect.GrantAchievement.class).stream()
                .map(grant -> CATALOG.achievement(grant.achievement()).orElseThrow().name()).toList();
    }

    /** Задания и достижения, которые могут появиться из условий (включая вложенные). */
    private static TreeSet<String> conditionTargets(List<Effect> effects) {
        TreeSet<String> targets = new TreeSet<>();
        for (Effect.Conditional conditional : conditionals(effects)) {
            collect(conditional, targets);
        }
        return targets;
    }

    private static void collect(Effect effect, TreeSet<String> targets) {
        switch (effect) {
            case Effect.OpenQuest open -> targets.add("задание " + open.quest());
            case Effect.GrantAchievement grant -> targets.add(CATALOG.achievement(grant.achievement()).orElseThrow().name());
            case Effect.Conditional conditional -> {
                conditional.then().forEach(e -> collect(e, targets));
                conditional.otherwise().forEach(e -> collect(e, targets));
            }
            default -> {
            }
        }
    }

    /** Цели условий app: {@code kind|achievement|chapterSet|quest|else|rewardAchievement}. */
    /** Условия app — {@code kind|achievement|chapterSet|quest|else|rewardAchievement} через «;» (в chapterSet — запятые). */
    private static TreeSet<String> seedConditionTargets(String conditions) {
        TreeSet<String> targets = new TreeSet<>();
        for (String condition : conditions.split(";")) {
            if (condition.isBlank()) {
                continue;
            }
            String[] parts = Arrays.copyOf(condition.split("\\|", -1), 6);
            for (int index : new int[] {3, 4}) {
                if (parts[index] != null && !parts[index].isBlank()) {
                    targets.add("задание " + parts[index].trim());
                }
            }
            if (parts[5] != null && !parts[5].isBlank()) {
                targets.add(parts[5].trim());
            }
        }
        return targets;
    }

    private static List<Integer> numbers(String column) {
        return split(column).stream().map(Integer::parseInt).toList();
    }

    /** Список из колонки seed: элементы через «;» или «,». */
    private static List<String> split(String column) {
        List<String> items = new ArrayList<>();
        for (String part : column.split("[;,]")) {
            if (!part.isBlank()) {
                items.add(part.trim());
            }
        }
        return items;
    }
}
