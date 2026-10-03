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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Кузня: открытые кузни и создание снаряжения")
class ForgeIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;
    private long dareonId;
    private long miraId;

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
        dareonId = ((Number) JsonPath.read(body, "$.hunters[0].id")).longValue();
        miraId = ((Number) JsonPath.read(body, "$.hunters[1].id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    /** Трофей босса — как после принятой победы (BattleOutcomes). */
    private void trophy(String bossCode) {
        jdbc.update("insert into campaign_trophy (campaign_id, boss_code, chapter, acquired_at) values (?, ?, 0, now())",
                campaignId, bossCode);
    }

    private void give(long hunterId, String changes) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/resources/adjust")
                        .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\": " + changes + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions craft(long hunterId, String item) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + hunterId + "/forge")
                .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"item\": \"" + item + "\"}"));
    }

    private ResultActions sheet() throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId).cookie(alice));
    }

    @Nested
    @DisplayName("Открытые кузни")
    class OpenForges {

        @Test
        @DisplayName("без трофеев кузни закрыты; трофеи открывают стихии побеждённых боссов, без повторов")
        void byTrophies() throws Exception {
            // подготовка и проверка
            sheet().andExpect(jsonPath("$.openForges").isEmpty())
                    .andExpect(jsonPath("$.forgeLevel").value(1));

            // вызов: Вираксен (огонь) дважды и Коровон (коралл)
            trophy("VIRAXEN");
            trophy("KOROVON");
            trophy("VIRAXEN");

            // проверка: порядок справочника стихий
            sheet().andExpect(jsonPath("$.openForges", Matchers.contains("FIRE", "CORAL")));
        }
    }

    @Nested
    @DisplayName("Создание")
    class Craft {

        @Test
        @DisplayName("«Язык пламени» Дареону: −1 огонь, −1 кости, −1 кровь; версия кампании растёт")
        void craftsWeapon() throws Exception {
            // подготовка
            trophy("VIRAXEN");
            give(dareonId, "{\"FIRE\": 2, \"BONES\": 1, \"BLOOD\": 3}");
            int version = JsonPath.read(sheet().andReturn().getResponse().getContentAsString(), "$.version");

            // вызов
            craft(dareonId, "FIRE_01")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.item").value("FIRE_01"))
                    .andExpect(jsonPath("$.name").value("Язык пламени"))
                    .andExpect(jsonPath("$.level").value(1))
                    .andExpect(jsonPath("$.hunter.resources.FIRE").value(1))
                    .andExpect(jsonPath("$.hunter.resources.BLOOD").value(2))
                    .andExpect(jsonPath("$.hunter.resources.BONES").doesNotExist())
                    .andExpect(jsonPath("$.hunter.items[-1].name").value("Язык пламени"))
                    .andExpect(jsonPath("$.hunter.items[-1].kind").value("EQUIPMENT"))
                    .andExpect(jsonPath("$.hunter.items[-1].level").value(1))
                    .andExpect(jsonPath("$.hunter.items[-1].element").value("FIRE"))
                    .andExpect(jsonPath("$.hunter.items[-1].source").value("FIRE_01"));

            // проверка
            sheet().andExpect(jsonPath("$.version").value(version + 1));
        }

        @Test
        @DisplayName("на 2-м уровне кузни — цена 2-го уровня: «Язык пламени» — чешуя и кровь")
        void usesForgeLevel() throws Exception {
            // подготовка
            trophy("VIRAXEN");
            jdbc.update("update campaign set forge_level = 2 where id = ?", campaignId);
            give(dareonId, "{\"FIRE\": 1, \"SCALES\": 1, \"BLOOD\": 1}");

            // вызов и проверка
            craft(dareonId, "FIRE_01")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.level").value(2))
                    .andExpect(jsonPath("$.hunter.resources").isEmpty());
        }

        @Test
        @DisplayName("шлем может создать любой класс")
        void helmetForAnyone() throws Exception {
            // подготовка: «Чешуйчатый шлем» — 1 чешуя, 1 кровь
            trophy("VIRAXEN");
            give(miraId, "{\"FIRE\": 1, \"SCALES\": 1, \"BLOOD\": 1}");

            // вызов и проверка
            craft(miraId, "FIRE_09").andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Чешуйчатый шлем"));
        }

        @Test
        @DisplayName("оружие чужого класса — 422 FORGE_UNAVAILABLE, ресурсы не тронуты")
        void foreignWeapon() throws Exception {
            // подготовка
            trophy("VIRAXEN");
            give(miraId, "{\"FIRE\": 1, \"BONES\": 1, \"BLOOD\": 1}");

            // вызов и проверка: «Язык пламени» — большой меч Дареона
            craft(miraId, "FIRE_01")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("FORGE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.detail").value("«Язык пламени» — оружие класса Дареон: его может создать только этот охотник."));
            sheet().andExpect(jsonPath("$.hunters[1].resources.FIRE").value(1));
        }

        @Test
        @DisplayName("кузня стихии не открыта — 422 FORGE_UNAVAILABLE")
        void lockedForge() throws Exception {
            // подготовка: огонь открыт, коралл — нет
            trophy("VIRAXEN");
            give(dareonId, "{\"CORAL\": 1, \"SCALES\": 1, \"ZIMIA\": 1}");

            // вызов и проверка: «Кровавый риф»
            craft(dareonId, "CORAL_01")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("FORGE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.detail").value(Matchers.startsWith("Кузня стихии «Коралл» ещё не открыта")));
        }

        @Test
        @DisplayName("не хватает ресурсов — 422 NOT_ENOUGH_RESOURCES с перечнем, ничего не списано")
        void notEnough() throws Exception {
            // подготовка: есть огонь и кости, крови нет
            trophy("VIRAXEN");
            give(dareonId, "{\"FIRE\": 1, \"BONES\": 1}");

            // вызов и проверка
            craft(dareonId, "FIRE_01")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"))
                    .andExpect(jsonPath("$.detail").value("Не хватает ресурсов: Кровь."));
            sheet().andExpect(jsonPath("$.hunters[0].resources.FIRE").value(1))
                    .andExpect(jsonPath("$.hunters[0].resources.BONES").value(1));
        }

        @Test
        @DisplayName("неизвестный предмет — 404; чужой охотник — 404")
        void notFound() throws Exception {
            // подготовка
            trophy("VIRAXEN");

            // вызов и проверка
            craft(dareonId, "FIRE_13").andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/999999/forge")
                            .with(xsrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"item\": \"FIRE_01\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("без доступа к кампании — 403")
        void stranger() throws Exception {
            // подготовка
            trophy("VIRAXEN");
            Cookie bob = auth.login("bob");

            // вызов и проверка
            mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/hunters/" + dareonId + "/forge")
                            .with(xsrf()).cookie(bob).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"item\": \"FIRE_01\"}"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value(Matchers.oneOf("FORBIDDEN", "NOT_FOUND")));
        }
    }

    @Nested
    @DisplayName("Справочник")
    class CatalogApi {

        @Test
        @DisplayName("GET /catalog/forge — 9 планшетов, у оружия класс, цены на 3 уровня")
        void forgeCatalog() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/forge"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(9))
                    .andExpect(jsonPath("$[0].element").value("FIRE"))
                    .andExpect(jsonPath("$[0].items.length()").value(12))
                    .andExpect(jsonPath("$[0].items[0].code").value("FIRE_01"))
                    .andExpect(jsonPath("$[0].items[0].slot").value("GREATSWORD"))
                    .andExpect(jsonPath("$[0].items[0].hunterClass").value("DAREON"))
                    .andExpect(jsonPath("$[0].items[0].costs[0].materials.BONES").value(1))
                    .andExpect(jsonPath("$[0].items[0].costs[0].materials.BLOOD").value(1))
                    .andExpect(jsonPath("$[0].items[8].hunterClass").isEmpty())
                    .andExpect(jsonPath("$[0].items[2].costs[0].materials.BLOOD").value(2));
        }
    }
}
