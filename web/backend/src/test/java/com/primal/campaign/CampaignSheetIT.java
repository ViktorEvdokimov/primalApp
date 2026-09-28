package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Лист кампании")
class CampaignSheetIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long dareonId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice@example.com");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\", \"playerName\": \"Алиса\"},"
                                + " {\"class\": \"MIRA\", \"playerName\": \"Вадим\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        dareonId = ((Number) JsonPath.read(body, "$.hunters[0].id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions sheet() throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice));
    }

    private int version() throws Exception {
        return JsonPath.read(sheet().andReturn().getResponse().getContentAsString(), "$.version");
    }

    private ResultActions patchCampaign(String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/campaigns/{id}", campaignId).with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Nested
    @DisplayName("Структура ответа")
    class Structure {

        @Test
        @DisplayName("навыки, ресурсы, задания, достижения и трофеи — одним ответом, как в api.md §5.2")
        void fullSheet() throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = 3, forge_level = 2 where id = ?", campaignId);
            jdbc.update("insert into hunter_skill (hunter_id, branch, tier) values (?, 'A', 1)", dareonId);
            jdbc.update("insert into hunter_resource (hunter_id, resource, quantity) values (?, 'BONES', 3), (?, 'FIRE', 2),"
                    + " (?, 'BLOOD', 0)", dareonId, dareonId, dareonId);
            jdbc.update("""
                    insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter, closed_in_chapter)
                    values (?, 4, 'OPEN', 2, null), (?, 1, 'COMPLETED', 1, 2), (?, 36, 'EXPIRED', 1, 3)""",
                    campaignId, campaignId, campaignId);
            jdbc.update("""
                    insert into campaign_achievement (campaign_id, achievement_code, name, normalized_name, source, granted_in_chapter)
                    values (?, 'ZATISHE', 'Затишье', 'затишье', 'QUEST', 2), (?, null, 'Своё', 'своё', 'MANUAL', 3)""",
                    campaignId, campaignId);
            jdbc.update("insert into campaign_trophy (campaign_id, boss_code, chapter) values (?, 'VIRAXEN', 0),"
                    + " (?, 'TORAMAT', 1), (?, 'VIRAXEN', 2)", campaignId, campaignId, campaignId);

            // вызов и проверка
            sheet()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chapter").value(3))
                    .andExpect(jsonPath("$.difficulty").value(1))
                    .andExpect(jsonPath("$.forgeLevel").value(2))
                    .andExpect(jsonPath("$.labLevel").value(1))
                    .andExpect(jsonPath("$.finalBoss").isEmpty())
                    .andExpect(jsonPath("$.hunters[0].skills[0].branch").value("A"))
                    .andExpect(jsonPath("$.hunters[0].skills[0].tier").value(1))
                    .andExpect(jsonPath("$.hunters[0].unlockableSkills[0].branch").value("A"))
                    .andExpect(jsonPath("$.hunters[0].unlockableSkills[0].tier").value(2))
                    .andExpect(jsonPath("$.hunters[0].unlockableSkills.length()").value(5))
                    .andExpect(jsonPath("$.hunters[0].resources.BONES").value(3))
                    .andExpect(jsonPath("$.hunters[0].resources.FIRE").value(2))
                    .andExpect(jsonPath("$.hunters[0].resources.BLOOD").doesNotExist())
                    .andExpect(jsonPath("$.hunters[1].resources").isEmpty())
                    .andExpect(jsonPath("$.quests.open[0].number").value(4))
                    .andExpect(jsonPath("$.quests.open[0].name").value("Вожак стаи"))
                    .andExpect(jsonPath("$.quests.open[0].boss.code").value("FELAXIR"))
                    .andExpect(jsonPath("$.quests.open[0].boss.element").value("CRYSTAL"))
                    .andExpect(jsonPath("$.quests.open[0].closedInChapter").isEmpty())
                    .andExpect(jsonPath("$.quests.completed[0].number").value(1))
                    .andExpect(jsonPath("$.quests.completed[0].closedInChapter").value(2))
                    .andExpect(jsonPath("$.quests.expired[0].number").value(36))
                    .andExpect(jsonPath("$.achievements[0].code").value("ZATISHE"))
                    .andExpect(jsonPath("$.achievements[0].source").value("QUEST"))
                    .andExpect(jsonPath("$.achievements[1].code").isEmpty())
                    .andExpect(jsonPath("$.trophies[0].boss.code").value("VIRAXEN"))
                    .andExpect(jsonPath("$.trophies[0].chapters[0]").value(0))
                    .andExpect(jsonPath("$.trophies[0].chapters[1]").value(2))
                    .andExpect(jsonPath("$.trophies[1].boss.name").value("Торамат"))
                    .andExpect(jsonPath("$.activeBattles").isEmpty())
                    .andExpect(jsonPath("$.recentBattles").isEmpty());
        }

        @ParameterizedTest(name = "глава {0} → уровень {1}")
        @CsvSource({"0,0", "1,1", "3,1", "4,2", "7,2", "8,3", "11,3"})
        @DisplayName("уровень враждебности по главе")
        void difficulty(int chapter, int difficulty) throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = ? where id = ?", chapter, campaignId);

            // вызов и проверка
            sheet().andExpect(jsonPath("$.difficulty").value(difficulty));
        }

        @Test
        @DisplayName("финальный босс главы 11 и ожидающий переход главы")
        void finalBossAndTransition() throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = 11, final_boss_code = 'AWAKENED', status = 'CHAPTER_TRANSITION'"
                    + " where id = ?", campaignId);

            // вызов и проверка
            sheet()
                    .andExpect(jsonPath("$.finalBoss.code").value("AWAKENED"))
                    .andExpect(jsonPath("$.finalBoss.element").isEmpty())
                    .andExpect(jsonPath("$.pendingTransition").value(true));
        }
    }

    @Nested
    @DisplayName("Правка")
    class Update {

        @Test
        @DisplayName("название и заметки меняются, версия растёт")
        void nameAndNotes() throws Exception {
            // подготовка
            int version = version();

            // вызов и проверка
            patchCampaign("{\"expectedVersion\": " + version + ", \"name\": \"Новая\", \"notes\": \"Мира нашла карту\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Новая"))
                    .andExpect(jsonPath("$.notes").value("Мира нашла карту"))
                    .andExpect(jsonPath("$.version").value(version + 1));
        }

        @Test
        @DisplayName("устаревший expectedVersion → 409 VERSION_CONFLICT с актуальным листом")
        void staleVersion() throws Exception {
            // подготовка
            int version = version();
            patchCampaign("{\"expectedVersion\": " + version + ", \"notes\": \"первая правка\"}").andExpect(status().isOk());

            // вызов и проверка
            patchCampaign("{\"expectedVersion\": " + version + ", \"notes\": \"вторая правка\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                    .andExpect(jsonPath("$.current.notes").value("первая правка"))
                    .andExpect(jsonPath("$.current.version").value(version + 1));
        }

        @Test
        @DisplayName("ручная правка главы растит progress_seq")
        void chapterBumpsProgress() throws Exception {
            // подготовка
            Integer before = jdbc.queryForObject("select progress_seq from campaign where id = ?", Integer.class, campaignId);

            // вызов
            patchCampaign("{\"expectedVersion\": " + version() + ", \"chapter\": 4}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chapter").value(4))
                    .andExpect(jsonPath("$.difficulty").value(2));

            // проверка
            Integer after = jdbc.queryForObject("select progress_seq from campaign where id = ?", Integer.class, campaignId);
            assertThat(after).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("правка главы при ожидающем переходе → 409 CHAPTER_TRANSITION_PENDING")
        void chapterDuringTransition() throws Exception {
            // подготовка
            jdbc.update("update campaign set status = 'CHAPTER_TRANSITION' where id = ?", campaignId);

            // вызов и проверка
            patchCampaign("{\"expectedVersion\": " + version() + ", \"chapter\": 2}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHAPTER_TRANSITION_PENDING"));
        }

        @Test
        @DisplayName("глава вне 0–11 и пустое название → 400")
        void invalidValues() throws Exception {
            // вызов и проверка
            patchCampaign("{\"expectedVersion\": " + version() + ", \"chapter\": 12}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("chapter"));
            patchCampaign("{\"expectedVersion\": " + version() + ", \"name\": \" \"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
    }
}
