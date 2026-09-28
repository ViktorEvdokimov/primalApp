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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Охотники: навыки, ресурсы, имя игрока")
class HunterIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long hunterId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice@example.com");
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

    private String hunterUrl() {
        return "/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId;
    }

    private ResultActions unlock(String branch, int tier) throws Exception {
        return mockMvc.perform(post(hunterUrl() + "/skills").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"branch\": \"" + branch + "\", \"tier\": " + tier + "}"));
    }

    private ResultActions lock(String branch, int tier) throws Exception {
        return mockMvc.perform(delete(hunterUrl() + "/skills/" + branch + "/" + tier).with(xsrf()).cookie(alice));
    }

    private ResultActions adjust(String changes) throws Exception {
        return mockMvc.perform(post(hunterUrl() + "/resources/adjust").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"changes\": " + changes + "}"));
    }

    private int version() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                .andReturn().getResponse().getContentAsString(), "$.version");
    }

    @Nested
    @DisplayName("Навыки")
    class Skills {

        @Test
        @DisplayName("ступень 1, затем ступень 2 — охотник с навыками и доступными ступенями")
        void unlockTiers() throws Exception {
            // вызов и проверка
            unlock("B", 1)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.skills[0].branch").value("B"))
                    .andExpect(jsonPath("$.unlockableSkills[?(@.branch == 'B')].tier").value(2));
            unlock("B", 2)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.skills.length()").value(2))
                    .andExpect(jsonPath("$.unlockableSkills[?(@.branch == 'B')]").isEmpty());
        }

        @Test
        @DisplayName("ступень 2 без ступени 1 → 422 SKILL_LOCKED")
        void secondTierFirst() throws Exception {
            // вызов и проверка
            unlock("V", 2)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SKILL_LOCKED"));
        }

        @Test
        @DisplayName("повторное открытие ступени → 422 SKILL_LOCKED")
        void unlockTwice() throws Exception {
            // подготовка
            unlock("A", 1).andExpect(status().isCreated());

            // вызов и проверка
            unlock("A", 1)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Эта ступень уже открыта."));
        }

        @Test
        @DisplayName("снятие ступени 1 при открытой ступени 2 → 422; сначала снимается ступень 2")
        void lockOrder() throws Exception {
            // подготовка
            unlock("G", 1).andExpect(status().isCreated());
            unlock("G", 2).andExpect(status().isCreated());

            // вызов и проверка
            lock("G", 1)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SKILL_LOCKED"));
            lock("G", 2).andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(1));
            lock("G", 1).andExpect(status().isOk()).andExpect(jsonPath("$.skills").isEmpty());
        }

        @Test
        @DisplayName("изменение навыков видно в листе кампании и растит версию")
        void visibleInSheet() throws Exception {
            // подготовка
            int before = version();

            // вызов
            unlock("D", 1).andExpect(status().isCreated());

            // проверка
            mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice))
                    .andExpect(jsonPath("$.hunters[0].skills[0].branch").value("D"))
                    .andExpect(jsonPath("$.version").value(before + 1));
        }
    }

    @Nested
    @DisplayName("Ресурсы")
    class Resources {

        @Test
        @DisplayName("ресурсы начисляются и списываются; нулевые не передаются")
        void adjustResources() throws Exception {
            // вызов и проверка
            adjust("{\"BONES\": 3, \"FIRE\": 2}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.BONES").value(3))
                    .andExpect(jsonPath("$.resources.FIRE").value(2));
            adjust("{\"BONES\": 1, \"FIRE\": -2}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.BONES").value(4))
                    .andExpect(jsonPath("$.resources.FIRE").doesNotExist());
        }

        @Test
        @DisplayName("{BONES: 1, FIRE: -5} при 2 огня не меняет ничего и возвращает 422")
        void allOrNothing() throws Exception {
            // подготовка
            adjust("{\"FIRE\": 2}").andExpect(status().isOk());
            int before = version();

            // вызов
            adjust("{\"BONES\": 1, \"FIRE\": -5}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"))
                    .andExpect(jsonPath("$.detail").value("Не хватает ресурсов: Огонь."));

            // проверка
            assertThat(jdbc.queryForList("select resource || ':' || quantity from hunter_resource where hunter_id = ?",
                    String.class, hunterId)).containsExactly("FIRE:2");
            assertThat(version()).isEqualTo(before);
        }

        @Test
        @DisplayName("неизвестный ресурс или пустые изменения → 400")
        void invalidChanges() throws Exception {
            // вызов и проверка
            adjust("{\"GOLD\": 1}").andExpect(status().isBadRequest());
            adjust("{}").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Имя и доступ")
    class NameAndAccess {

        @Test
        @DisplayName("имя игрока меняется; пустое имя — название класса")
        void rename() throws Exception {
            // вызов и проверка
            mockMvc.perform(patch(hunterUrl()).with(xsrf()).cookie(alice)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"playerName\": \" Алиса К. \"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playerName").value("Алиса К."));
            mockMvc.perform(patch(hunterUrl()).with(xsrf()).cookie(alice)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"playerName\": \"\"}"))
                    .andExpect(jsonPath("$.playerName").value("Дареон"));
        }

        @Test
        @DisplayName("охотник чужой кампании или другой кампании → 404")
        void foreign() throws Exception {
            // подготовка
            Cookie bob = auth.login("bob@example.com");

            // вызов и проверка
            mockMvc.perform(post(hunterUrl() + "/skills").with(xsrf()).cookie(bob)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"branch\": \"A\", \"tier\": 1}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/999999/skills").with(xsrf()).cookie(alice)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"branch\": \"A\", \"tier\": 1}"))
                    .andExpect(status().isNotFound());
        }
    }
}
