package com.primal.access;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Основной персонаж участника")
class MainHunterIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Кампания Алисы", "hunters": [
                                  {"class": "DAREON", "playerName": "Алиса"}, {"class": "MIRA", "playerName": "Борис"},
                                  {"class": "TOREG", "playerName": "Вера"}]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions setMain(Cookie cookie, String hunterClass) throws Exception {
        String value = hunterClass == null ? "null" : "\"" + hunterClass + "\"";
        return mockMvc.perform(put("/api/v1/campaigns/" + campaignId + "/main-hunter").with(xsrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"hunterClass\": " + value + "}"));
    }

    private ResultActions sheet(Cookie cookie) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId).cookie(cookie));
    }

    private String createLink() throws Exception {
        String body = mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions join(String token, Cookie cookie, String body) throws Exception {
        var request = post("/api/v1/share/" + token + "/join").with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
    }

    @Test
    @DisplayName("выбор основного персонажа виден в листе и списке кампаний; null снимает выбор")
    void setAndClear() throws Exception {
        // подготовка
        sheet(alice).andExpect(jsonPath("$.mainHunter").isEmpty());

        // вызов
        setMain(alice, "MIRA").andExpect(status().isOk()).andExpect(jsonPath("$.mainHunter").value("MIRA"));

        // проверка
        sheet(alice).andExpect(jsonPath("$.mainHunter").value("MIRA"));
        mockMvc.perform(get("/api/v1/campaigns").cookie(alice)).andExpect(jsonPath("$[0].mainHunter").value("MIRA"));
        setMain(alice, "TOREG").andExpect(jsonPath("$.mainHunter").value("TOREG"));
        setMain(alice, null).andExpect(status().isOk()).andExpect(jsonPath("$.mainHunter").isEmpty());
        sheet(alice).andExpect(jsonPath("$.mainHunter").isEmpty());
    }

    @Test
    @DisplayName("охотника нет в отряде — 400 у поля hunterClass; без доступа к кампании — 404")
    void validation() throws Exception {
        // вызов и проверка
        setMain(alice, "KARA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("hunterClass"));
        setMain(auth.login("bob"), "MIRA").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("приглашение показывает отряд; гость выбирает персонажа — он основной, имя игрока — имя гостя")
    void guestChoosesHunter() throws Exception {
        // подготовка
        String token = createLink();
        mockMvc.perform(get("/api/v1/share/" + token))
                .andExpect(jsonPath("$.hunters[0].class").value("DAREON"))
                .andExpect(jsonPath("$.hunters[1].class").value("MIRA"))
                .andExpect(jsonPath("$.hunters[1].playerName").value("Борис"))
                .andExpect(jsonPath("$.hunters.length()").value(3));

        // вызов
        Cookie guest = join(token, null, "{\"hunterClass\": \"MIRA\"}").andExpect(status().isOk())
                .andReturn().getResponse().getCookie(DeviceCookies.NAME);

        // проверка: свой основной персонаж у гостя, у владельца — свой (не выбран)
        sheet(guest).andExpect(jsonPath("$.mainHunter").value("MIRA"));
        sheet(alice).andExpect(jsonPath("$.mainHunter").isEmpty());
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(alice))
                .andExpect(jsonPath("$.joined[0].name").value("Борис"));
    }

    @Test
    @DisplayName("гость с именем выбирает персонажа: имя не меняется; пользователь — выбор на аккаунт")
    void namedGuestAndUserChoose() throws Exception {
        // подготовка
        String token = createLink();
        Cookie guest = join(token, null, "{\"displayName\": \"Вадим\"}").andReturn().getResponse().getCookie(DeviceCookies.NAME);
        Cookie bob = auth.login("bob");

        // вызов
        join(token, guest, "{\"hunterClass\": \"TOREG\"}").andExpect(status().isOk());
        join(token, bob, "{\"hunterClass\": \"DAREON\"}").andExpect(status().isOk());

        // проверка
        sheet(guest).andExpect(jsonPath("$.mainHunter").value("TOREG"));
        sheet(bob).andExpect(jsonPath("$.mainHunter").value("DAREON"));
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/share-link").cookie(alice))
                .andExpect(jsonPath("$.joined[0].name").value("Вадим"));
    }

    @Test
    @DisplayName("охотника нет в отряде при входе — 400, доступ не выдаётся")
    void joinWithUnknownHunter() throws Exception {
        // подготовка
        String token = createLink();
        Cookie bob = auth.login("bob");

        // вызов и проверка
        join(token, bob, "{\"hunterClass\": \"KARA\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hunterClass"));
        sheet(bob).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("гость вошёл в аккаунт — его основной персонаж переходит аккаунту")
    void guestAttachedToAccount() throws Exception {
        // подготовка
        String token = createLink();
        Cookie guest = join(token, null, "{\"hunterClass\": \"MIRA\"}").andReturn().getResponse().getCookie(DeviceCookies.NAME);

        // вызов
        Cookie signedIn = auth.login("vadim", guest);

        // проверка
        sheet(signedIn).andExpect(status().isOk()).andExpect(jsonPath("$.mainHunter").value("MIRA"));
    }
}
