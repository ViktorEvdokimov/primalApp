package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.Plan;
import com.primal.rules.model.ResourceCode;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Применение плана наград")
class PlanApplierIT extends IntegrationTest {

    @Autowired
    private PlanApplier applier;

    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        Cookie alice = auth.login("alice@example.com");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
        jdbc.update("update campaign set chapter = 3 where id = ?", campaignId);
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private PlanApplier.Applied apply(Effect... actions) {
        return applier.apply(campaignId, new Plan(List.of(actions), List.of()), CampaignAchievement.Source.CHAPTER);
    }

    private String questStatus(int number) {
        List<String> statuses = jdbc.queryForList("select status || ':' || opened_in_chapter || ':' || coalesce(closed_in_chapter, -1)"
                + " from campaign_quest where campaign_id = ? and quest_number = ?", String.class, campaignId, number);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private void quest(int number, String status) {
        jdbc.update("insert into campaign_quest (campaign_id, quest_number, status, opened_in_chapter) values (?, ?, ?, 1)",
                campaignId, number, status);
    }

    @Test
    @DisplayName("ресурсы начисляются каждому охотнику")
    void resourcesToEveryHunter() {
        // вызов
        apply(new Effect.Resources(Map.of(ResourceCode.BONES, 2, ResourceCode.MELLIS, 1), null));
        apply(new Effect.Resources(Map.of(ResourceCode.BONES, 1), null));

        // проверка
        assertThat(jdbc.queryForList("select hunter_id || ':' || resource || ':' || quantity from hunter_resource"
                + " order by hunter_id, resource", String.class))
                .hasSize(4)
                .allMatch(row -> row.endsWith(":BONES:3") || row.endsWith(":MELLIS:1"));
    }

    @Test
    @DisplayName("открытие задания: новое и истёкшее — открыто, выполненное не переоткрывается (D-5)")
    void openQuest() {
        // подготовка
        quest(5, "EXPIRED");
        quest(6, "COMPLETED");
        quest(7, "OPEN");

        // вызов
        PlanApplier.Applied applied = apply(new Effect.OpenQuest(4, null), new Effect.OpenQuest(5, null),
                new Effect.OpenQuest(6, null), new Effect.OpenQuest(7, null));

        // проверка
        assertThat(applied.openedQuests()).containsExactly(4, 5);
        assertThat(questStatus(4)).isEqualTo("OPEN:3:-1");
        assertThat(questStatus(5)).startsWith("OPEN:").endsWith(":-1");
        assertThat(questStatus(6)).startsWith("COMPLETED");
        assertThat(questStatus(7)).startsWith("OPEN");
    }

    @Test
    @DisplayName("истекают только открытые задания; «истечение всех» тоже не трогает выполненные")
    void expireOnlyOpen() {
        // подготовка
        quest(1, "OPEN");
        quest(3, "COMPLETED");
        quest(9, "OPEN");

        // вызов
        PlanApplier.Applied applied = apply(new Effect.ExpireQuests(List.of(1, 3, 4), null));

        // проверка
        assertThat(applied.expiredQuests()).containsExactly(1);
        assertThat(questStatus(1)).isEqualTo("EXPIRED:1:3");
        assertThat(questStatus(3)).startsWith("COMPLETED");
        assertThat(apply(new Effect.ExpireAllQuests(null)).expiredQuests()).containsExactly(9);
        assertThat(questStatus(3)).startsWith("COMPLETED");
    }

    @Test
    @DisplayName("достижение выдаётся один раз, в том числе при другом написании")
    void achievementWithoutDuplicates() {
        // подготовка: вручную записано «голос  волтьяра»
        jdbc.update("""
                insert into campaign_achievement (campaign_id, name, normalized_name, source, granted_in_chapter)
                values (?, 'голос  волтьяра', 'голос волтьяра', 'MANUAL', 2)""", campaignId);

        // вызов
        PlanApplier.Applied applied = apply(new Effect.GrantAchievement("GOLOS_VOLTYARA", null),
                new Effect.GrantAchievement("ZATISHE", null), new Effect.GrantAchievement("ZATISHE", null));

        // проверка
        assertThat(applied.grantedAchievements()).containsExactly("ZATISHE");
        assertThat(jdbc.queryForList("select name || ':' || source || ':' || granted_in_chapter from campaign_achievement"
                + " where campaign_id = ? order by id", String.class, campaignId))
                .containsExactly("голос  волтьяра:MANUAL:2", "Затишье:CHAPTER:3");
    }

    @Test
    @DisplayName("кузня и лаборатория не выше 3; финальный бой задаёт босса")
    void forgeLabAndFinalBoss() {
        // вызов
        apply(new Effect.ForgeLevelUp(null), new Effect.ForgeLevelUp(null), new Effect.ForgeLevelUp(null),
                new Effect.LabLevelUp(null), new Effect.FinalBattle("AWAKENED", null));

        // проверка
        Map<String, Object> row = jdbc.queryForMap("select forge_level, lab_level, final_boss_code from campaign where id = ?", campaignId);
        assertThat(((Number) row.get("forge_level")).intValue()).isEqualTo(3);
        assertThat(((Number) row.get("lab_level")).intValue()).isEqualTo(2);
        assertThat(row).containsEntry("final_boss_code", "AWAKENED");
    }

    @Test
    @DisplayName("инструкции для игроков возвращаются, версия кампании растёт")
    void instructionsAndVersion() {
        // подготовка
        Integer before = jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId);

        // вызов
        PlanApplier.Applied applied = apply(new Effect.HunterKitUpgrade(null), new Effect.RewardCards(List.of("10", "11"), null),
                new Effect.Message("Получите награду 25", null));

        // проверка
        assertThat(applied.instructions()).containsExactly(
                "Каждый охотник улучшает свой набор.", "Карты наград: 10, 11.", "Получите награду 25");
        assertThat(jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId))
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("снимок для планировщика: достижения по коду, глава, открытые и выполненные задания")
    void facts() {
        // подготовка
        quest(1, "COMPLETED");
        quest(4, "OPEN");
        quest(5, "EXPIRED");
        apply(new Effect.GrantAchievement("ZATISHE", null));

        // вызов
        var facts = applier.facts(campaignId);

        // проверка
        assertThat(facts.chapter()).isEqualTo(3);
        assertThat(facts.achievements()).containsExactly("ZATISHE");
        assertThat(facts.availableQuests()).containsExactlyInAnyOrder(1, 4);
    }
}
