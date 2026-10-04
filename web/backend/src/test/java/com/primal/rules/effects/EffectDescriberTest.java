package com.primal.rules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.catalog.CatalogService;
import com.primal.rules.effects.EffectDescriber.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Формулировки правил для игроков совпадают с окнами наград app ({@code ConditionOutcomes.kt}). */
@DisplayName("Формулировки правил заданий и глав")
class EffectDescriberTest {

    private static final CatalogService CATALOG = new CatalogService();

    @Nested
    @DisplayName("Задания")
    class Quests {

        @Test
        @DisplayName("задание 1: условие по главе с «иначе»")
        void quest1() {
            assertThat(victoryRules(1)).containsExactly("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6");
        }

        @Test
        @DisplayName("задание 2: одно и то же условие при победе и поражении")
        void quest2() {
            assertThat(victoryRules(2)).containsExactly("Если текущая глава 1 или 2, то добавить задание 5");
            assertThat(expiryRules(2)).containsExactly("Если текущая глава 1 или 2, то добавить задание 5");
        }

        @Test
        @DisplayName("задание 11: условие по достижению с «иначе»")
        void quest11() {
            assertThat(victoryRules(11)).containsExactly("Если есть достижение «Грибной лес», добавить задание 32, иначе добавить задание 17");
        }

        @Test
        @DisplayName("задание 25: вложенное условие описывается после двоеточия")
        void quest25() {
            assertThat(victoryRules(25)).containsExactly(
                    "Если есть достижение «Горящий уголёк»: если текущая глава 8, то добавить задание 34, иначе добавить задание 27");
        }

        @Test
        @DisplayName("задания 29 и 40: условное достижение")
        void quests29And40() {
            for (int number : new int[] {29, 40}) {
                assertThat(victoryRules(number)).containsExactly("Если есть достижение «Голос Волтьяра», добавить достижение «Уробборос»");
            }
        }

        @Test
        @DisplayName("задание 42: задание ещё не доступно")
        void quest42() {
            assertThat(victoryRules(42)).containsExactly("Если задание 18 ещё не доступно, добавить задание 45");
        }
    }

    @Nested
    @DisplayName("Главы")
    class Chapters {

        @Test
        @DisplayName("глава 4: «открыть задание …, иначе добавить», условное сообщение")
        void chapter4() {
            assertThat(chapterRules(4)).containsExactly(
                    "Если есть достижение «Народ Золотых гор», открыть задание 7, иначе добавить задание 8",
                    "Если есть достижение «Затишье», добавить задание 9",
                    "Если есть достижение «Яд Пазиса»: Получите награду 25");
        }

        @Test
        @DisplayName("глава 5: четыре условных задания")
        void chapter5() {
            assertThat(chapterRules(5)).containsExactly(
                    "Если есть достижение «Тайны прошлого», добавить задание 12",
                    "Если есть достижение «Пыль аркеума», добавить задание 13",
                    "Если есть достижение «Лагерь в джунглях», открыть задание 37, иначе добавить задание 38",
                    "Если есть достижение «Упавшая звезда», открыть задание 15, иначе добавить задание 47");
        }

        @Test
        @DisplayName("глава 9: «нет достижения» и «есть достижения … и …»")
        void chapter9() {
            assertThat(chapterRules(9)).containsExactly(
                    "Если нет достижения «Звезда дракона», добавить задание 28",
                    "Если есть достижения «Упавшая звезда» и «Звезда дракона», добавить задание 49");
        }

        @Test
        @DisplayName("глава 10: «нет хотя бы одного из достижений» (C-8) и условное улучшение набора (C-9)")
        void chapter10() {
            assertThat(chapterRules(10)).containsExactly(
                    "Если есть достижение «Три копья», добавить задание 29",
                    "Если есть достижение «Эхо водопада», добавить задание 40",
                    "Если есть достижения «Неоплаченный долг» и «Гербарий», добавить задание 35",
                    "Если нет хотя бы одного из достижений «Три копья» и «Эхо водопада», добавить задание 30",
                    "Если есть достижение «Голос Волтьяра» — улучшение набора охотника");
        }
    }

    private static java.util.List<String> victoryRules(int quest) {
        return CATALOG.describer().rules(CATALOG.quest(quest).orElseThrow().victory(), Context.QUEST);
    }

    private static java.util.List<String> expiryRules(int quest) {
        return CATALOG.describer().rules(CATALOG.quest(quest).orElseThrow().expired(), Context.QUEST);
    }

    private static java.util.List<String> chapterRules(int chapter) {
        return CATALOG.describer().rules(CATALOG.chapter(chapter).orElseThrow().effects(), Context.CHAPTER);
    }
}
