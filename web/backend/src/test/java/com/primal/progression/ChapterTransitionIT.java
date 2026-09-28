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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Переход главы")
class ChapterTransitionIT extends IntegrationTest {

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
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    /** Кампания ждёт перехода из главы {@code from}. */
    private void pending(int from) {
        jdbc.update("update campaign set chapter = ?, status = 'CHAPTER_TRANSITION' where id = ?", from, campaignId);
    }

    private void quest(int number, String status) {
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, ?, ?, 1)",
                campaignId, number, status);
    }

    private void achievement(String code, String name) {
        jdbc.update("""
                insert into campaign_achievement (campaign_id, achievement_code, name, normalized_name, source, granted_in_chapter)
                values (?, ?, ?, lower(?), 'QUEST', 1)""", campaignId, code, name, name);
    }

    private int version() {
        return jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId);
    }

    private String questStatus(int number) {
        List<String> rows = jdbc.queryForList("select status from campaign_quest where campaign_id = ? and quest_number = ?",
                String.class, campaignId, number);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ResultActions preview(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/chapter-transition" + query).cookie(alice));
    }

    private ResultActions submit(String action, String decisions, int expectedVersion) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/chapter-transition").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"%s\", \"decisions\": %s, \"expectedVersion\": %d}"
                        .formatted(action, decisions, expectedVersion)));
    }

    @Nested
    @DisplayName("Глава 7: решение «Тренироваться у Волтьяра»")
    class ChapterSeven {

        @Test
        @DisplayName("превью учитывает ответ; «Да» выдаёт «Голос Волтьяра» и улучшение набора; без ответа → 422")
        void yes() throws Exception {
            // подготовка
            pending(6);
            quest(7, "OPEN");

            // вызов и проверка: превью без ответа
            preview("")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fromChapter").value(6))
                    .andExpect(jsonPath("$.toChapter").value(7))
                    .andExpect(jsonPath("$.decisions[0].code").value("TRAIN_WITH_VOLTYAR"))
                    .andExpect(jsonPath("$.decisions[0].options[0].label").value("Да"))
                    .andExpect(jsonPath("$.decisions[0].selected").isEmpty())
                    .andExpect(jsonPath("$.decisionsComplete").value(false))
                    .andExpect(jsonPath("$.achievements").isEmpty())
                    .andExpect(jsonPath("$.hunterKitUpgrade").value(true));

            // превью с ответом «Да»
            preview("?decision=TRAIN_WITH_VOLTYAR:YES")
                    .andExpect(jsonPath("$.decisions[0].selected").value("YES"))
                    .andExpect(jsonPath("$.decisionsComplete").value(true))
                    .andExpect(jsonPath("$.achievements[0].code").value("GOLOS_VOLTYARA"))
                    .andExpect(jsonPath("$.expireQuests[0].number").value(7))
                    .andExpect(jsonPath("$.expireQuests[0].wasOpen").value(true))
                    .andExpect(jsonPath("$.expireQuests[1].wasOpen").value(false))
                    .andExpect(jsonPath("$.rules[0].kind").value("QUEST"))
                    .andExpect(jsonPath("$.rules[0].result").value("Условие не выполнено, задание не добавляется."))
                    .andExpect(jsonPath("$.rejectConsequences").value(contains(
                            "Глава останется 6. После следующей победы переход в главу 7 будет предложен снова.",
                            "Не будет получено достижение «Голос Волтьяра» (решение главы).",
                            "Не истечёт время задания 7.",
                            "Не будет улучшения набора охотника.",
                            "Главу, задания и достижения можно изменить вручную на листе кампании.")));

            // «Принять» без ответа — 422
            submit("ACCEPT", "{}", version())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("DECISION_REQUIRED"));

            // «Принять» с ответом «Да»
            int seq = jdbc.queryForObject("select progress_seq from campaign where id = ?", Integer.class, campaignId);
            submit("ACCEPT", "{\"TRAIN_WITH_VOLTYAR\": \"YES\"}", version())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chapter").value(7))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.pendingTransition").value(false))
                    .andExpect(jsonPath("$.achievements[0].code").value("GOLOS_VOLTYARA"))
                    .andExpect(jsonPath("$.achievements[0].source").value("DECISION"))
                    .andExpect(jsonPath("$.achievements[0].grantedInChapter").value(7))
                    .andExpect(jsonPath("$.quests.expired[0].number").value(7))
                    .andExpect(jsonPath("$.quests.expired[0].closedInChapter").value(7));
            assertThat(jdbc.queryForObject("select progress_seq from campaign where id = ?", Integer.class, campaignId))
                    .isEqualTo(seq + 1);
        }

        @Test
        @DisplayName("«Отклонить» (D-10): ничего не применяется, глава прежняя, кампания снова активна")
        void reject() throws Exception {
            // подготовка
            pending(6);
            quest(7, "OPEN");

            // вызов
            submit("REJECT", "null", version())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chapter").value(6))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.achievements").isEmpty());

            // проверка
            assertThat(questStatus(7)).isEqualTo("OPEN");
            preview("").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_CHANGED"));
        }
    }

    @ParameterizedTest(name = "переход в главу {0}")
    @ValueSource(ints = {8, 10})
    @DisplayName("главы 8 и 10: улучшение набора только при «Голосе Волтьяра» (C-9)")
    void kitUpgradeNeedsVoice(int to) throws Exception {
        // подготовка
        pending(to - 1);

        // вызов и проверка: без достижения
        preview("")
                .andExpect(jsonPath("$.hunterKitUpgrade").value(false))
                .andExpect(jsonPath("$.rules[?(@.kind == 'KIT')].result").value(hasItem("Достижения нет.")));

        // с достижением
        achievement("GOLOS_VOLTYARA", "Голос Волтьяра");
        preview("")
                .andExpect(jsonPath("$.hunterKitUpgrade").value(true))
                .andExpect(jsonPath("$.rules[?(@.kind == 'KIT')].result").value(hasItem("Достижение есть, улучшите набор охотника.")));
    }

    @Test
    @DisplayName("глава 11 (R-6): истекли все открытые задания, следующий бой — только Пробуждённый")
    void chapterEleven() throws Exception {
        // подготовка
        pending(10);
        quest(5, "OPEN");
        quest(12, "OPEN");
        quest(3, "COMPLETED");

        // вызов и проверка
        preview("")
                .andExpect(jsonPath("$.expireQuests[0].number").value(5))
                .andExpect(jsonPath("$.expireQuests[1].number").value(12))
                .andExpect(jsonPath("$.expireQuests[1].wasOpen").value(true))
                .andExpect(jsonPath("$.finalBattle.code").value("AWAKENED"))
                .andExpect(jsonPath("$.messages[0]").value("Истекло время всех заданий. Следующий бой — финальный: Пробуждённый"));
        submit("ACCEPT", "{}", version()).andExpect(status().isOk()).andExpect(jsonPath("$.chapter").value(11));
        assertThat(questStatus(5)).isEqualTo("EXPIRED");
        assertThat(questStatus(12)).isEqualTo("EXPIRED");
        assertThat(questStatus(3)).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup").cookie(alice))
                .andExpect(jsonPath("$.purpose").value("FINAL"))
                .andExpect(jsonPath("$.forcedBoss.code").value("AWAKENED"));
    }

    @Test
    @DisplayName("устаревшая версия → 409 VERSION_CONFLICT; неизвестное решение и неверный формат ответа → 400")
    void refusals() throws Exception {
        // подготовка
        pending(6);

        // вызов и проверка
        submit("ACCEPT", "{\"TRAIN_WITH_VOLTYAR\": \"YES\"}", version() - 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.current.chapter").value(6));
        preview("?decision=NOBODY:YES").andExpect(status().isBadRequest());
        preview("?decision=TRAIN_WITH_VOLTYAR:MAYBE").andExpect(status().isBadRequest());
        preview("?decision=TRAIN_WITH_VOLTYAR").andExpect(status().isBadRequest());
    }

    /** Превью и применение совпадают для всех 11 глав (критерий приёмки 5.3). */
    @ParameterizedTest(name = "переход в главу {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
    @DisplayName("превью совпадает с применённым")
    void previewMatchesApplied(int to) throws Exception {
        // подготовка: открыты задания, которые главы делают истёкшими
        pending(to - 1);
        for (int number : List.of(1, 2, 3, 5, 7, 8, 9, 10, 11, 13, 15, 20, 36)) {
            quest(number, "OPEN");
        }
        String query = to == 7 ? "?decision=TRAIN_WITH_VOLTYAR:YES" : "";
        String decisions = to == 7 ? "{\"TRAIN_WITH_VOLTYAR\": \"YES\"}" : "{}";
        String preview = preview(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Integer> perHunter = JsonPath.read(preview, "$.perHunter");
        List<Integer> opened = JsonPath.read(preview, "$.openQuests");
        List<Integer> expired = JsonPath.read(preview, "$.expireQuests[?(@.wasOpen == true)].number");
        List<String> achievements = JsonPath.read(preview, "$.achievements[*].code");
        boolean forge = JsonPath.read(preview, "$.forgeLevelUp");
        boolean lab = JsonPath.read(preview, "$.labLevelUp");
        Map<String, Object> finalBattle = JsonPath.read(preview, "$.finalBattle");
        String finalBoss = finalBattle == null ? null : (String) finalBattle.get("code");

        // вызов
        submit("ACCEPT", decisions, JsonPath.read(preview, "$.version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapter").value(to));

        // проверка
        for (String code : perHunter.keySet()) {
            assertThat(jdbc.queryForList("""
                    select r.quantity from hunter_resource r join campaign_hunter h on h.id = r.hunter_id
                    where h.campaign_id = ? and r.resource = ?""", Integer.class, campaignId, code))
                    .as("ресурс %s", code).containsExactly(perHunter.get(code), perHunter.get(code));
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from hunter_resource r join campaign_hunter h on h.id = r.hunter_id
                where h.campaign_id = ?""", Integer.class, campaignId)).isEqualTo(perHunter.size() * 2);
        opened.forEach(number -> assertThat(questStatus(number)).as("задание %d", number).isEqualTo("OPEN"));
        expired.forEach(number -> assertThat(questStatus(number)).as("задание %d", number).isEqualTo("EXPIRED"));
        assertThat(jdbc.queryForObject("select count(*) from campaign_quest where campaign_id = ? and status = 'EXPIRED'",
                Integer.class, campaignId)).isEqualTo(expired.size());
        assertThat(new HashSet<>(jdbc.queryForList("select achievement_code from campaign_achievement where campaign_id = ?",
                String.class, campaignId))).isEqualTo(new HashSet<>(achievements));
        assertThat(jdbc.queryForObject("select forge_level from campaign where id = ?", Integer.class, campaignId))
                .isEqualTo(forge ? 2 : 1);
        assertThat(jdbc.queryForObject("select lab_level from campaign where id = ?", Integer.class, campaignId))
                .isEqualTo(lab ? 2 : 1);
        assertThat(jdbc.queryForObject("select final_boss_code from campaign where id = ?", String.class, campaignId))
                .isEqualTo(finalBoss);
    }
}
