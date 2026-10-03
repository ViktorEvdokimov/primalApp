package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Лаборатория: приготовление зелий")
class LabIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long hunterId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\", \"playerName\": \"Алиса\"},"
                                + " {\"class\": \"MIRA\", \"playerName\": \"Вадим\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        hunterId = ((Number) JsonPath.read(body, "$.hunters[0].id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private void give(String changes) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/resources/adjust")
                        .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\": " + changes + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions brew(String potion, String plants) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/lab")
                .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"potion\": \"" + potion + "\", \"plants\": " + plants + "}"));
    }

    @Test
    @DisplayName("«Имперум»: −1 антемон, −1 ниллея; уровень зелья — уровень лаборатории")
    void brewsPotion() throws Exception {
        // подготовка
        give("{\"ANTHEMON\": 1, \"NILLEA\": 2}");
        jdbc.update("update campaign set lab_level = 2 where id = ?", campaignId);

        // вызов и проверка
        brew("LAB_02", "[\"ANTHEMON\", \"NILLEA\"]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.potion").value("LAB_02"))
                .andExpect(jsonPath("$.name").value("Имперум"))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.hunter.resources.NILLEA").value(1))
                .andExpect(jsonPath("$.hunter.resources.ANTHEMON").doesNotExist())
                .andExpect(jsonPath("$.hunter.items[-1].name").value("Имперум"))
                .andExpect(jsonPath("$.hunter.items[-1].kind").value("POTION"))
                .andExpect(jsonPath("$.hunter.items[-1].level").value(2));
    }

    @Test
    @DisplayName("пример из правил: «Эвок» — тармарет и меллис вместо антемона")
    void choice() throws Exception {
        // подготовка
        give("{\"TARMARET\": 1, \"MELLIS\": 1}");

        // вызов и проверка
        brew("LAB_05", "[\"TARMARET\", \"MELLIS\"]").andExpect(status().isCreated()).andExpect(jsonPath("$.hunter.resources").isEmpty());
    }

    @Test
    @DisplayName("«Алемор» — любые 2 растения, в том числе два одинаковых")
    void anyPlants() throws Exception {
        // подготовка
        give("{\"SELICORNIA\": 2}");

        // вызов и проверка
        brew("LAB_01", "[\"SELICORNIA\", \"SELICORNIA\"]").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("растение не с планшета или не на своём месте — 400 с ошибкой поля plants, ничего не списано")
    void wrongPlant() throws Exception {
        // подготовка
        give("{\"TARMARET\": 1, \"NILLEA\": 1}");

        // вызов и проверка
        brew("LAB_05", "[\"TARMARET\", \"NILLEA\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("plants"))
                .andExpect(jsonPath("$.errors[0].message").value("«Ниллея» не подходит для «Эвок»."));
        brew("LAB_05", "[\"TARMARET\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Для «Эвок» нужно 2 растения."));
        mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                .andExpect(jsonPath("$.hunters[0].resources.TARMARET").value(1));
    }

    @Test
    @DisplayName("не хватает растений — 422 NOT_ENOUGH_RESOURCES; неизвестное зелье — 404")
    void notEnough() throws Exception {
        // подготовка
        give("{\"ANTHEMON\": 1}");

        // вызов и проверка
        brew("LAB_02", "[\"ANTHEMON\", \"NILLEA\"]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"))
                .andExpect(jsonPath("$.detail").value("Не хватает ресурсов: Ниллея."));
        brew("LAB_07", "[\"ANTHEMON\", \"NILLEA\"]").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /catalog/lab — 6 зелий; выбор и «любое» видны в цене")
    void catalog() throws Exception {
        // вызов и проверка
        mockMvc.perform(get("/api/v1/catalog/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("Алемор"))
                .andExpect(jsonPath("$[0].units[0].any").value(true))
                .andExpect(jsonPath("$[0].units[0].options.length()").value(6))
                .andExpect(jsonPath("$[4].units[1].any").value(false))
                .andExpect(jsonPath("$[4].units[1].options", Matchers.contains("ANTHEMON", "MELLIS")));
    }
}
