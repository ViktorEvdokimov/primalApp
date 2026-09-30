package com.primal.access;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.identity.DeviceCookies;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Матрица доступа ({@code doc/data-model.md} §3.5): владелец, гость и пользователь по ссылке, чужой. */
@DisplayName("Доступ к кампании")
class AccessMatrixIT extends IntegrationTest {

    private Cookie owner;
    private Cookie guest;
    private Cookie member;
    private Cookie stranger;
    private long campaignId;

    @BeforeEach
    void setUp() throws Exception {
        owner = auth.login("alice");
        String created = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String link = mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(owner))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(link, "$.url");
        String token = url.substring(url.lastIndexOf('/') + 1);
        guest = mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"Вадим\"}"))
                .andReturn().getResponse().getCookie(DeviceCookies.NAME);
        member = auth.login("mira");
        mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf()).cookie(member)).andExpect(status().isOk());
        stranger = auth.login("bob");
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private Cookie who(String name) {
        return switch (name) {
            case "owner" -> owner;
            case "guest" -> guest;
            case "member" -> member;
            default -> stranger;
        };
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @ParameterizedTest(name = "{0}: лист, правка, задания, достижения, ресурсы")
    @ValueSource(strings = {"owner", "guest", "member"})
    @DisplayName("участники кампании правят её")
    void participantsEdit(String name) throws Exception {
        // подготовка
        Cookie cookie = who(name);
        String sheet = mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value(name.equals("owner") ? "OWNER" : "LINK"))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(sheet, "$.version");
        long hunterId = ((Number) JsonPath.read(sheet, "$.hunters[0].id")).longValue();

        // вызов и проверка
        mockMvc.perform(json(patch("/api/v1/campaigns/" + campaignId), "{\"expectedVersion\": " + version + ", \"notes\": \"" + name + "\"}")
                        .cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/resources/adjust"),
                        "{\"changes\": {\"BONES\": 1}}").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/campaigns/" + campaignId + "/achievements"), "{\"name\": \"Затишье\"}").cookie(cookie))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("чужой не видит кампанию: 404 на всё, в списке её нет")
    void strangerSeesNothing() throws Exception {
        // вызов и проверка
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup").cookie(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId).with(xsrf()).cookie(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/campaigns").cookie(stranger)).andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"guest", "member"})
    @DisplayName("удаление кампании и управление ссылкой — только владельцу (403 OWNER_ONLY)")
    void ownerOnly(String name) throws Exception {
        // подготовка
        Cookie cookie = who(name);

        // вызов и проверка
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId).with(xsrf()).cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWNER_ONLY"));
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("гость по ссылке проходит пролог: подготовка, отметка, результат, переход главы")
    void guestPlaysPrologue() throws Exception {
        // подготовка
        UUID battle = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battle-setup").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("PROLOGUE"));

        // вызов
        mockMvc.perform(json(post("/api/v1/campaigns/" + campaignId + "/battles"), """
                        {"id": "%s", "questNumber": null, "bossCode": "VIRAXEN", "difficulty": 0, "chapter": 0,
                         "progressSeq": 0, "startedAt": "%s"}""".formatted(battle, clock.instant())).cookie(guest))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(owner))
                .andExpect(jsonPath("$.activeBattles[0].startedBy.kind").value("GUEST"))
                .andExpect(jsonPath("$.activeBattles[0].startedBy.name").value("Вадим"));
        mockMvc.perform(json(post("/api/v1/campaigns/" + campaignId + "/battles/" + battle + "/result"), """
                        {"questNumber": null, "bossCode": "VIRAXEN", "difficulty": 0, "chapter": 0, "progressSeq": 0,
                         "result": "VICTORY", "roundsPlayed": 3, "startedAt": "%s", "finishedAt": "%s",
                         "action": "ACCEPT"}""".formatted(clock.instant(), clock.instant())).cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"))
                .andExpect(jsonPath("$.campaign.access").value("LINK"));
        int version = JsonPath.read(mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/chapter-transition").cookie(guest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.version");
        mockMvc.perform(json(post("/api/v1/campaigns/" + campaignId + "/chapter-transition"),
                        "{\"action\": \"ACCEPT\", \"decisions\": {}, \"expectedVersion\": " + version + "}").cookie(guest))

                // проверка
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapter").value(1))
                .andExpect(jsonPath("$.recentBattles[0].submittedBy.name").value("Вадим"));
    }
}
