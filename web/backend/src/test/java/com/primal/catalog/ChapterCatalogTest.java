package com.primal.catalog;

import static com.primal.catalog.AppSeeds.column;
import static com.primal.catalog.QuestCatalogTest.conditionals;
import static com.primal.catalog.QuestCatalogTest.topLevel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.primal.rules.effects.Condition;
import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Каталог: главы")
class ChapterCatalogTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    @Nested
    @DisplayName("Состав")
    class Contents {

        @Test
        @DisplayName("11 глав по порядку")
        void allChapters() {
            // вызов и проверка
            assertThat(CATALOG.chapters()).extracting(ChapterDef::chapter).isEqualTo(IntStream.rangeClosed(1, 11).boxed().toList());
        }

        @Test
        @DisplayName("глава 7: решение «Хотите ли вы тренироваться в лагере у Волтьяра?» — «Да» выдаёт «Голос Волтьяра»")
        void chapter7Decision() {
            // вызов
            Decision decision = chapter(7).decisions().getFirst();

            // проверка
            assertThat(decision.code()).isEqualTo("TRAIN_WITH_VOLTYAR");
            assertThat(decision.question()).isEqualTo("Хотите ли вы тренироваться в лагере у Волтьяра?");
            assertThat(decision.options()).extracting(Decision.Option::label).containsExactly("Да", "Нет");
            assertThat(decision.options().getFirst().effects()).containsExactly(new Effect.GrantAchievement("GOLOS_VOLTYARA", null));
            assertThat(decision.options().getLast().effects()).isEmpty();
        }

        @Test
        @DisplayName("главы 8 и 10: улучшение набора охотника только при «Голосе Волтьяра» (C-9)")
        void conditionalKitUpgrade() {
            // вызов и проверка
            for (int number : new int[] {8, 10}) {
                ChapterDef chapter = chapter(number);
                assertThat(topLevel(chapter.effects(), Effect.HunterKitUpgrade.class)).as("глава " + number).isEmpty();
                assertThat(conditionals(chapter.effects())).as("глава " + number).anySatisfy(rule -> {
                    assertThat(rule.condition()).isEqualTo(new Condition.HasAchievement("GOLOS_VOLTYARA"));
                    assertThat(rule.then()).containsExactly(new Effect.HunterKitUpgrade(null));
                });
            }
        }

        @Test
        @DisplayName("глава 10: задание 30 — если нет хотя бы одного из «Три копья» и «Эхо водопада» (C-8)")
        void chapter10Quest30() {
            // вызов и проверка
            assertThat(conditionals(chapter(10).effects())).anySatisfy(rule -> {
                assertThat(rule.condition()).isEqualTo(new Condition.Not(new Condition.All(List.of(
                        new Condition.HasAchievement("TRI_KOPYA"), new Condition.HasAchievement("EKHO_VODOPADA")))));
                assertThat(rule.then()).containsExactly(new Effect.OpenQuest(30, null));
            });
        }

        @Test
        @DisplayName("глава 11: истекают все задания, следующий бой — Пробуждённый (R-6)")
        void chapter11Final() {
            // вызов
            List<Effect> effects = chapter(11).effects();

            // проверка
            assertThat(effects).contains(new Effect.ExpireAllQuests(null), new Effect.FinalBattle("AWAKENED", null));
        }
    }

    @Nested
    @DisplayName("Паритет с ChapterInfoSeed.kt мобильного приложения")
    class Parity {

        @Test
        @DisplayName("ресурсы, задания, истечения, кузня, лаборатория, набор охотника и финал совпадают у всех глав")
        void effects() {
            // подготовка
            Optional<List<List<String>>> seed = AppSeeds.chapterRows();
            assumeTrue(seed.isPresent(), "исходники app рядом с web/");

            // вызов и проверка
            for (List<String> row : seed.get()) {
                ChapterDef chapter = chapter(Integer.parseInt(row.get(0)));
                List<Effect> effects = chapter.effects();
                String where = "глава " + chapter.chapter();
                assertThat(resources(effects)).as(where + ": ресурсы").isEqualTo(seedResources(row.get(1), row.get(2)));
                assertThat(topLevel(effects, Effect.OpenQuest.class)).extracting(Effect.OpenQuest::quest)
                        .as(where + ": задания").isEqualTo(numbers(row.get(3)));
                assertThat(conditionals(effects).stream().filter(r -> r.then().getFirst() instanceof Effect.OpenQuest).count())
                        .as(where + ": условные задания").isEqualTo(split(row.get(4)).size());
                assertThat(topLevel(effects, Effect.ExpireQuests.class).stream().flatMap(e -> e.quests().stream()).toList())
                        .as(where + ": истечения").isEqualTo(numbers(row.get(5)));
                assertThat(!topLevel(effects, Effect.ForgeLevelUp.class).isEmpty()).as(where + ": кузня").isEqualTo(row.get(6).equals("1"));
                assertThat(!topLevel(effects, Effect.LabLevelUp.class).isEmpty()).as(where + ": лаборатория").isEqualTo(row.get(7).equals("1"));
                boolean kit = !topLevel(effects, Effect.HunterKitUpgrade.class).isEmpty()
                        || conditionals(effects).stream().anyMatch(r -> r.then().getFirst() instanceof Effect.HunterKitUpgrade);
                assertThat(kit).as(where + ": набор охотника").isEqualTo(row.get(8).equals("1"));
                assertThat(chapter.decisions()).as(where + ": решения").hasSize(split(row.get(9)).size());
                assertThat(!topLevel(effects, Effect.ExpireAllQuests.class).isEmpty()).as(where + ": все задания истекают")
                        .isEqualTo(column(row, 13).equals("1"));
            }
        }
    }

    private static ChapterDef chapter(int number) {
        return CATALOG.chapter(number).orElseThrow();
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

    private static List<Integer> numbers(String column) {
        return split(column).stream().map(s -> Integer.parseInt(s.trim())).toList();
    }

    private static List<String> split(String column) {
        List<String> items = new ArrayList<>();
        for (String part : column.split(column.contains(";") ? ";" : ",")) {
            if (!part.isBlank()) {
                items.add(part.trim());
            }
        }
        return items;
    }
}
