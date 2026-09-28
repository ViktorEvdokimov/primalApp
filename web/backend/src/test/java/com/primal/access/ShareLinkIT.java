package com.primal.access;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.identity.DeviceCookies;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Ссылка-приглашение")
class ShareLinkIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice@example.com");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания Алисы\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    /** Ссылка владельца; возвращает токен из адреса. */
    private String createLink() throws Exception {
        String body = mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** Гость без cookie открывает ссылку: возвращает cookie нового гостевого устройства. */
    private Cookie joinAsGuest(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAMPAIGN"))
                .andExpect(jsonPath("$.id").value(campaignId))
                .andExpect(cookie().exists(DeviceCookies.NAME))
                .andReturn();
        return result.getResponse().getCookie(DeviceCookies.NAME);
    }

    private ResultActions sheet(Cookie cookie) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(cookie));
    }

    @Test
    @DisplayName("приглашение видно без входа; гость без cookie получает устройство и доступ LINK")
    void guestJoins() throws Exception {
        // подготовка
        String token = createLink();

        // вызов и проверка
        mockMvc.perform(get("/api/v1/share/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAMPAIGN"))
                .andExpect(jsonPath("$.name").value("Кампания Алисы"))
                .andExpect(jsonPath("$.ownerName").value("alice"));
        Cookie guest = joinAsGuest(token, "Вадим");
        sheet(guest).andExpect(status().isOk()).andExpect(jsonPath("$.access").value("LINK"));
        mockMvc.perform(get("/api/v1/campaigns").cookie(guest))
                .andExpect(jsonPath("$[0].id").value(campaignId))
                .andExpect(jsonPath("$[0].access").value("LINK"));
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(alice))
                .andExpect(jsonPath("$.joined[0].kind").value("GUEST"))
                .andExpect(jsonPath("$.joined[0].name").value("Вадим"));
    }

    @Test
    @DisplayName("повторный вход ничего не дублирует; владелец по своей ссылке ничего не получает")
    void idempotentJoin() throws Exception {
        // подготовка
        String token = createLink();
        Cookie guest = joinAsGuest(token, "Вадим");

        // вызов
        mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf()).cookie(guest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(DeviceCookies.NAME));
        mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf()).cookie(alice)).andExpect(status().isOk());

        // проверка
        assertThat(jdbc.queryForObject("select count(*) from share_access", Integer.class)).isEqualTo(1);
        sheet(alice).andExpect(jsonPath("$.access").value("OWNER"));
    }

    @Test
    @DisplayName("перевыпуск и отзыв сразу закрывают доступ по старой ссылке; подделанный токен → 404")
    void reissueAndRevoke() throws Exception {
        // подготовка
        String first = createLink();
        Cookie guest = joinAsGuest(first, "Вадим");

        // вызов: перевыпуск
        String second = createLink();

        // проверка
        assertThat(second).isNotEqualTo(first);
        sheet(guest).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/share/" + first))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_LINK_INVALID"));

        // вход по новой ссылке и отзыв
        mockMvc.perform(post("/api/v1/share/" + second + "/join").with(xsrf()).cookie(guest)).andExpect(status().isOk());
        sheet(guest).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(alice))
                .andExpect(status().isNoContent());
        sheet(guest).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(alice)).andExpect(status().isNotFound());

        // подделанный токен
        String forged = second.substring(0, second.length() - 2) + (second.endsWith("AA") ? "BB" : "AA");
        mockMvc.perform(post("/api/v1/share/" + forged + "/join").with(xsrf())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("гость входит по коду — доступ переходит аккаунту и виден на другом устройстве пользователя")
    void guestSignsIn() throws Exception {
        // подготовка
        Cookie guest = joinAsGuest(createLink(), "Вадим");

        // вызов
        Cookie signedIn = auth.login("vadim@example.com", guest);

        // проверка
        assertThat(signedIn.getValue()).isEqualTo(guest.getValue());
        assertThat(jdbc.queryForObject("select count(*) from share_access where device_id is not null", Integer.class)).isZero();
        sheet(signedIn).andExpect(jsonPath("$.access").value("LINK"));
        Cookie otherDevice = auth.login("vadim@example.com");
        sheet(otherDevice).andExpect(status().isOk()).andExpect(jsonPath("$.access").value("LINK"));
        mockMvc.perform(get("/api/v1/campaigns").cookie(otherDevice)).andExpect(jsonPath("$[0].id").value(campaignId));
    }

    @Test
    @DisplayName("не больше 30 входов по ссылкам в час с одного адреса")
    void rateLimited() throws Exception {
        // подготовка
        String token = createLink();
        Cookie bob = auth.login("bob@example.com");
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf()).cookie(bob)).andExpect(status().isOk());
        }

        // вызов и проверка
        mockMvc.perform(post("/api/v1/share/" + token + "/join").with(xsrf()).cookie(bob))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
