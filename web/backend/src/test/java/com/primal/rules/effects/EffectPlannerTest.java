package com.primal.rules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.catalog.CatalogService;
import com.primal.rules.effects.EffectDescriber.Context;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Паритет с {@code ConditionOutcomesTest.kt}: формулировки правил и результаты для окон наград. */
@DisplayName("Планировщик эффектов")
class EffectPlannerTest {

    private static final CatalogService CATALOG = new CatalogService();
    private static final EffectPlanner PLANNER = CATALOG.planner();

    private static CampaignFacts facts(int chapter, Set<String> achievements, Set<Integer> quests) {
        return new CampaignFacts(achievements, chapter, quests);
    }

    private static Plan questVictory(int number, CampaignFacts facts) {
        return PLANNER.plan(CATALOG.quest(number).orElseThrow().victory(), facts, Context.QUEST);
    }

    private static Plan chapter(int number, CampaignFacts facts) {
        return PLANNER.plan(CATALOG.chapter(number).orElseThrow().effects(), facts, Context.CHAPTER);
    }

    private static RuleExplanation rule(Plan plan, String descriptionEnd) {
        return plan.explanations().stream().filter(r -> r.description().endsWith(descriptionEnd)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Условия заданий")
    class Quests {

        @Test
        @DisplayName("задание 1: в главе 1 — задание 4, в главе 3 — задание 6")
        void quest1() {
            // вызов
            Plan inChapter = questVictory(1, facts(1, Set.of(), Set.of()));
            Plan otherChapter = questVictory(1, facts(3, Set.of(), Set.of()));

            // проверка
            assertThat(inChapter.explanations()).containsExactly(new RuleExplanation(
                    "Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6", "Добавлено задание 4."));
            assertThat(inChapter.openedQuests()).containsExactly(4);
            assertThat(otherChapter.explanations().getFirst().result()).isEqualTo("Добавлено задание 6.");
            assertThat(otherChapter.openedQuests()).containsExactly(6);
        }

        @Test
        @DisplayName("задание 2: невыполненное условие без «иначе» не добавляет задание")
        void quest2() {
            // вызов
            Plan plan = questVictory(2, facts(4, Set.of(), Set.of()));

            // проверка
            assertThat(plan.explanations()).containsExactly(new RuleExplanation(
                    "Если текущая глава 1 или 2, то добавить задание 5", "Условие не выполнено, задание не добавляется."));
            assertThat(plan.openedQuests()).isEmpty();
        }

        @Test
        @DisplayName("задание 11: с достижением — 32, без него — 17")
        void quest11() {
            // вызов
            Plan with = questVictory(11, facts(5, Set.of("GRIBNOY_LES"), Set.of()));
            Plan without = questVictory(11, facts(5, Set.of(), Set.of()));

            // проверка
            assertThat(with.explanations().getFirst()).isEqualTo(new RuleExplanation(
                    "Если есть достижение «Грибной лес», добавить задание 32, иначе добавить задание 17", "Добавлено задание 32."));
            assertThat(without.explanations().getFirst().result()).isEqualTo("Добавлено задание 17.");
        }

        @Test
        @DisplayName("задание 25: при «Горящем угольке» в главе 8 — 34, в другой главе — 27, без достижения — ничего")
        void quest25() {
            // вызов
            Plan chapter8 = questVictory(25, facts(8, Set.of("GORYASHCHIY_UGOLEK"), Set.of()));
            Plan chapter6 = questVictory(25, facts(6, Set.of("GORYASHCHIY_UGOLEK"), Set.of()));
            Plan without = questVictory(25, facts(8, Set.of(), Set.of()));

            // проверка
            assertThat(chapter8.openedQuests()).containsExactly(34);
            assertThat(chapter8.explanations().getFirst().result()).isEqualTo("Добавлено задание 34.");
            assertThat(chapter6.openedQuests()).containsExactly(27);
            assertThat(without.openedQuests()).isEmpty();
            assertThat(without.explanations().getFirst().result()).isEqualTo("Достижения нет, задание не добавляется.");
        }

        @Test
        @DisplayName("задания 29 и 40: условное достижение «Уробборос» только при «Голосе Волтьяра»")
        void conditionalAchievement() {
            for (int number : new int[] {29, 40}) {
                // вызов
                Plan has = questVictory(number, facts(9, Set.of("GOLOS_VOLTYARA"), Set.of()));
                Plan hasNot = questVictory(number, facts(9, Set.of(), Set.of()));
                Plan already = questVictory(number, facts(9, Set.of("GOLOS_VOLTYARA", "UROBBOROS"), Set.of()));

                // проверка
                RuleExplanation rule = rule(has, "«Уробборос»");
                assertThat(rule.description()).isEqualTo("Если есть достижение «Голос Волтьяра», добавить достижение «Уробборос»");
                assertThat(rule.result()).isEqualTo("Достижение есть, добавлено достижение «Уробборос».");
                assertThat(has.actions()).contains(new Effect.GrantAchievement("UROBBOROS", null));
                assertThat(rule(hasNot, "«Уробборос»").result()).isEqualTo("Достижения нет.");
                assertThat(hasNot.actions()).doesNotContain(new Effect.GrantAchievement("UROBBOROS", null));
                assertThat(rule(already, "«Уробборос»").result()).isEqualTo("Достижение «Уробборос» уже получено.");
            }
        }

        @Test
        @DisplayName("задание 42: задание 45 — только если задание 18 ещё не доступно")
        void quest42() {
            // вызов
            Plan notAvailable = questVictory(42, facts(6, Set.of(), Set.of(30, 31)));
            Plan available = questVictory(42, facts(6, Set.of(), Set.of(18)));

            // проверка
            assertThat(notAvailable.explanations()).containsExactly(new RuleExplanation(
                    "Если задание 18 ещё не доступно, добавить задание 45", "Добавлено задание 45."));
            assertThat(available.explanations().getFirst().result()).isEqualTo("Условие не выполнено, задание не добавляется.");
            assertThat(available.openedQuests()).isEmpty();
        }

        @Test
        @DisplayName("безусловные награды попадают в план как есть")
        void unconditional() {
            // вызов
            Plan plan = questVictory(1, facts(1, Set.of(), Set.of()));

            // проверка
            assertThat(plan.actions().getFirst()).isInstanceOf(Effect.Resources.class);
        }
    }

    @Nested
    @DisplayName("Условия глав")
    class Chapters {

        @Test
        @DisplayName("глава 4: условные задания 7/8 и 9, условное сообщение, кузня и лаборатория")
        void chapter4() {
            // вызов
            Plan with = chapter(4, facts(3, Set.of("NAROD_ZOLOTYKH_GOR", "YAD_PAZISA"), Set.of()));
            Plan without = chapter(4, facts(3, Set.of(), Set.of()));

            // проверка
            assertThat(with.openedQuests()).containsExactly(11, 7);
            assertThat(without.openedQuests()).containsExactly(11, 8);
            assertThat(rule(with, "иначе добавить задание 8").description())
                    .isEqualTo("Если есть достижение «Народ Золотых гор», открыть задание 7, иначе добавить задание 8");
            assertThat(rule(without, "задание 9").result()).isEqualTo("Условие не выполнено, задание не добавляется.");
            assertThat(rule(with, "Получите награду 25")).isEqualTo(new RuleExplanation(
                    "Если есть достижение «Яд Пазиса»: Получите награду 25", "Достижение есть: Получите награду 25."));
            assertThat(rule(without, "Получите награду 25").result()).isEqualTo("Достижения нет.");
            assertThat(with.actions()).contains(new Effect.ForgeLevelUp(null), new Effect.LabLevelUp(null));
        }

        @Test
        @DisplayName("глава 5: без «Упавшей звезды» — альтернативное задание 47")
        void chapter5() {
            // вызов
            Plan plan = chapter(5, facts(4, Set.of(), Set.of()));

            // проверка
            assertThat(rule(plan, "иначе добавить задание 47")).isEqualTo(new RuleExplanation(
                    "Если есть достижение «Упавшая звезда», открыть задание 15, иначе добавить задание 47", "Добавлено задание 47."));
        }

        @Test
        @DisplayName("глава 6: «Змеиная кровь» — задание 42, иначе 43")
        void chapter6() {
            // вызов и проверка
            assertThat(chapter(6, facts(5, Set.of("ZMEINAYA_KROV"), Set.of())).openedQuests()).containsExactly(22, 42);
            assertThat(chapter(6, facts(5, Set.of(), Set.of())).openedQuests()).containsExactly(22, 43);
        }

        @Test
        @DisplayName("глава 9: отрицание и «все достижения»")
        void chapter9() {
            // вызов
            Plan plan = chapter(9, facts(8, Set.of("UPAVSHAYA_ZVEZDA"), Set.of()));

            // проверка
            assertThat(plan.explanations().get(0)).isEqualTo(new RuleExplanation(
                    "Если нет достижения «Звезда дракона», добавить задание 28", "Добавлено задание 28."));
            assertThat(plan.explanations().get(1)).isEqualTo(new RuleExplanation(
                    "Если есть достижения «Упавшая звезда» и «Звезда дракона», добавить задание 49",
                    "Условие не выполнено, задание не добавляется."));
        }

        @Test
        @DisplayName("глава 10 (C-8): задание 30, если нет хотя бы одного из «Три копья» и «Эхо водопада»")
        void chapter10Quest30() {
            // вызов
            Plan one = chapter(10, facts(9, Set.of("TRI_KOPYA"), Set.of()));
            Plan both = chapter(10, facts(9, Set.of("TRI_KOPYA", "EKHO_VODOPADA"), Set.of()));

            // проверка
            assertThat(rule(one, "задание 30")).isEqualTo(new RuleExplanation(
                    "Если нет хотя бы одного из достижений «Три копья» и «Эхо водопада», добавить задание 30",
                    "Добавлено задание 30."));
            assertThat(one.openedQuests()).contains(29, 30).doesNotContain(40);
            assertThat(both.openedQuests()).contains(29, 40).doesNotContain(30);
        }

        @Test
        @DisplayName("глава 10 (C-9) и глава 8: условное улучшение набора охотника")
        void kitUpgrade() {
            // вызов
            Plan has = chapter(8, facts(7, Set.of("GOLOS_VOLTYARA"), Set.of()));
            Plan hasNot = chapter(8, facts(7, Set.of(), Set.of()));
            Plan chapter10 = chapter(10, facts(9, Set.of("GOLOS_VOLTYARA"), Set.of()));

            // проверка
            assertThat(rule(has, "улучшение набора охотника")).isEqualTo(new RuleExplanation(
                    "Если есть достижение «Голос Волтьяра» — улучшение набора охотника", "Достижение есть, улучшите набор охотника."));
            assertThat(rule(hasNot, "улучшение набора охотника").result()).isEqualTo("Достижения нет.");
            assertThat(chapter10.actions()).contains(new Effect.HunterKitUpgrade(null));
        }

        @Test
        @DisplayName("безусловное улучшение набора (глава 3) не становится условным правилом")
        void unconditionalKit() {
            // вызов
            Plan plan = chapter(3, facts(2, Set.of(), Set.of()));

            // проверка
            assertThat(plan.explanations()).noneMatch(r -> r.description().contains("набора"));
            assertThat(plan.actions()).contains(new Effect.HunterKitUpgrade(null));
        }

        @Test
        @DisplayName("глава 11: истечение всех заданий и финальный бой")
        void chapter11() {
            // вызов
            Plan plan = chapter(11, facts(10, Set.of(), Set.of()));

            // проверка
            assertThat(plan.actions()).contains(new Effect.ExpireAllQuests(null), new Effect.FinalBattle("AWAKENED", null));
        }
    }

    @Nested
    @DisplayName("Снимок кампании")
    class Snapshot {

        @Test
        @DisplayName("достижение, выдаваемое тем же набором, не влияет на его условия")
        void grantedInSameSet() {
            // подготовка: набор выдаёт «Голос Волтьяра» и сразу проверяет его
            List<Effect> effects = List.of(
                    new Effect.GrantAchievement("GOLOS_VOLTYARA", null),
                    new Effect.Conditional(new Condition.HasAchievement("GOLOS_VOLTYARA"),
                            List.of(new Effect.OpenQuest(24, null)), List.of(), null));

            // вызов
            Plan plan = PLANNER.plan(effects, facts(7, Set.of(), Set.of()), Context.CHAPTER);

            // проверка
            assertThat(plan.openedQuests()).isEmpty();
            assertThat(plan.actions()).containsExactly(new Effect.GrantAchievement("GOLOS_VOLTYARA", null));
        }

        @Test
        @DisplayName("задание, открытое тем же набором, не делает его «доступным» для условий")
        void openedInSameSet() {
            // подготовка
            List<Effect> effects = List.of(
                    new Effect.OpenQuest(18, null),
                    new Effect.Conditional(new Condition.Not(new Condition.QuestAvailable(18)),
                            List.of(new Effect.OpenQuest(45, null)), List.of(), null));

            // вызов
            Plan plan = PLANNER.plan(effects, facts(6, Set.of(), Set.of()), Context.QUEST);

            // проверка
            assertThat(plan.openedQuests()).containsExactly(18, 45);
        }
    }

    @Test
    @DisplayName("у всех условий каталога есть формулировка без пропусков")
    void allCatalogRulesDescribed() {
        // подготовка
        CampaignFacts empty = facts(1, Set.of(), Set.of());

        // вызов
        List<RuleExplanation> rules = new java.util.ArrayList<>();
        CATALOG.quests().forEach(quest -> {
            rules.addAll(PLANNER.plan(quest.victory(), empty, Context.QUEST).explanations());
            rules.addAll(PLANNER.plan(quest.defeat(), empty, Context.QUEST).explanations());
        });
        CATALOG.chapters().forEach(chapter -> rules.addAll(PLANNER.plan(chapter.effects(), empty, Context.CHAPTER).explanations()));

        // проверка
        assertThat(rules).isNotEmpty().allSatisfy(rule -> {
            assertThat(rule.description()).startsWith("Если ").doesNotContain("«»", "?");
            assertThat(rule.result()).isNotBlank();
        });
    }
}
