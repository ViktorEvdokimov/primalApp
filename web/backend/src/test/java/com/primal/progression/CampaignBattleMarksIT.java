package com.primal.progression;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Отметки о начале боя кампании")
class CampaignBattleMarksIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice@example.com");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        jdbc.update("update campaign set chapter = 1 where id = ?", campaignId);
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, 1, 'OPEN', 1)",
                campaignId);
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions start(UUID id, String questNumber, String bossCode) throws Exception {
        String body = """
                {"id": "%s", "questNumber": %s, "bossCode": %s, "difficulty": 1, "chapter": 1, "progressSeq": 0,
                 "startedAt": "%s"}""".formatted(id, questNumber, bossCode == null ? "null" : "\"" + bossCode + "\"",
                clock.instant());
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/battles").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions start(UUID id) throws Exception {
        return start(id, "1", "TORAMAT");
    }

    private ResultActions battles(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battles" + query).cookie(alice));
    }

    private ResultActions setup() throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup").cookie(alice));
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from campaign_battle where campaign_id = ?", Integer.class, campaignId);
    }

    @Nested
    @DisplayName("Старт")
    class Start {

        @Test
        @DisplayName("два старта подряд проходят оба; второй получает первый в otherActiveBattles")
        void twoStarts() throws Exception {
            // подготовка
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();

            // вызов
            start(first)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/campaigns/" + campaignId + "/battles/" + first))
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.otherActiveBattles").isEmpty());
            start(second, "null", "OZEV")

                    // проверка
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.otherActiveBattles.length()").value(1))
                    .andExpect(jsonPath("$.otherActiveBattles[0].id").value(first.toString()))
                    .andExpect(jsonPath("$.otherActiveBattles[0].startedBy.kind").value("USER"))
                    .andExpect(jsonPath("$.otherActiveBattles[0].startedBy.name").value("alice"))
                    .andExpect(jsonPath("$.otherActiveBattles[0].quest.number").value(1))
                    .andExpect(jsonPath("$.otherActiveBattles[0].boss.code").value("TORAMAT"));
            setup().andExpect(jsonPath("$.activeBattles.length()").value(2));
            mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(alice))
                    .andExpect(jsonPath("$.activeBattles.length()").value(2))
                    .andExpect(jsonPath("$.activeBattles[0].id").value(second.toString()));
            assertThat(jdbc.queryForObject("select purpose from campaign_battle where id = ?", String.class, second))
                    .isEqualTo("FREE");
        }

        @Test
        @DisplayName("повтор с тем же id → 200, вторая запись не создаётся")
        void repeat() throws Exception {
            // подготовка
            UUID id = UUID.randomUUID();
            start(id).andExpect(status().isCreated());

            // вызов и проверка
            start(id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.otherActiveBattles").isEmpty());
            assertThat(rows()).isEqualTo(1);
        }

        @Test
        @DisplayName("проверки как у подготовки: закрытое задание → 422, ожидающий переход → 409")
        void refusals() throws Exception {
            // вызов и проверка
            start(UUID.randomUUID(), "2", "OZEV")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("QUEST_NOT_OPEN"));
            jdbc.update("update campaign set status = 'CHAPTER_TRANSITION' where id = ?", campaignId);
            start(UUID.randomUUID())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHAPTER_TRANSITION_PENDING"));
            assertThat(rows()).isZero();
        }

        @Test
        @DisplayName("финальный бой — только с финальным боссом; неизвестный босс → 400")
        void finalBossOnly() throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = 11, final_boss_code = 'AWAKENED' where id = ?", campaignId);

            // вызов и проверка
            start(UUID.randomUUID(), "null", "TORAMAT")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("FINAL_BOSS_REQUIRED"));
            start(UUID.randomUUID(), "null", "NOBODY")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            start(UUID.randomUUID(), "null", "AWAKENED").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Устаревшие отметки")
    class Stale {

        @Test
        @DisplayName("отметка старше 24 часов в предупреждения не попадает, в истории — stale")
        void olderThanDay() throws Exception {
            // подготовка
            UUID id = UUID.randomUUID();
            start(id).andExpect(status().isCreated());

            // вызов
            clock.advance(Duration.ofHours(25));

            // проверка
            setup().andExpect(jsonPath("$.activeBattles").isEmpty());
            start(UUID.randomUUID(), "null", "OZEV").andExpect(jsonPath("$.otherActiveBattles").isEmpty());
            battles("?status=IN_PROGRESS")
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].id").value(id.toString()))
                    .andExpect(jsonPath("$[1].stale").value(true))
                    .andExpect(jsonPath("$[0].stale").value(false));
        }

        @Test
        @DisplayName("бой, начатый до принятой победы (progress_seq вырос), в предупреждения не попадает")
        void startedBeforeVictory() throws Exception {
            // подготовка
            UUID id = UUID.randomUUID();
            start(id).andExpect(status().isCreated());

            // вызов
            jdbc.update("update campaign set progress_seq = progress_seq + 1 where id = ?", campaignId);

            // проверка
            setup().andExpect(jsonPath("$.activeBattles").isEmpty());
            battles("").andExpect(jsonPath("$[0].stale").value(true));
        }
    }

    @Test
    @DisplayName("DELETE переводит бой в ABANDONED; неизвестный бой → 404")
    void abandon() throws Exception {
        // подготовка
        UUID id = UUID.randomUUID();
        start(id).andExpect(status().isCreated());

        // вызов
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/battles/" + id).with(xsrf()).cookie(alice))
                .andExpect(status().isNoContent());

        // проверка
        battles("")
                .andExpect(jsonPath("$[0].status").value("ABANDONED"))
                .andExpect(jsonPath("$[0].purpose").value("QUEST"))
                .andExpect(jsonPath("$[0].questNumber").value(1))
                .andExpect(jsonPath("$[0].startedBy.name").value("alice"))
                .andExpect(jsonPath("$[0].submittedBy").isEmpty())
                .andExpect(jsonPath("$[0].stale").value(false));
        battles("?status=IN_PROGRESS").andExpect(jsonPath("$").isEmpty());
        setup().andExpect(jsonPath("$.activeBattles").isEmpty());
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/battles/" + UUID.randomUUID()).with(xsrf()).cookie(alice))
                .andExpect(status().isNotFound());
    }
}
