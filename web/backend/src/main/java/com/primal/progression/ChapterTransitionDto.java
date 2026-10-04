package com.primal.progression;

import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.catalog.CatalogViews.AchievementRef;
import com.primal.common.api.ApiNullable;
import com.primal.progression.BattleResultDto.RewardRule;
import java.util.List;
import java.util.Map;

/** Ответы API перехода главы ({@code doc/api.md} §8). */
public final class ChapterTransitionDto {

    private ChapterTransitionDto() {
    }

    public record TransitionOption(String code, String label) {
    }

    /** Решение главы; {@code selected} — вариант из запроса превью. */
    public record TransitionDecision(String code, String question, List<TransitionOption> options,
                                     @ApiNullable String selected) {
    }

    /**
     * Задание, у которого истечёт время; {@code wasOpen} — открыто ли оно сейчас (иначе ничего не изменится).
     * {@code consequences} — последствия невыполненного задания (правила, «Последствия невыполненных заданий»):
     * «добавить задание 6»; применяются вместе с переходом, только если задание было открыто.
     */
    public record ExpiringQuest(int number, String name, boolean wasOpen, List<String> consequences) {
    }

    /**
     * Превью перехода: что сделают эффекты выбранных вариантов решений и главы. {@code version} передаётся в
     * «Принять»/«Отклонить» как {@code expectedVersion}; {@code rejectConsequences} — текст окна «Отклонить».
     */
    public record TransitionPreview(
            int fromChapter,
            int toChapter,
            int version,
            List<TransitionDecision> decisions,
            boolean decisionsComplete,
            Map<String, Integer> perHunter,
            List<Integer> openQuests,
            List<ExpiringQuest> expireQuests,
            List<AchievementRef> achievements,
            boolean forgeLevelUp,
            boolean labLevelUp,
            boolean hunterKitUpgrade,
            List<String> rewardCards,
            List<String> messages,
            @ApiNullable CampaignBoss finalBattle,
            List<RewardRule> rules,
            List<String> rejectConsequences) {
    }
}
