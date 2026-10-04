package com.primal.admin;

import static com.primal.support.Xsrf.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.AuthHelper;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Администратор: правка каталога и статистика")
class AdminIT extends IntegrationTest {

    private static final String QUEST_ONE = """
            {"victory": [
               {"resources": {"BONES": 5}},
               {"if": {"chapterIn": [1]}, "then": [{"openQuest": 4}], "else": [{"grantAchievement": "ZATISHE"}]}
             ],
             "expired": [{"openQuest": 6}]}""";

    private Cookie admin;
    private Cookie player;

    @BeforeEach
    void users() throws Exception {
        admin = auth.login("admin");
        player = auth.login("player");
        jdbc.update("insert into app_admin (user_id) select id from app_user where login = ?", AuthHelper.loginOf("admin"));
    }

    @AfterEach
    void cleanUp() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/quests/1").with(xsrf()).cookie(admin));
        mockMvc.perform(delete("/api/v1/admin/chapters/4").with(xsrf()).cookie(admin));
        mockMvc.perform(delete("/api/v1/admin/forge/FIRE_01").with(xsrf()).cookie(admin));
        mockMvc.perform(delete("/api/v1/admin/lab/LAB_05").with(xsrf()).cookie(admin));
        mockMvc.perform(delete("/api/v1/admin/bosses/KOROVON").with(xsrf()).cookie(admin));
        deleteIdentityData();
    }

    private ResultActions editQuest(Cookie who, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/quests/1").with(xsrf()).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Nested
    @DisplayName("Доступ")
    class Access {

        @Test
        @DisplayName("«Кто я»: администратору admin = true, остальным false")
        void me() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/auth/me").cookie(admin)).andExpect(jsonPath("$.user.admin").value(true));
            mockMvc.perform(get("/api/v1/auth/me").cookie(player)).andExpect(jsonPath("$.user.admin").value(false));
        }

        @Test
        @DisplayName("не администратору — 404 на любой адрес, без входа — 401; ничего не меняется")
        void hidden() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/catalog").cookie(player)).andExpect(status().isNotFound());
            editQuest(player, QUEST_ONE).andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/v1/admin/chapters/4").with(xsrf()).cookie(player)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/admin/catalog")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/catalog/quests/1"))
                    .andExpect(jsonPath("$.victory.resources.BONES").value(2));
        }
    }

    @Nested
    @DisplayName("Задания")
    class Quests {

        @Test
        @DisplayName("каталог для редактора: 49 заданий, 11 глав, награды на языке каталога и текстом, дополнение")
        void catalog() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/catalog").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quests.length()").value(49))
                    .andExpect(jsonPath("$.chapters.length()").value(11))
                    .andExpect(jsonPath("$.quests[0].victory[0].resources.BONES").value(2))
                    .andExpect(jsonPath("$.quests[0].expired[0].openQuest").value(6))
                    .andExpect(jsonPath("$.quests[0].expiredText", contains("Добавить задание 6")))
                    .andExpect(jsonPath("$.quests[0].expansion").isEmpty())
                    .andExpect(jsonPath("$.quests[32].expansion").value("NIGHTMARE"))
                    .andExpect(jsonPath("$.quests[0].edited").value(false));
        }

        @Test
        @DisplayName("правка применяется сразу — каталог и награды; «Вернуть исходные» — снова YAML")
        void editAndReset() throws Exception {
            // вызов и проверка: правка
            editQuest(admin, QUEST_ONE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.edited").value(true))
                    .andExpect(jsonPath("$.victoryText", contains("Каждый охотник получает Кости 5",
                            "Если текущая глава 1, то добавить задание 4, иначе добавить достижение «Затишье»")));
            mockMvc.perform(get("/api/v1/catalog/quests/1"))
                    .andExpect(jsonPath("$.victory.resources.BONES").value(5));

            // вызов и проверка: возврат
            mockMvc.perform(delete("/api/v1/admin/quests/1").with(xsrf()).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.edited").value(false))
                    .andExpect(jsonPath("$.victory[0].resources.BONES").value(2));
            mockMvc.perform(get("/api/v1/catalog/quests/1"))
                    .andExpect(jsonPath("$.victory.resources.BONES").value(2));
        }

        @Test
        @DisplayName("правка переживает перезагрузку каталога из БД")
        void stored() throws Exception {
            // подготовка
            editQuest(admin, QUEST_ONE).andExpect(status().isOk());

            // вызов и проверка: строка в БД — источник правки
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "select victory_effects -> 0 -> 'resources' ->> 'BONES' from quest_override where number = 1",
                    String.class)).isEqualTo("5");
        }

        @Test
        @DisplayName("неверная правка — 400 с пояснением, ничего не сохранено")
        void invalid() throws Exception {
            // вызов и проверка
            editQuest(admin, "{\"victory\": [{\"teleport\": 1}], \"expired\": []}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("victory"))
                    .andExpect(jsonPath("$.errors[0].message").value("Победа, эффект 1: неизвестный вид эффекта teleport"));
            editQuest(admin, "{\"victory\": [{\"openQuest\": 99}], \"expired\": []}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("effects"));
            editQuest(admin, "{\"victory\": [{\"resources\": {\"FIRE\": 1}}], \"expired\": []}")
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/v1/admin/quests/99").with(xsrf()).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content(QUEST_ONE)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/admin/catalog").cookie(admin))
                    .andExpect(jsonPath("$.quests[0].edited").value(false));
        }
    }

    @Nested
    @DisplayName("Главы")
    class Chapters {

        @Test
        @DisplayName("эффекты главы 4: правка и возврат")
        void editAndReset() throws Exception {
            // вызов и проверка
            mockMvc.perform(put("/api/v1/admin/chapters/4").with(xsrf()).cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"effects\": [{\"forgeLevelUp\": true}, {\"message\": \"Тест\"}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.edited").value(true))
                    .andExpect(jsonPath("$.text", contains("Повышение уровня кузни", "Тест")));
            mockMvc.perform(get("/api/v1/catalog/chapters")).andExpect(jsonPath("$[3].openQuests").isEmpty());
            mockMvc.perform(delete("/api/v1/admin/chapters/4").with(xsrf()).cookie(admin))
                    .andExpect(jsonPath("$.edited").value(false));
            mockMvc.perform(get("/api/v1/catalog/chapters")).andExpect(jsonPath("$[3].openQuests[0]").value(11));
        }
    }

    @Nested
    @DisplayName("Условие «есть дополнение»")
    class ExpansionCondition {

        @Test
        @DisplayName("в наградах задания: разбирается и описывается для игроков; неизвестное дополнение — 400")
        void parsedAndDescribed() throws Exception {
            // вызов и проверка
            editQuest(admin, """
                    {"victory": [{"if": {"expansion": "FEATHER"}, "then": [{"openQuest": 36}], "else": [{"openQuest": 6}]}],
                     "expired": []}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.victoryText", contains(
                            "Если есть дополнение «Перо», добавить задание 36, иначе добавить задание 6")));
            editQuest(admin, """
                    {"victory": [{"if": {"expansion": "MOON"}, "then": [{"openQuest": 36}]}], "expired": []}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("victory"));
        }
    }

    @Nested
    @DisplayName("Цены кузни и лаборатории")
    class Prices {

        private static final String SIMPLE = """
                {"costs": [{"BONES": 1}, {"BONES": 1}, {"BONES": 1}]}""";

        private ResultActions forge(Cookie who, String code, String body) throws Exception {
            return mockMvc.perform(put("/api/v1/admin/forge/" + code).with(xsrf()).cookie(who)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        private ResultActions lab(String body) throws Exception {
            return mockMvc.perform(put("/api/v1/admin/lab/LAB_05").with(xsrf()).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        @Test
        @DisplayName("каталог для редактора: 108 предметов кузни и 6 зелий с ценами")
        void listed() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/catalog").cookie(admin))
                    .andExpect(jsonPath("$.forge.length()").value(108))
                    .andExpect(jsonPath("$.forge[0].code").value("FIRE_01"))
                    .andExpect(jsonPath("$.forge[0].costs[0].materials.BONES").value(1))
                    .andExpect(jsonPath("$.forge[0].edited").value(false))
                    .andExpect(jsonPath("$.lab.length()").value(6))
                    .andExpect(jsonPath("$.lab[4].units[1].options", contains("ANTHEMON", "MELLIS")));
        }

        @Test
        @DisplayName("«Язык пламени»: новая цена сразу в справочнике кузни; «Вернуть исходные»")
        void forgeEditAndReset() throws Exception {
            // вызов и проверка
            forge(admin, "FIRE_01", """
                    {"costs": [{"ZLATIA": 3}, {"SCALES": 1, "BLOOD": 1}, {"BONES": 1, "BLOOD": 1}]}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.edited").value(true))
                    .andExpect(jsonPath("$.costs[0].materials.ZLATIA").value(3));
            mockMvc.perform(get("/api/v1/catalog/forge"))
                    .andExpect(jsonPath("$[0].items[0].costs[0].materials.ZLATIA").value(3))
                    .andExpect(jsonPath("$[0].items[0].costs[0].materials.BONES").doesNotExist());
            mockMvc.perform(delete("/api/v1/admin/forge/FIRE_01").with(xsrf()).cookie(admin))
                    .andExpect(jsonPath("$.edited").value(false))
                    .andExpect(jsonPath("$.costs[0].materials.BONES").value(1));
        }

        @Test
        @DisplayName("неверная цена кузни: стихия, 0, больше 4 материй, не 3 уровня — 400; не администратору и нет предмета — 404")
        void forgeInvalid() throws Exception {
            // вызов и проверка
            forge(admin, "FIRE_01", """
                    {"costs": [{"FIRE": 1}, {"BONES": 1}, {"BONES": 1}]}""").andExpect(status().isBadRequest());
            forge(admin, "FIRE_01", """
                    {"costs": [{"BONES": 0}, {"BONES": 1}, {"BONES": 1}]}""").andExpect(status().isBadRequest());
            forge(admin, "FIRE_01", """
                    {"costs": [{"BONES": 3, "BLOOD": 2}, {"BONES": 1}, {"BONES": 1}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("costs"));
            forge(admin, "FIRE_01", """
                    {"costs": [{"BONES": 1}]}""").andExpect(status().isBadRequest());
            forge(player, "FIRE_01", SIMPLE).andExpect(status().isNotFound());
            forge(admin, "FIRE_99", SIMPLE).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("«Эвок»: ниллея и любое растение; пустой выбор — 400; «Вернуть исходные»")
        void labEditAndReset() throws Exception {
            // вызов и проверка
            lab("""
                    {"units": [{"options": ["NILLEA"], "any": false}, {"options": [], "any": true}]}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.units[0].options", contains("NILLEA")))
                    .andExpect(jsonPath("$.units[1].any").value(true));
            mockMvc.perform(get("/api/v1/catalog/lab")).andExpect(jsonPath("$[4].units[0].options", contains("NILLEA")));
            lab("""
                    {"units": [{"options": [], "any": false}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("units"));
            mockMvc.perform(delete("/api/v1/admin/lab/LAB_05").with(xsrf()).cookie(admin))
                    .andExpect(jsonPath("$.units[0].options", contains("TARMARET")));
        }
    }

    @Nested
    @DisplayName("Характеристики монстров")
    class Bosses {

        /** Коровон: уровни 0–3, на уровне 0 — новые стойки. */
        private static String stances(String levelZero) {
            String other = """
                    [{"toughnessPerHunter": 6, "mode": "HEALTH", "atHealth": 6},
                     {"toughnessPerHunter": null, "mode": "ON_DEMAND", "atHealth": null},
                     {"toughnessPerHunter": 8, "mode": "FINAL", "atHealth": null}]""";
            return "{\"difficulties\": {\"0\": " + levelZero + ", \"1\": " + other + ", \"2\": " + other + ", \"3\": " + other + "}}";
        }

        private ResultActions edit(String body) throws Exception {
            return mockMvc.perform(put("/api/v1/admin/bosses/KOROVON").with(xsrf()).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        @Test
        @DisplayName("каталог для редактора: все боссы со стойками по уровням")
        void listed() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/catalog").cookie(admin))
                    .andExpect(jsonPath("$.bosses[0].code").value("KOROVON"))
                    .andExpect(jsonPath("$.bosses[0].difficulties.0[0].toughnessPerHunter").value(2))
                    .andExpect(jsonPath("$.bosses[0].difficulties.0[0].stanceChange.atHealth").value(6))
                    .andExpect(jsonPath("$.bosses[0].edited").value(false));
        }

        @Test
        @DisplayName("стойки Коровона: сразу в справочнике боссов (бой берёт их оттуда); «Вернуть исходные»")
        void editAndReset() throws Exception {
            // вызов и проверка
            edit(stances("""
                    [{"toughnessPerHunter": 3, "mode": "HEALTH", "atHealth": 7},
                     {"toughnessPerHunter": 4, "mode": "HEALTH", "atHealth": 3},
                     {"toughnessPerHunter": 5, "mode": "FINAL", "atHealth": null}]"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.edited").value(true));
            mockMvc.perform(get("/api/v1/catalog/bosses"))
                    .andExpect(jsonPath("$[0].difficulties.0[0].toughnessPerHunter").value(3))
                    .andExpect(jsonPath("$[0].difficulties.0[1].stanceChange.mode").value("HEALTH"))
                    .andExpect(jsonPath("$[0].difficulties.1[1].toughnessPerHunter").isEmpty());
            mockMvc.perform(delete("/api/v1/admin/bosses/KOROVON").with(xsrf()).cookie(admin))
                    .andExpect(jsonPath("$.edited").value(false))
                    .andExpect(jsonPath("$.difficulties.0[0].toughnessPerHunter").value(2));
        }

        @Test
        @DisplayName("неверные стойки: меньше 3, порог вне 1–9, прочность 0, не те уровни — 400")
        void invalid() throws Exception {
            // вызов и проверка
            edit(stances("""
                    [{"toughnessPerHunter": 3, "mode": "FINAL", "atHealth": null}]"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("stances"));
            edit(stances("""
                    [{"toughnessPerHunter": 3, "mode": "HEALTH", "atHealth": 12},
                     {"toughnessPerHunter": 4, "mode": "HEALTH", "atHealth": 3},
                     {"toughnessPerHunter": 5, "mode": "FINAL", "atHealth": null}]""")).andExpect(status().isBadRequest());
            edit(stances("""
                    [{"toughnessPerHunter": 0, "mode": "HEALTH", "atHealth": 6},
                     {"toughnessPerHunter": 4, "mode": "HEALTH", "atHealth": 3},
                     {"toughnessPerHunter": 5, "mode": "FINAL", "atHealth": null}]""")).andExpect(status().isBadRequest());
            edit("""
                    {"difficulties": {"0": [{"toughnessPerHunter": 3, "mode": "HEALTH", "atHealth": 6},
                     {"toughnessPerHunter": 4, "mode": "HEALTH", "atHealth": 3},
                     {"toughnessPerHunter": 5, "mode": "FINAL", "atHealth": null}]}}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value(startsWith("Стойки: уровни враждебности")));
            mockMvc.perform(put("/api/v1/admin/bosses/KOROVON").with(xsrf()).cookie(player)
                    .contentType(MediaType.APPLICATION_JSON).content(stances("[]"))).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Статистика")
    class Stats {

        private long campaign(String name) throws Exception {
            String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + name + "\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(body, "$.id")).longValue();
        }

        /** Бой кампании, закончившийся {@code daysAgo} дней назад; {@code result = null} — ещё идёт. */
        private void battle(long campaignId, String status, String result, int daysAgo) {
            Timestamp at = Timestamp.from(Instant.now().minus(Duration.ofDays(daysAgo)));
            jdbc.update("""
                    insert into campaign_battle (id, campaign_id, purpose, difficulty, chapter, progress_seq, status, result,
                                                 defeat_reason, started_at, finished_at)
                    values (?, ?, 'FREE', 1, 1, 0, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), campaignId, status, result, "DEFEAT".equals(result) ? "ROUNDS" : null,
                    at, result == null ? null : at);
        }

        @Test
        @DisplayName("учётные записи, кампании и сыгранные бои — всего, за 30 и за 7 дней")
        void counts() throws Exception {
            // подготовка: игрок зарегистрирован 40 дней назад; вторая кампания пройдена и создана 10 дней назад
            jdbc.update("update app_user set created_at = now() - interval '40 days' where login = ?", AuthHelper.loginOf("player"));
            long first = campaign("Первая");
            long second = campaign("Вторая");
            jdbc.update("update campaign set status = 'COMPLETED', created_at = now() - interval '10 days' where id = ?", second);
            battle(first, "APPLIED", "VICTORY", 1);
            battle(first, "APPLIED", "DEFEAT", 10);
            battle(second, "DISMISSED", "VICTORY", 40);
            battle(first, "IN_PROGRESS", null, 0);
            battle(first, "ABANDONED", null, 2);

            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/stats").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accounts.total").value(2))
                    .andExpect(jsonPath("$.accounts.last30Days").value(1))
                    .andExpect(jsonPath("$.accounts.last7Days").value(1))
                    .andExpect(jsonPath("$.campaigns.created.total").value(2))
                    .andExpect(jsonPath("$.campaigns.created.last30Days").value(2))
                    .andExpect(jsonPath("$.campaigns.created.last7Days").value(1))
                    .andExpect(jsonPath("$.campaigns.active").value(1))
                    .andExpect(jsonPath("$.campaigns.completed").value(1))
                    .andExpect(jsonPath("$.battles.played.total").value(3))
                    .andExpect(jsonPath("$.battles.played.last30Days").value(2))
                    .andExpect(jsonPath("$.battles.played.last7Days").value(1))
                    .andExpect(jsonPath("$.battles.victories").value(2))
                    .andExpect(jsonPath("$.battles.defeats").value(1))
                    .andExpect(jsonPath("$.battles.inProgress").value(1));
        }

        @Test
        @DisplayName("не администратору — 404")
        void hidden() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/admin/stats").cookie(player)).andExpect(status().isNotFound());
        }
    }
}
