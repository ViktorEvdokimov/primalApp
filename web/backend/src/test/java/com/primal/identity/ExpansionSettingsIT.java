package com.primal.identity;

import static com.primal.support.Xsrf.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Дополнения игрока — настройка аккаунта (qa № 138)")
class ExpansionSettingsIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions change(Cookie who, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/me/expansions").with(xsrf()).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("по умолчанию — все; выбор сохраняется; пустой список — только базовая игра")
    void choose() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");

        // вызов и проверка
        mockMvc.perform(get("/api/v1/auth/me").cookie(alice))
                .andExpect(jsonPath("$.user.expansions", containsInAnyOrder("NIGHTMARE", "FEATHER", "POISON", "ICE")));
        change(alice, "{\"expansions\": [\"ICE\", \"FEATHER\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.expansions", contains("FEATHER", "ICE")));
        mockMvc.perform(get("/api/v1/auth/me").cookie(alice))
                .andExpect(jsonPath("$.user.expansions", contains("FEATHER", "ICE")));
        change(alice, "{\"expansions\": []}").andExpect(jsonPath("$.user.expansions").isEmpty());
    }

    @Test
    @DisplayName("неизвестное дополнение или нет списка — 400, настройка не меняется")
    void refusals() throws Exception {
        // подготовка
        Cookie alice = auth.login("alice");

        // вызов и проверка
        change(alice, "{\"expansions\": [\"MOON\"]}").andExpect(status().isBadRequest());
        change(alice, "{}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/me").cookie(alice))
                .andExpect(jsonPath("$.user.expansions.length()").value(4));
    }
}
