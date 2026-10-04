package com.primal.progression;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Результат боя кампании")
class BattleResultIT extends IntegrationTest {

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    // --- подготовка кампании ---

    private void chapter(int chapter) {
        jdbc.update("update campaign set chapter = ? where id = ?", chapter, campaignId);
    }

    private void quest(int number, String status) {
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, ?, ?, 1)",
                campaignId, number, status);
    }

    // --- запросы ---

    /** Результат боя: поля старта и итог; {@code extra} — action и overrides. */
    private static String report(Integer quest, String boss, int chapter, int progressSeq, String result, String extra) {
        String defeatReason = "DEFEAT".equals(result) ? "\"ROUNDS\"" : "null";
        return """
                {"questNumber": %s, "bossCode": %s, "difficulty": 1, "chapter": %d, "progressSeq": %d,
                 "result": "%s", "defeatReason": %s, "roundsPlayed": 6,
                 "startedAt": "2026-09-27T18:35:00Z", "finishedAt": "2026-09-27T19:05:00Z"%s}"""
                .formatted(quest, boss == null ? "null" : "\"" + boss + "\"", chapter, progressSeq, result, defeatReason,
                        extra.isEmpty() ? "" : ", " + extra);
    }

    private ResultActions preview(UUID battleId, String body) throws Exception {
        return mockMvc.perform(post(path(battleId) + "/result/preview").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submit(UUID battleId, String body) throws Exception {
        return mockMvc.perform(post(path(battleId) + "/result").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions accept(UUID battleId, Integer quest, String boss, int chapter, int progressSeq, String result)
            throws Exception {
        return submit(battleId, report(quest, boss, chapter, progressSeq, result, "\"action\": \"ACCEPT\""));
    }

    private void startMark(UUID battleId, Integer quest, String boss, int chapter) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/battles").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "questNumber": %s, "bossCode": "%s", "difficulty": 1, "chapter": %d,
                                 "progressSeq": 0, "startedAt": "%s"}""".formatted(battleId, quest, boss, chapter, clock.instant())))
                .andExpect(status().isCreated());
    }

    private String path(UUID battleId) {
        return "/api/v1/campaigns/" + campaignId + "/battles/" + battleId;
    }

    // --- состояние в БД ---

    private List<Integer> resource(String code) {
        return jdbc.queryForList("""
                select coalesce(r.quantity, 0) from campaign_hunter h
                left join hunter_resource r on r.hunter_id = h.id and r.resource = ?
                where h.campaign_id = ? order by h.position""", Integer.class, code, campaignId);
    }

    private int resourceRows() {
        return jdbc.queryForObject("""
                select count(*) from hunter_resource r join campaign_hunter h on h.id = r.hunter_id
                where h.campaign_id = ?""", Integer.class, campaignId);
    }

    private String questStatus(int number) {
        List<String> rows = jdbc.queryForList("select status from campaign_quest where campaign_id = ? and quest_number = ?",
                String.class, campaignId, number);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String campaignStatus() {
        return jdbc.queryForObject("select status from campaign where id = ?", String.class, campaignId);
    }

    private int progressSeq() {
        return jdbc.queryForObject("select progress_seq from campaign where id = ?", Integer.class, campaignId);
    }

    private String battleStatus(UUID id) {
        return jdbc.queryForObject("select status from campaign_battle where id = ?", String.class, id);
    }

    private List<String> trophies() {
        return jdbc.queryForList("select boss_code from campaign_trophy where campaign_id = ? order by id", String.class, campaignId);
    }

    @Nested
    @DisplayName("Победа по заданию")
    class QuestVictory {

        @Test
        @DisplayName("задание 1 в главе 2: превью совпадает с применённым, повтор ничего не меняет")
        void questOne() throws Exception {
            // подготовка
            chapter(2);
            quest(1, "OPEN");
            UUID battle = UUID.randomUUID();

            // вызов и проверка: превью
            preview(battle, report(1, "TORAMAT", 2, 0, "VICTORY", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("VICTORY"))
                    .andExpect(jsonPath("$.purpose").value("QUEST"))
                    .andExpect(jsonPath("$.quest.number").value(1))
                    .andExpect(jsonPath("$.quest.name").value("Память пустыни"))
                    .andExpect(jsonPath("$.rewards.trophy.code").value("TORAMAT"))
                    .andExpect(jsonPath("$.rewards.perHunter.HORN").value(2))
                    .andExpect(jsonPath("$.rewards.perHunter.BONES").value(2))
                    .andExpect(jsonPath("$.rewards.perHunter.SELICORNIA").value(1))
                    .andExpect(jsonPath("$.rewards.openQuests").value(contains(4)))
                    .andExpect(jsonPath("$.rules[0].kind").value("QUEST"))
                    .andExpect(jsonPath("$.rules[0].result").value("Добавлено задание 4."))
                    .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"))
                    .andExpect(jsonPath("$.dismissConsequences").value(contains(
                            "Задание 1 «Память пустыни» не будет отмечено выполненным.",
                            "Трофей «Торамат» не будет получен.",
                            "Охотники не получат ресурсы: Рог 2, Кости 2, Златия 2, Ниллея 2, Тармарет 1, Альбалацея 1, Селикорния 1.",
                            "Не будет добавлено задание 4.",
                            "Переход в главу 3 не состоится, глава останется 2.",
                            "Награды можно внести вручную на листе кампании.")));
            assertThat(resourceRows()).isZero();

            // вызов и проверка: «Принять»
            accept(battle, 1, "TORAMAT", 2, 0, "VICTORY")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"))
                    .andExpect(jsonPath("$.campaign.pendingTransition").value(true))
                    .andExpect(jsonPath("$.campaign.trophies[0].boss.code").value("TORAMAT"))
                    .andExpect(jsonPath("$.campaign.recentBattles[0].id").value(battle.toString()))
                    .andExpect(jsonPath("$.campaign.recentBattles[0].result").value("VICTORY"))
                    .andExpect(jsonPath("$.campaign.recentBattles[0].submittedBy.name").value("alice"));
            assertThat(resource("HORN")).containsExactly(2, 2);
            assertThat(resource("BONES")).containsExactly(2, 2);
            assertThat(resource("TARMARET")).containsExactly(1, 1);
            assertThat(questStatus(1)).isEqualTo("COMPLETED");
            assertThat(questStatus(4)).isEqualTo("OPEN");
            assertThat(trophies()).containsExactly("TORAMAT");
            assertThat(campaignStatus()).isEqualTo("CHAPTER_TRANSITION");
            assertThat(progressSeq()).isEqualTo(1);
            assertThat(battleStatus(battle)).isEqualTo("APPLIED");

            // повторная отправка ничего не меняет
            accept(battle, 1, "TORAMAT", 2, 0, "VICTORY")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"));
            assertThat(resource("HORN")).containsExactly(2, 2);
            assertThat(trophies()).hasSize(1);
            assertThat(progressSeq()).isEqualTo(1);
        }

        @Test
        @DisplayName("задание 3 (app): Коралл 2 и ресурсы задания каждому, достижение «Затишье»")
        void questThree() throws Exception {
            // подготовка
            chapter(1);
            quest(3, "OPEN");

            // вызов
            accept(UUID.randomUUID(), 3, "KOROVON", 1, 0, "VICTORY").andExpect(status().isOk());

            // проверка
            assertThat(resource("CORAL")).containsExactly(2, 2);
            assertThat(resource("SCALES")).containsExactly(1, 1);
            assertThat(jdbc.queryForList("select name from campaign_achievement where campaign_id = ?", String.class, campaignId))
                    .containsExactly("Затишье");
        }

        @Test
        @DisplayName("«Редактировать»: применяются ровно переданные награды")
        void overrides() throws Exception {
            // подготовка
            chapter(2);
            quest(1, "OPEN");
            UUID battle = UUID.randomUUID();
            String overrides = """
                    "action": "ACCEPT", "overrides": {"bossCode": "TORAMAT", "perHunter": {"HORN": 2, "BONES": 2, "ZLATIA": 1},
                     "openQuests": [4], "achievements": ["TAYNY_PROSHLOGO"]}""";

            // вызов
            submit(battle, report(1, "TORAMAT", 2, 0, "VICTORY", overrides)).andExpect(status().isOk());

            // проверка
            assertThat(resource("HORN")).containsExactly(2, 2);
            assertThat(resource("ZLATIA")).containsExactly(1, 1);
            assertThat(resource("NILLEA")).containsExactly(0, 0);
            assertThat(questStatus(1)).isEqualTo("COMPLETED");
            assertThat(questStatus(4)).isEqualTo("OPEN");
            assertThat(jdbc.queryForList("select achievement_code from campaign_achievement where campaign_id = ?",
                    String.class, campaignId)).containsExactly("TAYNY_PROSHLOGO");
            assertThat(jdbc.queryForObject("select overrides ->> 'bossCode' from campaign_battle where id = ?", String.class, battle))
                    .isEqualTo("TORAMAT");
        }
    }

    @Nested
    @DisplayName("Пролог, бой без задания, финал")
    class Purposes {

        @Test
        @DisplayName("пролог (36.1): трофей Вираксена и 2 «Огня» каждому, задания не открываются")
        void prologue() throws Exception {
            // подготовка
            UUID battle = UUID.randomUUID();

            // вызов и проверка
            preview(battle, report(null, "VIRAXEN", 0, 0, "VICTORY", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.purpose").value("PROLOGUE"))
                    .andExpect(jsonPath("$.quest").isEmpty())
                    .andExpect(jsonPath("$.rewards.trophy.code").value("VIRAXEN"))
                    .andExpect(jsonPath("$.rewards.perHunter.FIRE").value(2))
                    .andExpect(jsonPath("$.rewards.openQuests").isEmpty())
                    .andExpect(jsonPath("$.dismissConsequences").value(contains(
                            "Трофей «Вираксен» не будет получен.",
                            "Охотники не получат ресурсы: Огонь 2.",
                            "Переход в главу 1 не состоится, останется пролог.",
                            "Награды можно внести вручную на листе кампании.")));
            accept(battle, null, "VIRAXEN", 0, 0, "VICTORY")
                    .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"))
                    .andExpect(jsonPath("$.campaign.chapter").value(0));
            assertThat(resource("FIRE")).containsExactly(2, 2);
            assertThat(trophies()).containsExactly("VIRAXEN");
            assertThat(jdbc.queryForObject("select count(*) from campaign_quest where campaign_id = ?", Integer.class, campaignId))
                    .isZero();
        }

        @Test
        @DisplayName("бой без задания (42.4): трофей и 2 стихии выбранного босса, без босса — 422 BOSS_REQUIRED")
        void freeBattle() throws Exception {
            // подготовка
            chapter(3);

            // вызов и проверка: без босса превью есть, «Принять» — нет
            UUID manual = UUID.randomUUID();
            preview(manual, report(null, null, 3, 0, "VICTORY", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.purpose").value("FREE"))
                    .andExpect(jsonPath("$.rewards.trophy").isEmpty());
            accept(manual, null, null, 3, 0, "VICTORY")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("BOSS_REQUIRED"));

            // с выбранным Тораматом — трофей и только 2 «Рога»
            accept(UUID.randomUUID(), null, "TORAMAT", 3, 0, "VICTORY").andExpect(status().isOk());
            assertThat(resource("HORN")).containsExactly(2, 2);
            assertThat(resourceRows()).isEqualTo(2);
            assertThat(trophies()).containsExactly("TORAMAT");
        }

        @Test
        @DisplayName("финальный бой: кампания пройдена, у Пробуждённого стихий нет")
        void finalBattle() throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = 11, final_boss_code = 'AWAKENED' where id = ?", campaignId);

            // вызов и проверка
            accept(UUID.randomUUID(), null, "AWAKENED", 11, 0, "VICTORY")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next").value("CAMPAIGN_COMPLETED"))
                    .andExpect(jsonPath("$.campaign.status").value("COMPLETED"));
            assertThat(trophies()).containsExactly("AWAKENED");
            assertThat(resourceRows()).isZero();
        }
    }

    @Nested
    @DisplayName("Поражение")
    class Defeat {

        @Test
        @DisplayName("задание 47 (D-15, qa 135): поражение ничего не даёт — «Оледенение» выдаётся при истечении, не здесь")
        void quest47() throws Exception {
            // подготовка
            chapter(5);
            quest(47, "OPEN");
            UUID battle = UUID.randomUUID();

            // вызов и проверка
            preview(battle, report(47, "SIRKAAJ", 5, 0, "DEFEAT", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rewards.trophy").isEmpty())
                    .andExpect(jsonPath("$.rewards.perHunter").isEmpty())
                    .andExpect(jsonPath("$.rewards.achievements").isEmpty())
                    .andExpect(jsonPath("$.rewards.openQuests").isEmpty())
                    .andExpect(jsonPath("$.rules").isEmpty())
                    .andExpect(jsonPath("$.next").value("CAMPAIGN_SHEET"));
            accept(battle, 47, "SIRKAAJ", 5, 0, "DEFEAT")
                    .andExpect(jsonPath("$.next").value("CAMPAIGN_SHEET"))
                    .andExpect(jsonPath("$.campaign.pendingTransition").value(false));
            assertThat(jdbc.queryForList("select achievement_code from campaign_achievement where campaign_id = ?",
                    String.class, campaignId)).isEmpty();
            assertThat(questStatus(47)).isEqualTo("OPEN");
            assertThat(trophies()).isEmpty();
            assertThat(progressSeq()).isZero();
        }

        @Test
        @DisplayName("задание 3: поражение не открывает задание 10 — это последствие истечения задания 3")
        void questThree() throws Exception {
            // подготовка
            chapter(1);
            quest(3, "OPEN");

            // вызов
            accept(UUID.randomUUID(), 3, "KOROVON", 1, 0, "DEFEAT").andExpect(status().isOk());

            // проверка
            assertThat(jdbc.queryForObject("select count(*) from campaign_quest where campaign_id = ? and quest_number = 10",
                    Integer.class, campaignId)).isZero();
            assertThat(questStatus(3)).isEqualTo("OPEN");
            assertThat(campaignStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Первая принятая победа закрывает главу")
    class FirstVictory {

        @Test
        @DisplayName("после победы первого боя победа и поражение второго → 409 с причиной про первый; «Отклонить» принимается")
        void secondBattleRefused() throws Exception {
            // подготовка
            chapter(2);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            startMark(first, null, "OZEV", 2);
            startMark(second, null, "TORAMAT", 2);
            preview(first, report(null, "OZEV", 2, 0, "VICTORY", ""))
                    .andExpect(jsonPath("$.otherActiveBattles[0].id").value(second.toString()));
            accept(first, null, "OZEV", 2, 0, "VICTORY").andExpect(status().isOk());

            // вызов и проверка
            preview(second, report(null, "TORAMAT", 2, 0, "VICTORY", ""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CAMPAIGN_CHANGED"))
                    .andExpect(jsonPath("$.reasons[0]").value(containsString("Глава 2 уже завершена")))
                    .andExpect(jsonPath("$.reasons[0]").value(containsString("«Озев»")))
                    .andExpect(jsonPath("$.reasons[0]").value(containsString("alice")))
                    .andExpect(jsonPath("$.closedBy.submittedBy.name").value("alice"))
                    .andExpect(jsonPath("$.closedBy.boss.code").value("OZEV"));
            accept(second, null, "TORAMAT", 2, 0, "VICTORY").andExpect(status().isConflict());
            accept(second, null, "TORAMAT", 2, 0, "DEFEAT")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CAMPAIGN_CHANGED"));
            submit(second, report(null, "TORAMAT", 2, 0, "VICTORY", "\"action\": \"DISMISS\""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next").value("CAMPAIGN_SHEET"));
            assertThat(battleStatus(second)).isEqualTo("DISMISSED");
            assertThat(trophies()).containsExactly("OZEV");
            assertThat(progressSeq()).isEqualTo(1);
        }

        @Test
        @DisplayName("принятое поражение и отклонённая победа главу не закрывают — победа другого боя принимается")
        void defeatAndDismissKeepChapter() throws Exception {
            // подготовка
            chapter(2);
            quest(1, "OPEN");
            accept(UUID.randomUUID(), 1, "TORAMAT", 2, 0, "DEFEAT").andExpect(status().isOk());
            submit(UUID.randomUUID(), report(null, "OZEV", 2, 0, "VICTORY", "\"action\": \"DISMISS\""))
                    .andExpect(status().isOk());

            // вызов и проверка
            assertThat(progressSeq()).isZero();
            accept(UUID.randomUUID(), 1, "TORAMAT", 2, 0, "VICTORY")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.next").value("CHAPTER_TRANSITION"));
            assertThat(questStatus(4)).isEqualTo("OPEN");
            assertThat(questStatus(6)).as("поражение задание 6 не открывает (qa 135)").isNull();
            assertThat(trophies()).containsExactly("TORAMAT");
        }

        @Test
        @DisplayName("результат без отметки о начале и брошенного боя принимается; история показывает автора и итог")
        void withoutMarkAndAbandoned() throws Exception {
            // подготовка
            chapter(4);
            UUID abandoned = UUID.randomUUID();
            startMark(abandoned, null, "OZEV", 4);
            mockMvc.perform(delete(path(abandoned)).with(xsrf()).cookie(alice)).andExpect(status().isNoContent());
            UUID unmarked = UUID.randomUUID();

            // вызов
            accept(unmarked, null, "TORAMAT", 4, 0, "DEFEAT").andExpect(status().isOk());
            accept(abandoned, null, "OZEV", 4, 0, "VICTORY").andExpect(status().isOk());

            // проверка
            assertThat(battleStatus(unmarked)).isEqualTo("APPLIED");
            assertThat(battleStatus(abandoned)).isEqualTo("APPLIED");
            mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/battles").cookie(alice))
                    .andExpect(jsonPath("$[?(@.id == '%s')].startedBy.name".formatted(unmarked)).value("alice"))
                    .andExpect(jsonPath("$[?(@.id == '%s')].submittedBy.name".formatted(unmarked)).value("alice"))
                    .andExpect(jsonPath("$[?(@.id == '%s')].result".formatted(unmarked)).value("DEFEAT"))
                    .andExpect(jsonPath("$[?(@.id == '%s')].result".formatted(abandoned)).value("VICTORY"));
        }
    }

    @Nested
    @DisplayName("Несогласованный результат")
    class Inconsistent {

        @Test
        @DisplayName("задание уже выполнено, кампания ждёт перехода или пройдена → 409 с причинами")
        void reasons() throws Exception {
            // подготовка
            chapter(2);
            quest(2, "COMPLETED");

            // вызов и проверка
            preview(UUID.randomUUID(), report(2, "OZEV", 2, 0, "VICTORY", ""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.reasons[0]").value("Задание 2 больше не открыто: его выполнили или время истекло."));
            jdbc.update("update campaign set status = 'CHAPTER_TRANSITION' where id = ?", campaignId);
            preview(UUID.randomUUID(), report(null, "OZEV", 2, 0, "VICTORY", ""))
                    .andExpect(jsonPath("$.reasons[0]").value("Глава 2 уже завершена и ждёт перехода главы."));
            jdbc.update("update campaign set status = 'COMPLETED' where id = ?", campaignId);
            preview(UUID.randomUUID(), report(null, "OZEV", 2, 0, "VICTORY", ""))
                    .andExpect(jsonPath("$.reasons[0]").value("Кампания уже пройдена."));
        }

        @Test
        @DisplayName("в главе 11 не финальный босс → 409; правка главы вручную → 409")
        void wrongFinalBossAndManualChapter() throws Exception {
            // подготовка
            jdbc.update("update campaign set chapter = 11, final_boss_code = 'AWAKENED' where id = ?", campaignId);

            // вызов и проверка
            preview(UUID.randomUUID(), report(null, "TORAMAT", 11, 0, "VICTORY", ""))
                    .andExpect(jsonPath("$.reasons[0]").value("В главе 11 принимается только бой с боссом «Пробуждённый»."));
            jdbc.update("update campaign set progress_seq = 3 where id = ?", campaignId);
            preview(UUID.randomUUID(), report(null, "AWAKENED", 11, 2, "VICTORY", ""))
                    .andExpect(jsonPath("$.reasons[0]").value(
                            "Глава изменилась, пока шёл бой. Результаты боёв, начатых раньше, не принимаются."));
        }

        @Test
        @DisplayName("поражение без причины, итог без action, чужая кампания → отказ")
        void badRequests() throws Exception {
            // подготовка
            chapter(2);
            Cookie bob = auth.login("bob");

            // вызов и проверка
            preview(UUID.randomUUID(), report(null, "OZEV", 2, 0, "DEFEAT", "").replace("\"ROUNDS\"", "null"))
                    .andExpect(status().isBadRequest());
            submit(UUID.randomUUID(), report(null, "OZEV", 2, 0, "VICTORY", ""))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(path(UUID.randomUUID()) + "/result/preview").with(xsrf()).cookie(bob)
                            .contentType(MediaType.APPLICATION_JSON).content(report(null, "OZEV", 2, 0, "VICTORY", "")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Карты наград")
    class RewardCards {

        private List<Long> hunterIds() {
            return jdbc.queryForList("select id from campaign_hunter where campaign_id = ? order by position", Long.class, campaignId);
        }

        private List<String> rewardItems(long hunterId) {
            return jdbc.queryForList("select name from hunter_item where hunter_id = ? and kind = 'REWARD' order by id",
                    String.class, hunterId);
        }

        @Test
        @DisplayName("победа по заданию 5 — «Карта награды №1» выбранному охотнику")
        void toChosenHunter() throws Exception {
            // подготовка
            chapter(1);
            quest(5, "OPEN");
            List<Long> ids = hunterIds();

            // вызов
            submit(UUID.randomUUID(), report(5, "YUROM", 1, 0, "VICTORY",
                    "\"action\": \"ACCEPT\", \"rewardCardHolders\": [" + ids.get(1) + "]"))
                    .andExpect(status().isOk());

            // проверка
            assertThat(rewardItems(ids.get(1))).containsExactly("Карта награды №1");
            assertThat(rewardItems(ids.get(0))).isEmpty();
        }

        @Test
        @DisplayName("держатель не указан или чужой — карта первому охотнику отряда")
        void defaultHolder() throws Exception {
            // подготовка
            chapter(1);
            quest(5, "OPEN");
            List<Long> ids = hunterIds();

            // вызов
            accept(UUID.randomUUID(), 5, "YUROM", 1, 0, "VICTORY").andExpect(status().isOk());

            // проверка
            assertThat(rewardItems(ids.get(0))).containsExactly("Карта награды №1");
        }
    }
}
