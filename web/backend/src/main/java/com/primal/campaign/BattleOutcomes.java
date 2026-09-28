package com.primal.campaign;

import com.primal.rules.effects.Plan;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Принятый итог боя кампании ({@code doc/data-model.md} §4.3, общие правила): трофей, выполненное задание,
 * награды по плану и закрытие главы или кампании — в транзакции вызывающего.
 */
@Service
public class BattleOutcomes {

    /** Чем кончается принятый итог: ничем (поражение), закрытием главы или кампании (финальный бой). */
    public enum Ending { NONE, CHAPTER, CAMPAIGN }

    /**
     * @param trophyBoss     босс трофея; {@code null} — трофея нет
     * @param completedQuest задание, выполненное победой; {@code null} — бой не по заданию или поражение
     * @param plan           награды: ресурсы (со стихиями босса), задания, достижения
     */
    public record Outcome(UUID battleId, String trophyBoss, Integer completedQuest, Plan plan, Ending ending) {
    }

    private final CampaignService campaigns;
    private final CampaignTrophyRepository trophies;
    private final CampaignQuestRepository quests;
    private final PlanApplier applier;
    private final Clock clock;

    BattleOutcomes(CampaignService campaigns, CampaignTrophyRepository trophies, CampaignQuestRepository quests,
                   PlanApplier applier, Clock clock) {
        this.campaigns = campaigns;
        this.trophies = trophies;
        this.quests = quests;
        this.applier = applier;
        this.clock = clock;
    }

    @Transactional
    public PlanApplier.Applied apply(long campaignId, Outcome outcome) {
        Campaign campaign = campaigns.campaign(campaignId);
        Instant now = clock.instant();
        if (outcome.trophyBoss() != null) {
            trophies.save(new CampaignTrophy(campaignId, outcome.trophyBoss(), campaign.getChapter(), outcome.battleId(), now));
        }
        if (outcome.completedQuest() != null) {
            quests.findById(new CampaignQuest.Key(campaignId, outcome.completedQuest().shortValue()))
                    .filter(quest -> quest.getStatus() == CampaignQuest.Status.OPEN)
                    .ifPresent(quest -> quest.close(CampaignQuest.Status.COMPLETED, campaign.getChapter(), now));
        }
        switch (outcome.ending()) {
            case CHAPTER -> campaign.closeChapter();
            case CAMPAIGN -> campaign.finish();
            case NONE -> {
            }
        }
        quests.flush();
        return applier.apply(campaignId, outcome.plan(), CampaignAchievement.Source.QUEST);
    }
}
