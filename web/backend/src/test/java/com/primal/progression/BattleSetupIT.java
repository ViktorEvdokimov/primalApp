package com.primal.progression;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@DisplayName("Подготовка к бою кампании")
class BattleSetupIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice@example.com");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}, {\"class\": \"KARA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private void chapter(int chapter) {
        jdbc.update("update campaign set chapter = ? where id = ?", chapter, campaignId);
    }

    private void quest(int number, String status) {
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, ?, ?, 1)",
                campaignId, number, status);
    }

    private ResultActions setup(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup" + query).cookie(alice));
    }

    @Nested
    @DisplayName("Предзаполнение, как в app (42.4)")
    class Prefill {

        @Test
        @DisplayName("пролог: Вираксен без выбора, сложность 0, стойки уровня 0")
        void prologue() throws Exception {
            // вызов и проверка
            setup("")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.campaignId").value(campaignId))
                    .andExpect(jsonPath("$.chapter").value(0))
                    .andExpect(jsonPath("$.progressSeq").value(0))
                    .andExpect(jsonPath("$.purpose").value("PROLOGUE"))
                    .andExpect(jsonPath("$.difficulty").value(0))
                    .andExpect(jsonPath("$.hunterCount").value(3))
                    .andExpect(jsonPath("$.boss.code").value("VIRAXEN"))
                    .andExpect(jsonPath("$.boss.element").value("FIRE"))
                    .andExpect(jsonPath("$.forcedBoss.code").value("VIRAXEN"))
                    .andExpect(jsonPath("$.stances.length()").value(3))
                    .andExpect(jsonPath("$.stances[0].stance").value(1))
                    .andExpect(jsonPath("$.stances[0].toughnessPerHunter").value(2))
                    .andExpect(jsonPath("$.stances[0].stanceChange.mode").value("HEALTH"))
                    .andExpect(jsonPath("$.stances[0].stanceChange.atHealth").value(7))
                    .andExpect(jsonPath("$.stances[2].toughnessPerHunter").value(4))
                    .andExpect(jsonPath("$.stances[2].stanceChange.mode").value("FINAL"))
                    .andExpect(jsonPath("$.openQuests").isEmpty())
                    .andExpect(jsonPath("$.activeBattles").isEmpty());
        }

        @Test
        @DisplayName("задание 1 в главе 1: Торамат, сложность 1, выбор босса свободен")
        void questOne() throws Exception {
            // подготовка
            chapter(1);
            quest(1, "OPEN");

            // вызов и проверка
            setup("?questNumber=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.purpose").value("QUEST"))
                    .andExpect(jsonPath("$.difficulty").value(1))
                    .andExpect(jsonPath("$.boss.code").value("TORAMAT"))
                    .andExpect(jsonPath("$.boss.element").value("HORN"))
                    .andExpect(jsonPath("$.forcedBoss").isEmpty())
                    .andExpect(jsonPath("$.stances[0].toughnessPerHunter").value(4))
                    .andExpect(jsonPath("$.stances[0].stanceChange.atHealth").value(7))
                    .andExpect(jsonPath("$.stances[1].toughnessPerHunter").value(6))
                    .andExpect(jsonPath("$.stances[1].stanceChange.atHealth").value(4))
                    .andExpect(jsonPath("$.stances[2].toughnessPerHunter").value(9))
                    .andExpect(jsonPath("$.openQuests[0].number").value(1))
                    .andExpect(jsonPath("$.openQuests[0].boss.code").value("TORAMAT"));
        }

        @Test
        @DisplayName("глава 11: только Пробуждённый; его единственная сложность 3, пять стоек")
        void finalBattle() throws Exception {
            // подготовка
            chapter(11);
            jdbc.update("update campaign set final_boss_code = 'AWAKENED' where id = ?", campaignId);

            // вызов и проверка
            setup("")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.purpose").value("FINAL"))
                    .andExpect(jsonPath("$.difficulty").value(3))
                    .andExpect(jsonPath("$.boss.code").value("AWAKENED"))
                    .andExpect(jsonPath("$.boss.element").isEmpty())
                    .andExpect(jsonPath("$.forcedBoss.code").value("AWAKENED"))
                    .andExpect(jsonPath("$.stances.length()").value(5))
                    .andExpect(jsonPath("$.stances[0].toughnessPerHunter").value(30))
                    .andExpect(jsonPath("$.stances[4].stanceChange.mode").value("FINAL"));
        }
    }

    @ParameterizedTest(name = "глава {0} без задания → FREE, сложность {1}")
    @CsvSource({"1, 1", "2, 1", "3, 1", "4, 2", "5, 2", "6, 2", "7, 2", "8, 3", "9, 3", "10, 3"})
    @DisplayName("бой без задания: босса выбирает игрок, сложность по главе")
    void freeBattle(int chapter, int difficulty) throws Exception {
        // подготовка
        chapter(chapter);

        // вызов и проверка
        setup("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("FREE"))
                .andExpect(jsonPath("$.difficulty").value(difficulty))
                .andExpect(jsonPath("$.boss").isEmpty())
                .andExpect(jsonPath("$.forcedBoss").isEmpty())
                .andExpect(jsonPath("$.stances").isEmpty());
    }

    @Nested
    @DisplayName("Отказы")
    class Refusals {

        @Test
        @DisplayName("задание выполнено или не открыто → 422 QUEST_NOT_OPEN")
        void questNotOpen() throws Exception {
            // подготовка
            chapter(2);
            quest(2, "COMPLETED");

            // вызов и проверка
            setup("?questNumber=2")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("QUEST_NOT_OPEN"));
            setup("?questNumber=3").andExpect(jsonPath("$.code").value("QUEST_NOT_OPEN"));
        }

        @Test
        @DisplayName("задание в главе 11 → 422 FINAL_BOSS_REQUIRED")
        void questInFinal() throws Exception {
            // подготовка
            chapter(11);
            quest(5, "OPEN");
            jdbc.update("update campaign set final_boss_code = 'AWAKENED' where id = ?", campaignId);

            // вызов и проверка
            setup("?questNumber=5")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("FINAL_BOSS_REQUIRED"));
        }

        @Test
        @DisplayName("глава ждёт перехода → 409 CHAPTER_TRANSITION_PENDING")
        void pendingTransition() throws Exception {
            // подготовка
            chapter(3);
            jdbc.update("update campaign set status = 'CHAPTER_TRANSITION' where id = ?", campaignId);

            // вызов и проверка
            setup("")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHAPTER_TRANSITION_PENDING"));
        }

        @Test
        @DisplayName("чужая кампания → 404")
        void foreignCampaign() throws Exception {
            // подготовка
            Cookie bob = auth.login("bob@example.com");

            // вызов и проверка
            mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup").cookie(bob))
                    .andExpect(status().isNotFound());
        }
    }
}
