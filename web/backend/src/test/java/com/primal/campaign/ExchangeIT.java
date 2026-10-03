package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Обмен, преобразование и продажа по правилам")
class ExchangeIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long dareonId;
    private long miraId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        dareonId = ((Number) JsonPath.read(body, "$.hunters[0].id")).longValue();
        miraId = ((Number) JsonPath.read(body, "$.hunters[1].id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private void give(long hunterId, String changes) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/resources/adjust")
                        .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\": " + changes + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions call(String path, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + path).with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions exchange(String give, String receive) throws Exception {
        return call("/exchange", "{\"fromHunterId\": %d, \"toHunterId\": %d, \"give\": %s, \"receive\": %s}"
                .formatted(dareonId, miraId, give, receive));
    }

    private ResultActions sheet() throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice));
    }

    @Nested
    @DisplayName("Обмен между охотниками")
    class Exchange {

        @Test
        @DisplayName("1 огонь и 2 крови на 1 металл, 1 кость и 1 чешую — пример правил")
        void rulebookExample() throws Exception {
            // подготовка
            give(dareonId, "{\"FIRE\": 1, \"BLOOD\": 2}");
            give(miraId, "{\"METAL\": 1, \"BONES\": 1, \"SCALES\": 1}");

            // вызов и проверка
            exchange("{\"FIRE\": 1, \"BLOOD\": 2}", "{\"METAL\": 1, \"BONES\": 1, \"SCALES\": 1}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hunters[0].resources.METAL").value(1))
                    .andExpect(jsonPath("$.hunters[0].resources.SCALES").value(1))
                    .andExpect(jsonPath("$.hunters[0].resources.FIRE").doesNotExist())
                    .andExpect(jsonPath("$.hunters[1].resources.BLOOD").value(2))
                    .andExpect(jsonPath("$.hunters[1].resources.FIRE").value(1));
        }

        @Test
        @DisplayName("растения — тоже 1 к 1")
        void plants() throws Exception {
            // подготовка
            give(dareonId, "{\"NILLEA\": 1}");
            give(miraId, "{\"MELLIS\": 1}");

            // вызов и проверка
            exchange("{\"NILLEA\": 1}", "{\"MELLIS\": 1}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.hunters[0].resources.MELLIS").value(1));
        }

        @Test
        @DisplayName("не 1 к 1 внутри типа, просто так или с самим собой — 400, ничего не меняется")
        void rejected() throws Exception {
            // подготовка
            give(dareonId, "{\"FIRE\": 1, \"BLOOD\": 1}");
            give(miraId, "{\"BONES\": 2, \"NILLEA\": 1}");

            // вызов и проверка: стихия на материю, 1 на 2, просто так, сам с собой
            exchange("{\"FIRE\": 1}", "{\"BONES\": 1}").andExpect(status().isBadRequest());
            exchange("{\"BLOOD\": 1}", "{\"BONES\": 2}").andExpect(status().isBadRequest());
            exchange("{\"BLOOD\": 1}", "{}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value(
                            "Обмен — 1 к 1 внутри типа: стихию на стихию, материю на материю, растение на растение."));
            call("/exchange", "{\"fromHunterId\": %d, \"toHunterId\": %d, \"give\": {\"BLOOD\": 1}, \"receive\": {\"BLOOD\": 1}}"
                    .formatted(dareonId, dareonId)).andExpect(status().isBadRequest());
            sheet().andExpect(jsonPath("$.hunters[0].resources.BLOOD").value(1));
        }

        @Test
        @DisplayName("у второго не хватает — 422, первый тоже ничего не отдал")
        void notEnough() throws Exception {
            // подготовка
            give(dareonId, "{\"BLOOD\": 1}");

            // вызов и проверка
            exchange("{\"BLOOD\": 1}", "{\"BONES\": 1}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
            sheet().andExpect(jsonPath("$.hunters[0].resources.BLOOD").value(1))
                    .andExpect(jsonPath("$.hunters[1].resources.BLOOD").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Преобразование")
    class Convert {

        @Test
        @DisplayName("1 стихия вместо 1 материи; 2 материи вместо 1")
        void converts() throws Exception {
            // подготовка
            give(dareonId, "{\"FIRE\": 1, \"ZIMIA\": 1, \"IRIDIA\": 1}");

            // вызов и проверка
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"FIRE\"], \"gain\": \"SCALES\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.SCALES").value(1))
                    .andExpect(jsonPath("$.resources.FIRE").doesNotExist());
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"ZIMIA\", \"IRIDIA\"], \"gain\": \"SCALES\"}")
                    .andExpect(jsonPath("$.resources.SCALES").value(2))
                    .andExpect(jsonPath("$.resources.ZIMIA").doesNotExist());
        }

        @Test
        @DisplayName("не по правилам: растение, 2 стихии, 1 материя за 1, стихия как результат — 400")
        void rejected() throws Exception {
            // подготовка
            give(dareonId, "{\"FIRE\": 2, \"BLOOD\": 1, \"NILLEA\": 1}");

            // вызов и проверка
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"NILLEA\"], \"gain\": \"SCALES\"}").andExpect(status().isBadRequest());
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"FIRE\", \"FIRE\"], \"gain\": \"SCALES\"}").andExpect(status().isBadRequest());
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"BLOOD\"], \"gain\": \"SCALES\"}").andExpect(status().isBadRequest());
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"BLOOD\", \"FIRE\"], \"gain\": \"SCALES\"}").andExpect(status().isBadRequest());
            call("/hunters/" + dareonId + "/convert", "{\"spend\": [\"FIRE\"], \"gain\": \"HORN\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("Преобразование даёт материю."));
        }
    }

    @Nested
    @DisplayName("Продажа карты")
    class Sell {

        private long item(int index) throws Exception {
            return ((Number) JsonPath.read(sheet().andReturn().getResponse().getContentAsString(),
                    "$.hunters[0].items[" + index + "].id")).longValue();
        }

        @Test
        @DisplayName("стартовый «Большой меч» — за материю; карта исчезает")
        void sellsForMaterial() throws Exception {
            // вызов и проверка
            call("/hunters/" + dareonId + "/items/" + item(0) + "/sell", "{\"gain\": \"BLOOD\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.BLOOD").value(1))
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].name").value("Основной шлем"));
        }

        @Test
        @DisplayName("карта кузни — за стихию этой кузни; другая стихия и стихия за стартовую карту — 400")
        void sellsForOwnElement() throws Exception {
            // подготовка: «Язык пламени» из кузни огня
            jdbc.update("insert into campaign_trophy (campaign_id, boss_code, chapter, acquired_at) values (?, 'VIRAXEN', 0, now())",
                    campaignId);
            give(dareonId, "{\"FIRE\": 1, \"BONES\": 1, \"BLOOD\": 1}");
            call("/hunters/" + dareonId + "/forge", "{\"item\": \"FIRE_01\"}").andExpect(status().isCreated());

            // вызов и проверка
            call("/hunters/" + dareonId + "/items/" + item(4) + "/sell", "{\"gain\": \"HORN\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("За «Язык пламени» можно получить материю или стихию «Огонь»."));
            call("/hunters/" + dareonId + "/items/" + item(0) + "/sell", "{\"gain\": \"FIRE\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("За «Большой меч» можно получить только материю."));
            call("/hunters/" + dareonId + "/items/" + item(4) + "/sell", "{\"gain\": \"FIRE\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.FIRE").value(1));
        }

        @Test
        @DisplayName("зелье не сбрасывается вместо ресурса — 400")
        void potion() throws Exception {
            // вызов и проверка: «Алемор» — четвёртый стартовый предмет
            call("/hunters/" + dareonId + "/items/" + item(3) + "/sell", "{\"gain\": \"BLOOD\"}")
                    .andExpect(status().isBadRequest());
        }
    }
}
