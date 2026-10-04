package com.primal.campaign;

import com.primal.catalog.CatalogService;
import com.primal.identity.ExpansionSettings;
import com.primal.rules.effects.CampaignFacts;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.Plan;
import com.primal.rules.model.AchievementNames;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выполняет план наград над кампанией ({@code doc/data-model.md} §4.3). Повторы безопасны: открыть уже
 * открытое задание или выдать уже полученное достижение — ничего не меняет.
 */
@Service
public class PlanApplier {

    /** Что изменилось и что игрокам сделать самим (сообщения, карты наград, улучшение набора). */
    public record Applied(List<Integer> openedQuests, List<Integer> expiredQuests, List<String> grantedAchievements,
                          List<String> instructions) {
    }

    private final CampaignService campaigns;
    private final CampaignHunterRepository hunters;
    private final HunterResourceRepository resources;
    private final CampaignQuestRepository quests;
    private final CampaignAchievementRepository achievements;
    private final CatalogService catalog;
    private final Clock clock;
    private final ExpansionSettings expansions;

    PlanApplier(CampaignService campaigns, CampaignHunterRepository hunters, HunterResourceRepository resources,
                CampaignQuestRepository quests, CampaignAchievementRepository achievements, CatalogService catalog,
                Clock clock, ExpansionSettings expansions) {
        this.expansions = expansions;
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.resources = resources;
        this.quests = quests;
        this.achievements = achievements;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * Снимок кампании для планировщика: достижения, глава, добавленные задания (открытые и выполненные) и
     * дополнения владельца кампании — для условия «есть дополнение» (qa № 138).
     */
    @Transactional(readOnly = true)
    public CampaignFacts facts(long campaignId) {
        Campaign campaign = campaigns.campaign(campaignId);
        Set<String> codes = achievements.findByCampaignIdOrderById(campaignId).stream()
                .map(CampaignAchievement::getAchievementCode)
                .filter(code -> code != null)
                .collect(Collectors.toSet());
        Set<Integer> available = quests.findByKeyCampaignId(campaignId).stream()
                .filter(quest -> quest.getStatus() != CampaignQuest.Status.EXPIRED)
                .map(CampaignQuest::getQuestNumber)
                .collect(Collectors.toSet());
        return new CampaignFacts(codes, campaign.getChapter(), available, expansions.enabled(campaign.getOwnerId()));
    }

    /** Выполняет план в текущей транзакции; версия кампании растёт. */
    @Transactional
    public Applied apply(long campaignId, Plan plan, CampaignAchievement.Source source) {
        Campaign campaign = campaigns.campaign(campaignId);
        Instant now = clock.instant();
        List<Integer> opened = new ArrayList<>();
        List<Integer> expired = new ArrayList<>();
        List<String> granted = new ArrayList<>();
        List<String> instructions = new ArrayList<>();
        for (Effect action : plan.actions()) {
            switch (action) {
                case Effect.Resources given -> hunters.findByCampaignIdOrderByPosition(campaignId).forEach(hunter ->
                        given.items().forEach((code, quantity) -> {
                            if (quantity > 0) {
                                resources.add(hunter.getId(), code.name(), quantity);
                            }
                        }));
                case Effect.OpenQuest open -> {
                    if (openQuest(campaign, open.quest(), now)) {
                        opened.add(open.quest());
                    }
                }
                case Effect.ExpireQuests expire -> expire.quests().forEach(number -> {
                    if (expireQuest(campaign, number, now)) {
                        expired.add(number);
                    }
                });
                case Effect.ExpireAllQuests ignored -> quests.findByKeyCampaignId(campaignId).stream()
                        .filter(quest -> quest.getStatus() == CampaignQuest.Status.OPEN)
                        .forEach(quest -> {
                            quest.close(CampaignQuest.Status.EXPIRED, campaign.getChapter(), now);
                            expired.add(quest.getQuestNumber());
                        });
                case Effect.GrantAchievement grant -> {
                    if (grantAchievement(campaign, grant.achievement(), source, now)) {
                        granted.add(grant.achievement());
                    }
                }
                case Effect.ForgeLevelUp ignored -> campaign.forgeLevelUp();
                case Effect.LabLevelUp ignored -> campaign.labLevelUp();
                case Effect.FinalBattle battle -> campaign.setFinalBoss(battle.boss());
                case Effect.HunterKitUpgrade ignored -> instructions.add("Каждый охотник улучшает свой набор.");
                case Effect.RewardCards cards -> instructions.add("Карты наград: " + String.join(", ", cards.cards()) + ".");
                case Effect.Message message -> instructions.add(message.text());
                case Effect.Conditional ignored -> throw new IllegalArgumentException("В плане нет условий");
            }
        }
        campaigns.touch(campaign);
        return new Applied(opened, expired, granted, instructions);
    }

    /** Новое задание — открыто; истёкшее — открыто снова; открытое и выполненное не меняются (D-5). */
    private boolean openQuest(Campaign campaign, int number, Instant now) {
        CampaignQuest.Key key = new CampaignQuest.Key(campaign.getId(), (short) number);
        return quests.findById(key)
                .map(quest -> {
                    if (quest.getStatus() != CampaignQuest.Status.EXPIRED) {
                        return false;
                    }
                    quest.reopen(now);
                    return true;
                })
                .orElseGet(() -> {
                    quests.save(new CampaignQuest(campaign.getId(), number, campaign.getChapter(), now));
                    return true;
                });
    }

    /** Истекают только открытые задания. */
    private boolean expireQuest(Campaign campaign, int number, Instant now) {
        return quests.findById(new CampaignQuest.Key(campaign.getId(), (short) number))
                .filter(quest -> quest.getStatus() == CampaignQuest.Status.OPEN)
                .map(quest -> {
                    quest.close(CampaignQuest.Status.EXPIRED, campaign.getChapter(), now);
                    return true;
                })
                .orElse(false);
    }

    /** Достижение без дублей — ни по коду, ни в другом написании (D-9, 42.1). */
    private boolean grantAchievement(Campaign campaign, String code, CampaignAchievement.Source source, Instant now) {
        String name = catalog.achievementName(code);
        String normalized = AchievementNames.normalize(name);
        boolean present = achievements.findByCampaignIdOrderById(campaign.getId()).stream()
                .anyMatch(existing -> code.equals(existing.getAchievementCode())
                        || existing.getNormalizedName().equals(normalized));
        if (present) {
            return false;
        }
        achievements.save(new CampaignAchievement(campaign.getId(), code, name, normalized, source, campaign.getChapter(), now));
        return true;
    }
}
