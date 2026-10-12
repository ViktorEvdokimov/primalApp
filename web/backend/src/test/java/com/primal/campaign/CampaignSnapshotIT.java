package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Снимок кампании для мобильного приложения")
class CampaignSnapshotIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private long createCampaign(Cookie device) throws Exception {
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Состояние после победы в прологе — как его считает приложение. */
    private static String state(UUID battleId) {
        return """
                {"name": "Кампания приложения", "chapter": 0, "status": "CHAPTER_TRANSITION", "forgeLevel": 1, "labLevel": 1,
                 "finalBossCode": null, "progressSeq": 1, "notes": "заметка",
                 "hunters": [
                   {"id": null, "class": "DAREON", "playerName": "Алиса", "position": 1,
                    "skills": [{"branch": "A", "tier": 1}],
                    "resources": {"FIRE": 2, "BONES": 1},
                    "items": [{"id": null, "kind": "EQUIPMENT", "name": "Язык пламени", "level": 1, "element": "FIRE", "source": "FIRE_01"}]},
                   {"id": null, "class": "MIRA", "playerName": "", "position": 2, "skills": [], "resources": {"FIRE": 2}, "items": []}],
                 "quests": [{"number": 1, "status": "OPEN", "openedInChapter": 0, "closedInChapter": null}],
                 "achievements": [{"id": null, "code": null, "name": "голос  волтьяра", "source": "MANUAL", "grantedInChapter": 0}],
                 "trophies": [{"bossCode": "VIRAXEN", "chapter": 0, "battleId": "%s"}],
                 "battles": [{"id": "%s", "purpose": "PROLOGUE", "questNumber": null, "bossCode": "VIRAXEN", "difficulty": 0,
                              "chapter": 0, "progressSeq": 0, "status": "APPLIED", "result": "VICTORY", "defeatReason": null,
                              "roundsPlayed": 4, "startedBy": null, "startedAt": "2026-09-27T18:00:00Z", "submittedBy": null,
                              "submittedAt": "2026-09-27T18:30:00Z", "finishedAt": "2026-09-27T18:29:00Z"}]}
                """.formatted(battleId, battleId);
    }

    @Test
    @DisplayName("GET отдаёт снимок с версией и ETag; повтор с If-None-Match — 304")
    void readsSnapshot() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");
        long id = createCampaign(alice);

        // вызов и проверка
        String etag = mockMvc.perform(get("/api/v1/campaigns/" + id + "/snapshot").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("OWNER"))
                .andExpect(jsonPath("$.ownerExpansions", hasSize(4)))
                .andExpect(jsonPath("$.state.chapter").value(0))
                .andExpect(jsonPath("$.state.hunters", hasSize(2)))
                .andExpect(jsonPath("$.state.hunters[0].class").value("DAREON"))
                .andExpect(jsonPath("$.state.hunters[0].items", hasSize(4)))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/v1/campaigns/" + id + "/snapshot").cookie(alice).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("PUT записывает состояние целиком, растит версию; устаревшая версия — 409")
    void replacesState() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");
        long id = createCampaign(alice);
        int version = jdbc.queryForObject("select version from campaign where id = ?", Integer.class, id);
        UUID battleId = UUID.randomUUID();
        String body = "{\"expectedVersion\": " + version + ", \"state\": " + state(battleId) + "}";

        // вызов и проверка
        mockMvc.perform(put("/api/v1/campaigns/" + id + "/snapshot").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.state.name").value("Кампания приложения"))
                .andExpect(jsonPath("$.state.status").value("CHAPTER_TRANSITION"))
                .andExpect(jsonPath("$.state.progressSeq").value(1))
                .andExpect(jsonPath("$.state.hunters[0].resources.FIRE").value(2))
                .andExpect(jsonPath("$.state.hunters[0].skills[0].branch").value("A"))
                .andExpect(jsonPath("$.state.hunters[0].items", hasSize(1)))
                .andExpect(jsonPath("$.state.hunters[1].playerName").value("Мира"))
                .andExpect(jsonPath("$.state.achievements[0].code").value("GOLOS_VOLTYARA"))
                .andExpect(jsonPath("$.state.achievements[0].name").value("Голос Волтьяра"))
                .andExpect(jsonPath("$.state.trophies[0].battleId").value(battleId.toString()))
                .andExpect(jsonPath("$.state.battles[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.state.battles[0].submittedBy.name").value("alice"));
        mockMvc.perform(put("/api/v1/campaigns/" + id + "/snapshot").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from campaign_trophy where campaign_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("неизвестное задание в снимке — 400, ничего не меняется")
    void rejectsUnknownQuest() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");
        long id = createCampaign(alice);
        int version = jdbc.queryForObject("select version from campaign where id = ?", Integer.class, id);
        String state = state(UUID.randomUUID()).replace("\"number\": 1,", "\"number\": 99,");

        // вызов и проверка
        mockMvc.perform(put("/api/v1/campaigns/" + id + "/snapshot").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": " + version + ", \"state\": " + state + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select name from campaign where id = ?", String.class, id)).isEqualTo("Кампания");
    }

    @Test
    @DisplayName("импорт кампании из приложения: владелец — пользователь, отряд в порядке позиций")
    void importsCampaign() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");

        // вызов и проверка
        mockMvc.perform(post("/api/v1/campaigns/import").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": " + state(UUID.randomUUID()) + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.access").value("OWNER"))
                .andExpect(jsonPath("$.state.hunters", hasSize(2)))
                .andExpect(jsonPath("$.state.hunters[1].position").value(2))
                .andExpect(jsonPath("$.state.quests[0].number").value(1))
                .andExpect(jsonPath("$.state.battles", hasSize(1)));
        mockMvc.perform(get("/api/v1/campaigns").cookie(alice))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Кампания приложения"));
    }

    @Test
    @DisplayName("бандл каталога: сырые эффекты заданий и глав, кузня и лаборатория")
    void catalogBundle() throws Exception {
        // вызов и проверка
        mockMvc.perform(get("/api/v1/catalog/bundle"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.bosses", hasSize(23)))
                .andExpect(jsonPath("$.quests", hasSize(49)))
                .andExpect(jsonPath("$.quests[0].victory[1].if.chapterIn[0]").value(1))
                .andExpect(jsonPath("$.quests[0].expired[0].openQuest").value(6))
                .andExpect(jsonPath("$.chapters", hasSize(11)))
                .andExpect(jsonPath("$.chapters[6].decisions[0].code").value("TRAIN_WITH_VOLTYAR"))
                .andExpect(jsonPath("$.forge", hasSize(9)))
                .andExpect(jsonPath("$.lab", hasSize(6)));
    }
}
