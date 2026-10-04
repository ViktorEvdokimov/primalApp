package com.primal.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.primal.access.AccessService.CampaignAccess;
import com.primal.common.api.ApiNullable;
import com.primal.rules.model.Element;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.SkillBranch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ответы API кампаний ({@code doc/api.md} §5). */
public final class CampaignSheetDto {

    private CampaignSheetDto() {
    }

    /** Босс в листе кампании; у Пробуждённого стихии нет. */
    public record CampaignBoss(String code, String name, @ApiNullable String element) {
    }

    public record SkillStep(SkillBranch branch, int tier) {
    }

    public record HunterSheet(
            long id,
            @JsonProperty("class") HunterClass hunterClass,
            String playerName,
            int position,
            List<SkillStep> skills,
            List<SkillStep> unlockableSkills,
            /* нулевые количества не передаются */
            Map<String, Integer> resources,
            /* карты снаряжения, зелий и наград в порядке получения */
            List<InventoryItem> items) {
    }

    /**
     * Карта в инвентаре охотника.
     *
     * @param level   1–3; у карты награды — {@code null}
     * @param element стихия кузни снаряжения — её можно получить, сбросив карту
     * @param source  код каталога ({@code FIRE_01}, {@code LAB_02}) или номер карты награды; {@code null} — вручную
     */
    public record InventoryItem(long id, HunterItem.Kind kind, String name, @ApiNullable Integer level,
                                @ApiNullable Element element, @ApiNullable String source) {
    }

    /** {@code closedInChapter} — у выполненных и истёкших заданий. */
    /** {@code expansion} — дополнение задания (NIGHTMARE, FEATHER, POISON, ICE); {@code null} — базовая игра. */
    public record QuestItem(int number, String name, CampaignBoss boss, @ApiNullable String expansion,
                            @ApiNullable Integer closedInChapter) {
    }

    public record QuestLists(List<QuestItem> open, List<QuestItem> completed, List<QuestItem> expired) {
    }

    /** {@code code} — у достижений из каталога; своё достижение, введённое вручную, кода не имеет. */
    public record AchievementItem(long id, @ApiNullable String code, String name, CampaignAchievement.Source source,
                                  int grantedInChapter) {
    }

    /** Трофеи одного босса: в каких главах он побеждён. */
    public record TrophyItem(CampaignBoss boss, List<Integer> chapters) {
    }

    public enum ActorKind { USER, GUEST }

    public record Actor(ActorKind kind, String name) {
    }

    /** Идущий бой — баннер «Идёт бой» (задача 5.1). */
    public record ActiveBattle(UUID id, Actor startedBy, Instant startedAt, @ApiNullable QuestItem quest,
                               @ApiNullable CampaignBoss boss) {
    }

    /** Завершённый бой (задача 5.2). */
    public record RecentBattle(UUID id, String result, @ApiNullable Integer questNumber, @ApiNullable CampaignBoss boss,
                               int chapter, String status, Actor submittedBy, Instant submittedAt) {
    }

    /**
     * Лист кампании целиком ({@code doc/api.md} §5.2).
     *
     * @param openForges кузни открытых стихий — стихии побеждённых боссов (трофеи) в порядке справочника
     */
    public record CampaignSheet(
            long id,
            String name,
            int version,
            int chapter,
            Campaign.Status status,
            CampaignAccess access,
            String ownerName,
            int difficulty,
            int forgeLevel,
            List<Element> openForges,
            int labLevel,
            @ApiNullable CampaignBoss finalBoss,
            String notes,
            List<HunterSheet> hunters,
            QuestLists quests,
            List<AchievementItem> achievements,
            List<TrophyItem> trophies,
            List<ActiveBattle> activeBattles,
            List<RecentBattle> recentBattles,
            boolean pendingTransition) {
    }

    public record HunterSummary(@JsonProperty("class") HunterClass hunterClass, String playerName) {
    }

    /** Строка списка кампаний ({@code doc/api.md} §5.1). */
    public record CampaignSummary(
            long id,
            String name,
            int chapter,
            Campaign.Status status,
            CampaignAccess access,
            String ownerName,
            List<HunterSummary> hunters,
            boolean pendingTransition,
            Instant updatedAt) {
    }
}
