package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignSheetDto.QuestLists;
import com.primal.catalog.CatalogService;
import com.primal.catalog.QuestDef;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.EffectDescriber.Context;
import com.primal.rules.effects.EffectPlanner;
import com.primal.rules.effects.Plan;
import com.primal.rules.effects.RuleExplanation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Задания кампании вручную: «Выполнено», «Отмена», редактор открытых заданий ({@code doc/api.md} §5.5). */
@Service
public class QuestService {

    /** Итог «Выполнено»: открытые задания и пояснения условных правил. */
    public record Completed(int number, List<Integer> opened, List<RuleExplanation> rules) {
    }

    private final CampaignService campaigns;
    private final CampaignQuestRepository quests;
    private final CampaignSheetService sheets;
    private final PlanApplier applier;
    private final CatalogService catalog;
    private final AccessService access;
    private final Clock clock;

    QuestService(CampaignService campaigns, CampaignQuestRepository quests, CampaignSheetService sheets,
                 PlanApplier applier, CatalogService catalog, AccessService access, Clock clock) {
        this.campaigns = campaigns;
        this.quests = quests;
        this.sheets = sheets;
        this.applier = applier;
        this.catalog = catalog;
        this.access = access;
        this.clock = clock;
    }

    /**
     * «Выполнено» вручную: задание выполнено; из наград победы открываются только задания (безусловные и по
     * условиям), материи, растения и достижения не начисляются (qa 70 app).
     */
    @Transactional
    public Completed complete(PrimalPrincipal principal, long campaignId, int number) {
        access.require(principal, campaignId);
        Campaign campaign = campaigns.campaign(campaignId);
        CampaignQuest quest = quest(campaignId, number)
                .filter(found -> found.getStatus() == CampaignQuest.Status.OPEN)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEST_NOT_OPEN, "Задание " + number + " не открыто."));
        List<Effect> victory = questDef(number).victory();
        Plan plan = catalog.planner().plan(victory, applier.facts(campaignId), Context.QUEST);
        quest.close(CampaignQuest.Status.COMPLETED, campaign.getChapter(), clock.instant());
        quests.flush();
        PlanApplier.Applied applied = applier.apply(campaignId, plan.only(Effect.OpenQuest.class), CampaignAchievement.Source.QUEST);
        return new Completed(number, applied.openedQuests(), questRules(victory, plan.explanations()));
    }

    /** «Отмена» у выполненного: снова открыто; открытые им задания и награды не меняются (qa 119 app). */
    @Transactional
    public QuestLists reopen(PrimalPrincipal principal, long campaignId, int number) {
        access.require(principal, campaignId);
        CampaignQuest quest = quest(campaignId, number)
                .filter(found -> found.getStatus() == CampaignQuest.Status.COMPLETED)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEST_NOT_COMPLETED,
                        "Отменить можно только выполненное задание."));
        quest.reopen(clock.instant());
        return finish(campaignId);
    }

    /**
     * Редактор: итоговый набор открытых заданий. Недостающие добавляются открытыми, открытые вне списка
     * удаляются; выполненные и истёкшие не меняются (D-5).
     */
    @Transactional
    public QuestLists setOpen(PrimalPrincipal principal, long campaignId, Set<Integer> numbers) {
        access.require(principal, campaignId);
        List<Integer> unknown = numbers.stream().filter(n -> catalog.quest(n).isEmpty()).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Нет таких заданий: " + unknown + ".");
        }
        Campaign campaign = campaigns.campaign(campaignId);
        Instant now = clock.instant();
        List<CampaignQuest> existing = quests.findByKeyCampaignId(campaignId);
        existing.stream()
                .filter(q -> q.getStatus() == CampaignQuest.Status.OPEN && !numbers.contains(q.getQuestNumber()))
                .forEach(quests::delete);
        Set<Integer> present = new HashSet<>(existing.stream().map(CampaignQuest::getQuestNumber).toList());
        numbers.stream().filter(n -> !present.contains(n)).sorted()
                .forEach(n -> quests.save(new CampaignQuest(campaignId, n, campaign.getChapter(), now)));
        return finish(campaignId);
    }

    private QuestLists finish(long campaignId) {
        quests.flush();
        campaigns.touch(campaigns.campaign(campaignId));
        return sheets.questLists(campaignId);
    }

    /** Пояснения только тех правил, что открывают задания: они по порядку совпадают с условиями верхнего уровня. */
    private static List<RuleExplanation> questRules(List<Effect> effects, List<RuleExplanation> explanations) {
        List<RuleExplanation> result = new ArrayList<>();
        int index = 0;
        for (Effect effect : effects) {
            if (effect instanceof Effect.Conditional conditional) {
                if (EffectPlanner.opensQuests(conditional)) {
                    result.add(explanations.get(index));
                }
                index++;
            }
        }
        return result;
    }

    private Optional<CampaignQuest> quest(long campaignId, int number) {
        return quests.findById(new CampaignQuest.Key(campaignId, (short) number));
    }

    private QuestDef questDef(int number) {
        return catalog.quest(number).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Задания " + number + " нет."));
    }
}
