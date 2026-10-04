package com.primal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.primal.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

@DisplayName("API каталога")
class CatalogApiIT extends IntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Nested
    @DisplayName("Ответы")
    class Responses {

        @Test
        @DisplayName("боссы: 23, стойки по уровням враждебности в формате api.md §4")
        void bosses() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/bosses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(23)))
                    .andExpect(jsonPath("$[?(@.code == 'VIRAXEN')].name").value("Вираксен"))
                    .andExpect(jsonPath("$[?(@.code == 'VIRAXEN')].difficulties.0[0].toughnessPerHunter").value(2))
                    .andExpect(jsonPath("$[?(@.code == 'VIRAXEN')].difficulties.0[0].stanceChange.mode").value("HEALTH"))
                    .andExpect(jsonPath("$[?(@.code == 'VIRAXEN')].difficulties.0[0].stanceChange.atHealth").value(7))
                    .andExpect(jsonPath("$[?(@.code == 'VIRAXEN')].difficulties.0[2].stanceChange.mode").value("FINAL"))
                    .andExpect(jsonPath("$[?(@.code == 'AWAKENED')].element").value((Object) null))
                    .andExpect(jsonPath("$[?(@.code == 'KOROVON')].difficulties.1[1].toughnessPerHunter").value((Object) null));
        }

        @Test
        @DisplayName("словари: стихии, материи, растения, классы, ветви навыков, уровни враждебности по главам")
        void dictionaries() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/dictionaries"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.elements", hasSize(9)))
                    .andExpect(jsonPath("$.elements[0].code").value("FIRE"))
                    .andExpect(jsonPath("$.elements[0].name").value("Огонь"))
                    .andExpect(jsonPath("$.elements[8].expansion").value("ICE"))
                    .andExpect(jsonPath("$.materials", hasSize(6)))
                    .andExpect(jsonPath("$.plants", hasSize(6)))
                    .andExpect(jsonPath("$.hunterClasses", hasSize(8)))
                    .andExpect(jsonPath("$.skillBranches[2].name").value("В"))
                    .andExpect(jsonPath("$.difficultyByChapter[3].chapters[0]").value(8))
                    .andExpect(jsonPath("$.difficultyByChapter[3].difficulty").value(3));
        }

        @Test
        @DisplayName("достижения: 23 с кодами")
        void achievements() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/achievements"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(23)))
                    .andExpect(jsonPath("$[?(@.code == 'GOLOS_VOLTYARA')].name").value("Голос Волтьяра"));
        }
    }

    @Nested
    @DisplayName("Задания и главы")
    class QuestsAndChapters {

        @Test
        @DisplayName("задание 1 — пример из api.md §4")
        void quest1() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/quests/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.number").value(1))
                    .andExpect(jsonPath("$.name").value("Память пустыни"))
                    .andExpect(jsonPath("$.boss.code").value("TORAMAT"))
                    .andExpect(jsonPath("$.boss.element").value("HORN"))
                    .andExpect(jsonPath("$.victory.resources.BONES").value(2))
                    .andExpect(jsonPath("$.victory.resources.SELICORNIA").value(1))
                    .andExpect(jsonPath("$.victory.openQuests", hasSize(0)))
                    .andExpect(jsonPath("$.victory.rules[0]")
                            .value("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6"))
                    .andExpect(jsonPath("$.expired.openQuests[0]").value(6));
        }

        @Test
        @DisplayName("несуществующее задание → 404 NOT_FOUND")
        void unknownQuest() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/quests/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("все 49 заданий и 11 глав; у главы 7 решение, у главы 11 финальный бой")
        void listsAndChapters() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/quests")).andExpect(jsonPath("$", hasSize(49)));
            mockMvc.perform(get("/api/v1/catalog/chapters"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(11)))
                    .andExpect(jsonPath("$[0].resources.BLOOD").value(2))
                    .andExpect(jsonPath("$[0].openQuests[2]").value(36))
                    .andExpect(jsonPath("$[3].forgeLevelUp").value(true))
                    .andExpect(jsonPath("$[6].decisions[0].code").value("TRAIN_WITH_VOLTYAR"))
                    .andExpect(jsonPath("$[6].decisions[0].options[0].achievements[0].name").value("Голос Волтьяра"))
                    .andExpect(jsonPath("$[10].expireAllQuests").value(true))
                    .andExpect(jsonPath("$[10].finalBattle.code").value("AWAKENED"));
        }
    }

    @Nested
    @DisplayName("Кэширование")
    class Caching {

        @Test
        @DisplayName("ETag — контрольная сумма каталога; каждый раз проверка по ETag — правки администратора видны сразу")
        void etagAndCacheControl() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/bosses"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"" + catalog.checksum() + "\""))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, public"));
        }

        @Test
        @DisplayName("повторный запрос с If-None-Match получает 304 без тела")
        void notModified() throws Exception {
            // подготовка
            String etag = "\"" + catalog.checksum() + "\"";

            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/dictionaries").header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified());
        }
    }

    @Nested
    @DisplayName("Таблицы каталога (миграция R__Catalog)")
    class Tables {

        @Test
        @DisplayName("боссы, стойки, достижения и версия каталога записаны в БД")
        void catalogInDatabase() {
            // вызов
            Integer bosses = jdbc.queryForObject("select count(*) from boss", Integer.class);
            Integer stances = jdbc.queryForObject("select count(*) from boss_stance", Integer.class);
            Integer achievements = jdbc.queryForObject("select count(*) from achievement_def", Integer.class);
            String checksum = jdbc.queryForObject("select checksum from catalog_version", String.class);
            Integer korovonNoThreshold = jdbc.queryForObject("""
                    select count(*) from boss_stance
                    where boss_code = 'KOROVON' and stance_no = 2 and toughness_per_hunter is null and change_mode = 'ON_DEMAND'""",
                    Integer.class);

            Integer quests = jdbc.queryForObject("select count(*) from quest_def", Integer.class);
            Integer chapters = jdbc.queryForObject("select count(*) from chapter_def", Integer.class);
            String quest1Condition = jdbc.queryForObject(
                    "select victory_effects -> 1 -> 'if' ->> 'chapterIn' from quest_def where number = 1", String.class);

            // проверка
            assertThat(quests).isEqualTo(49);
            assertThat(chapters).isEqualTo(11);
            assertThat(quest1Condition).isEqualTo("[1, 2]");
            assertThat(bosses).isEqualTo(23);
            assertThat(stances).isEqualTo(22 * 4 * 3 + 5);
            assertThat(achievements).isEqualTo(23);
            assertThat(checksum).isEqualTo(catalog.checksum());
            assertThat(korovonNoThreshold).isEqualTo(4);
        }
    }
}
