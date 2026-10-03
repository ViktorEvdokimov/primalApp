package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@DisplayName("Инвентарь охотников")
class InventoryIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long dareonId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"KARA\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hunters[0].items[*].name",
                        Matchers.contains("Большой меч", "Основной шлем", "Основной доспех", "Алемор")))
                .andExpect(jsonPath("$.hunters[0].items[*].kind", Matchers.contains("EQUIPMENT", "EQUIPMENT", "EQUIPMENT", "POTION")))
                .andExpect(jsonPath("$.hunters[0].items[*].level", Matchers.contains(1, 1, 1, 1)))
                .andExpect(jsonPath("$.hunters[1].items[0].name").value("Парные клинки"))
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        dareonId = ((Number) JsonPath.read(body, "$.hunters[0].id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private String items() {
        return "/api/v1/campaigns/" + campaignId + "/hunters/" + dareonId + "/items";
    }

    private ResultActions add(String json) throws Exception {
        return mockMvc.perform(post(items()).with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long itemId(int index) throws Exception {
        String body = mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.hunters[0].items[" + index + "].id")).longValue();
    }

    private int version() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                .andReturn().getResponse().getContentAsString(), "$.version");
    }

    @Test
    @DisplayName("добавить предмет без оплаты: уровень по умолчанию 1, у карты награды уровня нет; версия растёт")
    void addsItems() throws Exception {
        // подготовка
        int version = version();

        // вызов и проверка
        add("{\"kind\": \"EQUIPMENT\", \"name\": \" Язык пламени \", \"level\": 2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[4].name").value("Язык пламени"))
                .andExpect(jsonPath("$.items[4].level").value(2))
                .andExpect(jsonPath("$.items[4].source").isEmpty());
        add("{\"kind\": \"REWARD\", \"name\": \"Карта награды №7\", \"level\": 3}")
                .andExpect(jsonPath("$.items[5].kind").value("REWARD"))
                .andExpect(jsonPath("$.items[5].level").isEmpty());
        add("{\"kind\": \"POTION\", \"name\": \"Эвок\"}").andExpect(jsonPath("$.items[6].level").value(1));
        assertThat(version()).isEqualTo(version + 3);
    }

    @Test
    @DisplayName("правка названия и уровня, удаление")
    void editsAndRemoves() throws Exception {
        // подготовка
        long sword = itemId(0);

        // вызов и проверка
        mockMvc.perform(patch(items() + "/" + sword).with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Большой меч\", \"level\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].level").value(3));
        mockMvc.perform(delete(items() + "/" + sword).with(xsrf()).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Основной шлем"));
    }

    @Test
    @DisplayName("ошибки: уровень вне 1–3, пустое название, чужой предмет — 404")
    void errors() throws Exception {
        // вызов и проверка
        add("{\"kind\": \"EQUIPMENT\", \"name\": \"Меч\", \"level\": 4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("level"));
        add("{\"kind\": \"EQUIPMENT\", \"name\": \" \"}").andExpect(status().isBadRequest());
        String karaItems = "/api/v1/campaigns/" + campaignId + "/hunters/" + dareonId + "/items/" + 999_999;
        mockMvc.perform(delete(karaItems).with(xsrf()).cookie(alice)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("охотник кампании, созданной до инвентаря, — без предметов")
    void legacyHunter() throws Exception {
        // подготовка: как строка, созданная до миграции V6
        jdbc.update("delete from hunter_item where hunter_id = ?", dareonId);

        // вызов и проверка
        mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                .andExpect(jsonPath("$.hunters[0].items").isEmpty());
    }
}
