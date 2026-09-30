package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Кампании: создание, список, удаление")
class CampaignIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    /** Отряд из классов с пустыми именами игроков. */
    private static String squad(String... classes) {
        return Arrays.stream(classes).map(c -> "{\"class\": \"" + c + "\", \"playerName\": \"\"}")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private ResultActions create(Cookie device, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(device)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createCampaign(Cookie device, String name) throws Exception {
        String body = create(device, "{\"name\": \"" + name + "\", \"hunters\": " + squad("DAREON", "MIRA") + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Nested
    @DisplayName("Создание")
    class Create {

        @Test
        @DisplayName("новая кампания — глава 0 «Пролог», отряд в порядке выбора, пустое имя игрока — название класса")
        void createsPrologue() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");

            // вызов и проверка
            create(alice, """
                    {"name": "  Кампания Алисы ", "hunters": [
                      {"class": "MIRA", "playerName": " Алиса "},
                      {"class": "TOREG", "playerName": ""},
                      {"class": "KARA"}]}""")
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/campaigns/" + currentCampaignId())))
                    .andExpect(jsonPath("$.name").value("Кампания Алисы"))
                    .andExpect(jsonPath("$.chapter").value(0))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.access").value("OWNER"))
                    .andExpect(jsonPath("$.ownerName").value("alice"))
                    .andExpect(jsonPath("$.difficulty").value(0))
                    .andExpect(jsonPath("$.hunters[0].class").value("MIRA"))
                    .andExpect(jsonPath("$.hunters[0].playerName").value("Алиса"))
                    .andExpect(jsonPath("$.hunters[1].playerName").value("Торег"))
                    .andExpect(jsonPath("$.hunters[2].playerName").value("Кара"))
                    .andExpect(jsonPath("$.hunters[2].position").value(3))
                    .andExpect(jsonPath("$.quests.open").isEmpty())
                    .andExpect(jsonPath("$.pendingTransition").value(false));
        }

        private long currentCampaignId() {
            return jdbc.queryForObject("select coalesce(max(id), 0) from campaign", Long.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"1 охотник", "6 охотников"})
        @DisplayName("в отряде от 2 до 5 охотников")
        void squadSize(String caseName) throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            String hunters = caseName.startsWith("1")
                    ? squad("DAREON")
                    : squad("DAREON", "MIRA", "TOREG", "LIONAR", "KARA", "HELEREN");

            // вызов и проверка
            create(alice, "{\"name\": \"Отряд\", \"hunters\": " + hunters + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.detail").value("В отряде от 2 до 5 охотников"))
                    .andExpect(jsonPath("$.errors[0].field").value("hunters"));
            assertThat(count("select count(*) from campaign")).isZero();
        }

        @Test
        @DisplayName("классы не повторяются")
        void duplicateClass() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");

            // вызов и проверка
            create(alice, "{\"name\": \"Отряд\", \"hunters\": " + squad("DAREON", "DAREON") + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Классы охотников не должны повторяться"));
        }

        @Test
        @DisplayName("пустое название → 400")
        void blankName() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");

            // вызов и проверка
            create(alice, "{\"name\": \"  \", \"hunters\": " + squad("DAREON", "MIRA") + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("11-я кампания → 422 CAMPAIGN_LIMIT_REACHED")
        void limit() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            for (int i = 1; i <= 10; i++) {
                createCampaign(alice, "Кампания " + i);
            }

            // вызов и проверка
            create(alice, "{\"name\": \"Лишняя\", \"hunters\": " + squad("DAREON", "MIRA") + "}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CAMPAIGN_LIMIT_REACHED"));
        }

        @Test
        @DisplayName("гость по ссылке кампанию не создаёт → 403 ACCOUNT_REQUIRED")
        void guestCannotCreate() throws Exception {
            // подготовка
            String token = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("insert into device (id, token_hash) values (?, sha256(?::bytea))", UUID.randomUUID(), token);
            Cookie guest = new Cookie("PRIMAL_DEVICE", token);

            // вызов и проверка
            create(guest, "{\"name\": \"Гостевая\", \"hunters\": " + squad("DAREON", "MIRA") + "}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
        }

        @Test
        @DisplayName("без входа → 401")
        void anonymous() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/campaigns")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Список и доступ")
    class ListAndAccess {

        @Test
        @DisplayName("список: своя кампания с главой «Пролог» и отрядом; чужие не видны")
        void list() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            Cookie bob = auth.login("bob");
            long id = createCampaign(alice, "Кампания Алисы");
            createCampaign(bob, "Кампания Боба");

            // вызов и проверка
            mockMvc.perform(get("/api/v1/campaigns").cookie(alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(id))
                    .andExpect(jsonPath("$[0].chapter").value(0))
                    .andExpect(jsonPath("$[0].access").value("OWNER"))
                    .andExpect(jsonPath("$[0].hunters[0].class").value("DAREON"))
                    .andExpect(jsonPath("$[0].hunters[1].playerName").value("Мира"));
        }

        @Test
        @DisplayName("чужая кампания → 404 при просмотре, правке и удалении")
        void foreignCampaign() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            Cookie bob = auth.login("bob");
            long id = createCampaign(alice, "Кампания Алисы");

            // вызов и проверка
            mockMvc.perform(get("/api/v1/campaigns/{id}", id).cookie(bob)).andExpect(status().isNotFound());
            mockMvc.perform(patch("/api/v1/campaigns/{id}", id).with(xsrf()).cookie(bob)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\": 0, \"notes\": \"!\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/v1/campaigns/{id}", id).with(xsrf()).cookie(bob)).andExpect(status().isNotFound());
            assertThat(count("select count(*) from campaign where id = ?", id)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Удаление")
    class Delete {

        @Test
        @DisplayName("удаление убирает все данные кампании")
        void deletesEverything() throws Exception {
            // подготовка: у кампании есть навыки, ресурсы, задания, достижения, трофеи
            Cookie alice = auth.login("alice");
            long id = createCampaign(alice, "Кампания Алисы");
            long hunterId = jdbc.queryForObject("select min(id) from campaign_hunter where campaign_id = ?", Long.class, id);
            jdbc.update("insert into hunter_skill (hunter_id, branch, tier) values (?, 'A', 1)", hunterId);
            jdbc.update("insert into hunter_resource (hunter_id, resource, quantity) values (?, 'BONES', 3)", hunterId);
            jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, 1, 'OPEN', 1)", id);
            jdbc.update("""
                    insert into campaign_achievement (campaign_id, achievement_code, name, normalized_name, source, granted_in_chapter)
                    values (?, 'ZATISHE', 'Затишье', 'затишье', 'QUEST', 1)""", id);
            jdbc.update("insert into campaign_trophy (campaign_id, boss_code, chapter) values (?, 'VIRAXEN', 0)", id);

            // вызов
            mockMvc.perform(delete("/api/v1/campaigns/{id}", id).with(xsrf()).cookie(alice))
                    .andExpect(status().isNoContent());

            // проверка
            assertThat(count("select count(*) from campaign")).isZero();
            assertThat(count("select count(*) from campaign_hunter")).isZero();
            assertThat(count("select count(*) from hunter_skill")).isZero();
            assertThat(count("select count(*) from hunter_resource")).isZero();
            assertThat(count("select count(*) from campaign_quest")).isZero();
            assertThat(count("select count(*) from campaign_achievement")).isZero();
            assertThat(count("select count(*) from campaign_trophy")).isZero();
            mockMvc.perform(get("/api/v1/campaigns/{id}", id).cookie(alice)).andExpect(status().isNotFound());
        }
    }
}
