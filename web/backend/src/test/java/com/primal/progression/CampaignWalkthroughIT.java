package com.primal.progression;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Сквозной путь кампании только через API: создание → пролог → главы 1–10 (бой по первому открытому заданию
 * или без задания) → глава 11 → Пробуждённый → кампания пройдена.
 */
@DisplayName("Кампания от создания до победы над Пробуждённым")
class CampaignWalkthroughIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private String campaigns() {
        return "/api/v1/campaigns/" + campaignId;
    }

    private String setup() throws Exception {
        return mockMvc.perform(get(campaigns() + "/battle-setup").cookie(alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Бой по подготовке: отметка о начале, превью и «Принять» победы. Возвращает превью. */
    private String win(String setup, Integer questNumber, String bossCode) throws Exception {
        int chapter = JsonPath.read(setup, "$.chapter");
        int progressSeq = JsonPath.read(setup, "$.progressSeq");
        UUID battle = UUID.randomUUID();
        String quest = questNumber == null ? "null" : String.valueOf(questNumber);
        mockMvc.perform(post(campaigns() + "/battles").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "questNumber": %s, "bossCode": "%s", "difficulty": 1, "chapter": %d,
                                 "progressSeq": %d, "startedAt": "%s"}""".formatted(battle, quest, bossCode, chapter,
                                progressSeq, clock.instant())))
                .andExpect(status().isCreated());
        String report = """
                {"questNumber": %s, "bossCode": "%s", "difficulty": 1, "chapter": %d, "progressSeq": %d,
                 "result": "VICTORY", "roundsPlayed": 5, "startedAt": "%s", "finishedAt": "%s"%%s}"""
                .formatted(quest, bossCode, chapter, progressSeq, clock.instant(), clock.instant());
        String preview = mockMvc.perform(post(campaigns() + "/battles/" + battle + "/result/preview").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(report.formatted("")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post(campaigns() + "/battles/" + battle + "/result").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(report.formatted(", \"action\": \"ACCEPT\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value(JsonPath.<String>read(preview, "$.next")));
        return preview;
    }

    /** Переход главы: превью, ответ «Да» на решение главы 7, «Принять». */
    private ResultActions advance(int to) throws Exception {
        String query = to == 7 ? "?decision=TRAIN_WITH_VOLTYAR:YES" : "";
        String preview = mockMvc.perform(get(campaigns() + "/chapter-transition" + query).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toChapter").value(to))
                .andExpect(jsonPath("$.decisionsComplete").value(true))
                .andReturn().getResponse().getContentAsString();
        String decisions = to == 7 ? "{\"TRAIN_WITH_VOLTYAR\": \"YES\"}" : "{}";
        return mockMvc.perform(post(campaigns() + "/chapter-transition").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"ACCEPT\", \"decisions\": %s, \"expectedVersion\": %d}"
                                .formatted(decisions, JsonPath.<Integer>read(preview, "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapter").value(to))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("создание → пролог → главы 1–11 → Пробуждённый → COMPLETED")
    void walkthrough() throws Exception {
        // подготовка
        alice = auth.login("alice");
        String created = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Путь\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}, {\"class\": \"KARA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(created, "$.id")).longValue();

        // вызов и проверка: пролог
        String prologue = setup();
        assertThat(JsonPath.<String>read(prologue, "$.purpose")).isEqualTo("PROLOGUE");
        win(prologue, null, "VIRAXEN");
        advance(1).andExpect(jsonPath("$.quests.open[*].number").value(contains(1, 2, 36)));

        // главы 1–10: бой по первому открытому заданию, иначе без задания с Тораматом
        for (int chapter = 1; chapter <= 10; chapter++) {
            String setup = setup();
            assertThat(JsonPath.<Integer>read(setup, "$.chapter")).isEqualTo(chapter);
            List<Integer> open = JsonPath.read(setup, "$.openQuests[*].number");
            if (open.isEmpty()) {
                win(setup, null, "TORAMAT");
            } else {
                int quest = open.getFirst();
                String questSetup = mockMvc.perform(get(campaigns() + "/battle-setup?questNumber=" + quest).cookie(alice))
                        .andExpect(jsonPath("$.purpose").value("QUEST"))
                        .andReturn().getResponse().getContentAsString();
                win(questSetup, quest, JsonPath.read(questSetup, "$.boss.code"));
                assertThat(jdbc.queryForObject("select status from campaign_quest where campaign_id = ? and quest_number = ?",
                        String.class, campaignId, quest)).isEqualTo("COMPLETED");
            }
            advance(chapter + 1);
        }

        // глава 11: только Пробуждённый
        String last = setup();
        assertThat(JsonPath.<String>read(last, "$.purpose")).isEqualTo("FINAL");
        assertThat(JsonPath.<List<Object>>read(last, "$.openQuests")).isEmpty();
        String finalPreview = win(last, null, "AWAKENED");

        // проверка
        assertThat(JsonPath.<String>read(finalPreview, "$.next")).isEqualTo("CAMPAIGN_COMPLETED");
        assertThat(JsonPath.<List<String>>read(finalPreview, "$.rewards.messages"))
                .contains("Кампания пройдена! Пробуждённый повержен.");
        mockMvc.perform(get(campaigns()).cookie(alice))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.chapter").value(11))
                .andExpect(jsonPath("$.forgeLevel").value(3))
                .andExpect(jsonPath("$.labLevel").value(3))
                .andExpect(jsonPath("$.achievements[*].code").value(hasItem("GOLOS_VOLTYARA")))
                .andExpect(jsonPath("$.trophies[*].boss.code").value(hasItem("AWAKENED")));
        assertThat(jdbc.queryForObject("select count(*) from campaign_trophy where campaign_id = ?", Integer.class, campaignId))
                .isEqualTo(12);
        mockMvc.perform(get(campaigns() + "/battle-setup").cookie(alice)).andExpect(status().isConflict());
    }
}
