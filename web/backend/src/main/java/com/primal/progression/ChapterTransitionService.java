package com.primal.progression;

import com.primal.access.AccessService;
import com.primal.access.AccessService.CampaignAccess;
import com.primal.campaign.CampaignService;
import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.campaign.CampaignViews;
import com.primal.campaign.ChapterChanges;
import com.primal.campaign.ChapterChanges.PendingChapter;
import com.primal.campaign.PlanApplier;
import com.primal.catalog.CatalogService;
import com.primal.catalog.ChapterDef;
import com.primal.catalog.QuestDef;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.catalog.CatalogViews.AchievementRef;
import com.primal.progression.BattleResultDto.RewardRule;
import com.primal.progression.ChapterTransitionDto.TransitionDecision;
import com.primal.progression.ChapterTransitionDto.ExpiringQuest;
import com.primal.progression.ChapterTransitionDto.TransitionOption;
import com.primal.progression.ChapterTransitionDto.TransitionPreview;
import com.primal.rules.effects.CampaignFacts;
import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.EffectDescriber.Context;
import com.primal.rules.effects.Plan;
import com.primal.rules.effects.RuleExplanation;
import com.primal.rules.model.ResourceCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переход главы ({@code doc/api.md} §8): превью с учётом ответов на решения, «Принять» и «Отклонить». Превью и
 * применение строят план одной функцией, поэтому совпадают.
 */
@Service
public class ChapterTransitionService {

    public enum Action { ACCEPT, REJECT }

    /**
     * План перехода: эффекты выбранных вариантов решений, эффекты главы и последствия истекающих открытых заданий,
     * по одному снимку кампании.
     */
    record Computation(int fromChapter, ChapterDef chapter, Map<String, String> selected, boolean complete,
                       List<Effect> decisionEffects, Plan decisionPlan, Plan chapterPlan, CampaignFacts facts,
                       Set<Integer> openNow, Map<Integer, Plan> expiries) {

        int toChapter() {
            return chapter.chapter();
        }

        /** Последствия всех истекающих заданий одним планом — применяются после главы. */
        Plan expiryPlan() {
            List<Effect> actions = new ArrayList<>();
            List<RuleExplanation> explanations = new ArrayList<>();
            expiries.values().forEach(plan -> {
                actions.addAll(plan.actions());
                explanations.addAll(plan.explanations());
            });
            return new Plan(actions, explanations);
        }
    }

    /** Что изменит переход — для превью и текста «Отклонить». */
    record Changes(Map<String, Integer> perHunter, List<Integer> openQuests, List<ExpiringQuest> expireQuests,
                   List<AchievementRef> decisionAchievements, List<AchievementRef> chapterAchievements,
                   boolean forgeLevelUp, boolean labLevelUp, boolean hunterKitUpgrade, List<String> rewardCards,
                   List<String> messages, String finalBattle) {
    }

    private final ChapterChanges chapters;
    private final CampaignService campaigns;
    private final PlanApplier applier;
    private final CampaignViews views;
    private final CatalogService catalog;
    private final ConsequencesWriter consequences;
    private final AccessService access;

    ChapterTransitionService(ChapterChanges chapters, CampaignService campaigns, PlanApplier applier, CampaignViews views,
                             CatalogService catalog, ConsequencesWriter consequences, AccessService access) {
        this.chapters = chapters;
        this.campaigns = campaigns;
        this.applier = applier;
        this.views = views;
        this.catalog = catalog;
        this.consequences = consequences;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public TransitionPreview preview(PrimalPrincipal principal, long campaignId, Map<String, String> decisions) {
        access.require(principal, campaignId);
        PendingChapter pending = chapters.pending(campaignId);
        Computation computation = compute(pending, decisions);
        Changes changes = changes(computation);
        List<RewardRule> rules = new ArrayList<>(RuleViews.of(computation.decisionEffects(),
                computation.decisionPlan().explanations()));
        rules.addAll(RuleViews.of(computation.chapter().effects(), computation.chapterPlan().explanations()));
        return new TransitionPreview(
                computation.fromChapter(),
                computation.toChapter(),
                pending.version(),
                computation.chapter().decisions().stream()
                        .map(decision -> new TransitionDecision(decision.code(), decision.question(),
                                decision.options().stream().map(o -> new TransitionOption(o.code(), o.label())).toList(),
                                computation.selected().get(decision.code())))
                        .toList(),
                computation.complete(),
                changes.perHunter(),
                changes.openQuests(),
                changes.expireQuests(),
                concat(changes.decisionAchievements(), changes.chapterAchievements()),
                changes.forgeLevelUp(),
                changes.labLevelUp(),
                changes.hunterKitUpgrade(),
                changes.rewardCards(),
                changes.messages(),
                changes.finalBattle() == null ? null : views.boss(changes.finalBattle()),
                rules,
                consequences.transition(computation.fromChapter(), computation.toChapter(), changes));
    }

    /**
     * «Принять» или «Отклонить» подтверждённого перехода: {@code expectedVersion} — версия из превью, иначе
     * {@code 409 VERSION_CONFLICT} (список последствий мог измениться). Без ответа на решение — {@code 422}.
     */
    @Transactional
    public CampaignSheet submit(PrimalPrincipal principal, long campaignId, Action action, Map<String, String> decisions,
                                int expectedVersion) {
        CampaignAccess campaignAccess = access.require(principal, campaignId);
        PendingChapter pending = chapters.lock(campaignId, expectedVersion, campaignAccess);
        if (action == Action.REJECT) {
            chapters.reject(campaignId);
            return campaigns.sheet(principal, campaignId);
        }
        Computation computation = compute(pending, decisions);
        if (!computation.complete()) {
            String questions = computation.chapter().decisions().stream()
                    .filter(decision -> !computation.selected().containsKey(decision.code()))
                    .map(Decision::question)
                    .collect(Collectors.joining(" "));
            throw new ApiException(ErrorCode.DECISION_REQUIRED, "Ответьте на решение главы: " + questions);
        }
        chapters.accept(campaignId, computation.toChapter(), computation.decisionPlan(), computation.chapterPlan(),
                computation.expiryPlan());
        return campaigns.sheet(principal, campaignId);
    }

    private Computation compute(PendingChapter pending, Map<String, String> decisions) {
        int to = pending.chapter() + 1;
        ChapterDef def = catalog.chapter(to)
                .orElseThrow(() -> new ApiException(ErrorCode.CAMPAIGN_CHANGED, "Главы " + to + " нет: кампания пройдена."));
        Map<String, String> selected = new LinkedHashMap<>();
        List<Effect> decisionEffects = new ArrayList<>();
        decisions.forEach((code, option) -> {
            Decision decision = def.decisions().stream().filter(d -> d.code().equals(code)).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "В главе " + to + " нет решения " + code + "."));
            Decision.Option chosen = decision.options().stream().filter(o -> o.code().equals(option)).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "У решения " + code + " нет варианта " + option + "."));
            selected.put(code, option);
            decisionEffects.addAll(chosen.effects());
        });
        boolean complete = def.decisions().stream().allMatch(decision -> selected.containsKey(decision.code()));
        CampaignFacts facts = applier.facts(pending.campaignId());
        Set<Integer> openNow = campaigns.battleContext(pending.campaignId()).openQuests().stream()
                .map(QuestItem::number)
                .collect(Collectors.toSet());
        Plan decisionPlan = catalog.planner().plan(decisionEffects, facts, Context.CHAPTER);
        Plan chapterPlan = catalog.planner().plan(def.effects(), facts, Context.CHAPTER);
        return new Computation(pending.chapter(), def, selected, complete, decisionEffects, decisionPlan, chapterPlan,
                facts, openNow, expiries(decisionPlan, chapterPlan, openNow, facts));
    }

    /**
     * Последствия невыполненных заданий (правила, «Последствия невыполненных заданий»): для каждого открытого
     * задания, у которого истекает время, — его эффекты {@code expired} по снимку кампании до перехода. Задание,
     * истекающее в этом же переходе, последствие снова не добавляет.
     */
    private Map<Integer, Plan> expiries(Plan decisionPlan, Plan chapterPlan, Set<Integer> openNow, CampaignFacts facts) {
        Set<Integer> expiring = new LinkedHashSet<>();
        List<Effect> actions = new ArrayList<>(decisionPlan.actions());
        actions.addAll(chapterPlan.actions());
        for (Effect action : actions) {
            if (action instanceof Effect.ExpireQuests expire) {
                expire.quests().stream().filter(openNow::contains).forEach(expiring::add);
            } else if (action instanceof Effect.ExpireAllQuests) {
                openNow.stream().sorted().forEach(expiring::add);
            }
        }
        Map<Integer, Plan> result = new LinkedHashMap<>();
        for (int number : expiring) {
            catalog.quest(number).ifPresent(quest -> {
                Plan plan = catalog.planner().plan(quest.expired(), facts, Context.QUEST);
                List<Effect> kept = plan.actions().stream()
                        .filter(effect -> !(effect instanceof Effect.OpenQuest open && expiring.contains(open.quest())))
                        .toList();
                result.put(number, new Plan(kept, plan.explanations()));
            });
        }
        return result;
    }

    /** «добавить задание 6», «добавить достижение «Оледенение»»: уже добавленное и полученное не повторяется. */
    private List<String> consequences(Plan plan, CampaignFacts facts) {
        return plan.actions().stream()
                .filter(effect -> !(effect instanceof Effect.OpenQuest open && facts.availableQuests().contains(open.quest())))
                .filter(effect -> !(effect instanceof Effect.GrantAchievement grant
                        && facts.achievements().contains(grant.achievement())))
                .map(catalog.describer()::action)
                .toList();
    }

    private ExpiringQuest expiring(Computation computation, int number, boolean wasOpen) {
        String name = catalog.quest(number).map(QuestDef::name).orElse("");
        Plan plan = computation.expiries().get(number);
        return new ExpiringQuest(number, name, wasOpen,
                wasOpen && plan != null ? consequences(plan, computation.facts()) : List.of());
    }

    /** Действия обоих планов по порядку применения: сначала решения, затем глава. */
    private Changes changes(Computation computation) {
        Map<String, Integer> perHunter = new LinkedHashMap<>();
        List<Integer> openQuests = new ArrayList<>();
        List<ExpiringQuest> expireQuests = new ArrayList<>();
        List<AchievementRef> decisionAchievements = new ArrayList<>();
        List<AchievementRef> chapterAchievements = new ArrayList<>();
        List<String> rewardCards = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        boolean forge = false;
        boolean lab = false;
        boolean kit = false;
        String finalBattle = null;
        CampaignFacts facts = computation.facts();
        List<Effect> all = new ArrayList<>(computation.decisionPlan().actions());
        int decisionCount = all.size();
        all.addAll(computation.chapterPlan().actions());
        for (int i = 0; i < all.size(); i++) {
            Effect action = all.get(i);
            switch (action) {
                case Effect.Resources resources -> resources.items().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(ResourceCode::ordinal)))
                        .forEach(entry -> perHunter.merge(entry.getKey().name(), entry.getValue(), Integer::sum));
                case Effect.OpenQuest open -> {
                    if (!facts.availableQuests().contains(open.quest()) && !openQuests.contains(open.quest())) {
                        openQuests.add(open.quest());
                    }
                }
                case Effect.ExpireQuests expire -> expire.quests().forEach(number ->
                        expireQuests.add(expiring(computation, number, computation.openNow().contains(number))));
                case Effect.ExpireAllQuests ignored -> computation.openNow().stream().sorted()
                        .forEach(number -> expireQuests.add(expiring(computation, number, true)));
                case Effect.GrantAchievement grant -> {
                    List<AchievementRef> target = i < decisionCount ? decisionAchievements : chapterAchievements;
                    if (!facts.achievements().contains(grant.achievement())) {
                        target.add(new AchievementRef(grant.achievement(), catalog.achievementName(grant.achievement())));
                    }
                }
                case Effect.ForgeLevelUp ignored -> forge = true;
                case Effect.LabLevelUp ignored -> lab = true;
                case Effect.HunterKitUpgrade ignored -> kit = true;
                case Effect.RewardCards cards -> rewardCards.addAll(cards.cards());
                case Effect.Message message -> messages.add(message.text());
                case Effect.FinalBattle battle -> finalBattle = battle.boss();
                case Effect.Conditional ignored -> throw new IllegalStateException("В плане нет условий");
            }
        }
        return new Changes(perHunter, openQuests, expireQuests, decisionAchievements, chapterAchievements, forge, lab, kit,
                rewardCards, messages, finalBattle);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }
}
