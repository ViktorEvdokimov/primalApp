package com.primal.progression;

import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.campaign.CampaignSheetDto.Actor;
import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.catalog.CatalogViews.AchievementRef;
import com.primal.common.api.ApiNullable;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.progression.CampaignBattle.Result;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Ответы API результата боя кампании ({@code doc/api.md} §7). */
public final class BattleResultDto {

    private BattleResultDto() {
    }

    /** Куда идти после итога: переход главы, лист кампании или «Кампания пройдена!». */
    public enum Next { CHAPTER_TRANSITION, CAMPAIGN_SHEET, CAMPAIGN_COMPLETED }

    public record QuestRef(int number, String name) {
    }

    /**
     * Награды итога; достижения — {@link AchievementRef} каталога.
     *
     * @param trophy     трофей победы; {@code null} — поражение или бой без задания без выбранного босса
     * @param perHunter  ресурсы каждому охотнику: 2 стихии босса и ресурсы задания
     * @param openQuests задания, которые добавятся (уже добавленные не повторяются)
     * @param messages   инструкции игрокам текстом
     */
    public record BattleRewards(
            @ApiNullable CampaignBoss trophy,
            Map<String, Integer> perHunter,
            List<AchievementRef> achievements,
            List<Integer> openQuests,
            List<String> rewardCards,
            List<String> messages) {
    }

    /** Условное правило наград: формулировка и результат для этой кампании. */
    public record RewardRule(String kind, String description, String result) {
    }

    /**
     * Превью наград: ничего не записывает. {@code otherActiveBattles} — идущие бои, чьи результаты не примут,
     * если принять эту победу; {@code dismissConsequences} — текст окна подтверждения «Отклонить».
     */
    public record ResultPreview(
            Result result,
            Purpose purpose,
            @ApiNullable QuestRef quest,
            BattleRewards rewards,
            List<RewardRule> rules,
            Next next,
            List<ActiveBattle> otherActiveBattles,
            List<String> dismissConsequences) {
    }

    /** Итог сохранён: куда идти и лист кампании после него. */
    public record ResultApplied(Next next, CampaignSheet campaign) {
    }

    /** Победа, закрывшая главу, — для причины {@code 409 CAMPAIGN_CHANGED}: время экран покажет по местному. */
    public record ClosedBy(Actor submittedBy, Instant submittedAt, @ApiNullable CampaignBoss boss) {
    }
}
