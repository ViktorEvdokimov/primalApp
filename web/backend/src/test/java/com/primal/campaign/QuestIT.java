package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Задания кампании вручную")
class QuestIT extends IntegrationTest {

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
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private void quest(int number, String status) {
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, ?, ?, 1)",
                campaignId, number, status);
    }

    private String questStatus(int number) {
        List<String> rows = jdbc.queryForList("select status from campaign_quest where campaign_id = ? and quest_number = ?",
                String.class, campaignId, number);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ResultActions action(String path) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + path).with(xsrf()).cookie(alice));
    }

    private ResultActions setOpen(String numbers) throws Exception {
        return mockMvc.perform(put("/api/v1/campaigns/" + campaignId + "/quests/open").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"numbers\": " + numbers + "}"));
    }

    @Nested
    @DisplayName("«Выполнено»")
    class Complete {

        @Test
        @DisplayName("открывает только задания из наград победы — без ресурсов и достижений (qa 70)")
        void opensOnlyQuests() throws Exception {
            // подготовка
            quest(1, "OPEN");

            // вызов и проверка
            action("/quests/1/complete")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quest.number").value(1))
                    .andExpect(jsonPath("$.quest.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.opened[0]").value(4))
                    .andExpect(jsonPath("$.rules[0].description")
                            .value("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6"))
                    .andExpect(jsonPath("$.rules[0].result").value("Добавлено задание 4."));
            assertThat(questStatus(1)).isEqualTo("COMPLETED");
            assertThat(questStatus(4)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("select count(*) from hunter_resource", Integer.class)).isZero();
        }

        @Test
        @DisplayName("условное достижение при «Выполнено» не выдаётся, его правило не показывается")
        void noConditionalAchievement() throws Exception {
            // подготовка: задание 29 выдаёт «Уробборос» при «Голосе Волтьяра»
            quest(29, "OPEN");
            jdbc.update("""
                    insert into campaign_achievement (campaign_id, achievement_code, name, normalized_name, source, granted_in_chapter)
                    values (?, 'GOLOS_VOLTYARA', 'Голос Волтьяра', 'голос волтьяра', 'DECISION', 7)""", campaignId);

            // вызов
            action("/quests/29/complete")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rules[?(@.description =~ /.*Уробборос.*/)]").isEmpty());

            // проверка
            assertThat(jdbc.queryForList("select achievement_code from campaign_achievement where campaign_id = ?",
                    String.class, campaignId)).containsExactly("GOLOS_VOLTYARA");
        }

        @Test
        @DisplayName("задание не открыто → 422 QUEST_NOT_OPEN")
        void notOpen() throws Exception {
            // подготовка
            quest(2, "COMPLETED");

            // вызов и проверка
            action("/quests/2/complete")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("QUEST_NOT_OPEN"));
            action("/quests/3/complete").andExpect(jsonPath("$.code").value("QUEST_NOT_OPEN"));
        }
    }

    @Nested
    @DisplayName("«Отмена»")
    class Reopen {

        @Test
        @DisplayName("выполненное задание снова открыто, открытые им задания не меняются (qa 119)")
        void reopen() throws Exception {
            // подготовка
            quest(1, "OPEN");
            action("/quests/1/complete").andExpect(status().isOk());

            // вызов и проверка
            action("/quests/1/reopen")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open[?(@.number == 1)]").isNotEmpty())
                    .andExpect(jsonPath("$.open[?(@.number == 4)]").isNotEmpty())
                    .andExpect(jsonPath("$.completed").isEmpty());
            assertThat(questStatus(4)).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("невыполненное задание → 422 QUEST_NOT_COMPLETED")
        void notCompleted() throws Exception {
            // подготовка
            quest(1, "OPEN");

            // вызов и проверка
            action("/quests/1/reopen")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("QUEST_NOT_COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Редактор открытых заданий")
    class Editor {

        @Test
        @DisplayName("добавляет недостающие, удаляет открытые вне списка, выполненные и истёкшие не трогает (D-5)")
        void replacesOpenSet() throws Exception {
            // подготовка
            quest(1, "OPEN");
            quest(2, "OPEN");
            quest(3, "COMPLETED");
            quest(5, "EXPIRED");
            Integer before = jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId);

            // вызов
            setOpen("[2, 4, 36, 3, 5]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open.length()").value(3))
                    .andExpect(jsonPath("$.open[0].number").value(2))
                    .andExpect(jsonPath("$.open[1].number").value(4))
                    .andExpect(jsonPath("$.open[2].number").value(36))
                    .andExpect(jsonPath("$.completed[0].number").value(3))
                    .andExpect(jsonPath("$.expired[0].number").value(5));

            // проверка
            assertThat(questStatus(1)).isNull();
            assertThat(jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId))
                    .isEqualTo(before + 1);
        }

        @Test
        @DisplayName("несуществующее задание → 400")
        void unknownQuest() throws Exception {
            // вызов и проверка
            setOpen("[2, 99]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }
}
