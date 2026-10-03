package com.primal.progression;

import com.primal.access.AccessService;
import com.primal.campaign.BattleOutcomes;
import com.primal.campaign.BattleOutcomes.Ending;
import com.primal.campaign.BattleOutcomes.Outcome;
import com.primal.campaign.Campaign;
import com.primal.campaign.CampaignService;
import com.primal.campaign.InventoryService;
import com.primal.campaign.CampaignService.BattleContext;
import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.campaign.CampaignViews;
import com.primal.campaign.PlanApplier;
import com.primal.catalog.BossDef;
import com.primal.catalog.CatalogService;
import com.primal.catalog.QuestDef;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.catalog.CatalogViews.AchievementRef;
import com.primal.progression.BattleResultDto.ClosedBy;
import com.primal.progression.BattleResultDto.Next;
import com.primal.progression.BattleResultDto.QuestRef;
import com.primal.progression.BattleResultDto.ResultApplied;
import com.primal.progression.BattleResultDto.ResultPreview;
import com.primal.progression.BattleResultDto.BattleRewards;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.realtime.ChangeEvents;
import com.primal.rules.effects.CampaignFacts;
import com.primal.rules.effects.EffectDescriber.Context;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.Plan;
import com.primal.rules.model.ResourceCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Результат боя кампании ({@code doc/api.md} §7): превью наград и итог — «Принять» или «Отклонить». Итог
 * сохраняется один раз; первая принятая победа закрывает главу, и результаты остальных боёв этой главы больше
 * не принимаются. Сервер не проверяет, честно ли прошёл бой, — только согласованность с кампанией.
 */
@Service
public class BattleResultService {

    public enum Action { ACCEPT, DISMISS }

    /**
     * Награды, исправленные через «Редактировать» ({@code PostVictoryDialog} app): применяются ровно они, правила
     * не вычисляются, стихии босса не добавляются сами.
     */
    public record Overrides(String bossCode, Map<ResourceCode, Integer> perHunter, List<Integer> openQuests,
                            List<String> achievements) {
    }

    /** Расчёт итога — одна функция для превью и применения; {@code rules} — эффекты задания для пояснений. */
    private record Computation(QuestDef quest, String trophyBoss, Integer completedQuest, Plan plan, Ending ending,
                               CampaignFacts facts, List<Effect> rules) {
    }

    private static final String KIT_UPGRADE = "Каждый охотник улучшает свой набор.";

    private final CampaignBattleRepository battles;
    private final CampaignBattleQueries queries;
    private final CampaignService campaigns;
    private final BattleOutcomes outcomes;
    private final PlanApplier applier;
    private final CampaignViews views;
    private final CatalogService catalog;
    private final ConsequencesWriter consequences;
    private final AccessService access;
    private final JsonMapper json;
    private final ChangeEvents changes;
    private final InventoryService inventory;
    private final Clock clock;

    BattleResultService(CampaignBattleRepository battles, CampaignBattleQueries queries, CampaignService campaigns,
                        BattleOutcomes outcomes, PlanApplier applier, CampaignViews views, CatalogService catalog,
                        ConsequencesWriter consequences, AccessService access, JsonMapper json, ChangeEvents changes, InventoryService inventory,
                        Clock clock) {
        this.battles = battles;
        this.queries = queries;
        this.campaigns = campaigns;
        this.outcomes = outcomes;
        this.applier = applier;
        this.views = views;
        this.catalog = catalog;
        this.consequences = consequences;
        this.access = access;
        this.json = json;
        this.changes = changes;
        this.inventory = inventory;
        this.clock = clock;
    }

    /** Превью наград: ничего не записывает. Несогласованный с кампанией результат — {@code 409 CAMPAIGN_CHANGED}. */
    @Transactional(readOnly = true)
    public ResultPreview preview(PrimalPrincipal principal, long campaignId, UUID battleId, BattleReport report) {
        access.require(principal, campaignId);
        BattleContext campaign = campaigns.battleContext(campaignId);
        if (battle(battleId, campaignId).filter(CampaignBattle::isFinished).isPresent()) {
            throw new ApiException(ErrorCode.CAMPAIGN_CHANGED, "Итог этого боя уже сохранён.")
                    .with("reasons", List.of("Итог этого боя уже сохранён."));
        }
        requireConsistent(campaign, report);
        Computation computation = compute(campaign, report, null);
        BattleRewards rewards = rewards(computation);
        List<ActiveBattle> others = queries.active(campaignId, campaign.progressSeq()).stream()
                .filter(active -> !active.id().equals(battleId))
                .toList();
        return new ResultPreview(
                report.result(),
                report.purpose(),
                computation.quest() == null ? null : new QuestRef(computation.quest().number(), computation.quest().name()),
                rewards,
                RuleViews.of(computation.rules(), computation.plan().explanations()),
                next(report),
                others,
                consequences.write(computation.completedQuest(), rewards, computation.ending(), report.chapter()));
    }

    /**
     * Итог боя. Повтор для уже сохранённого итога возвращает его и ничего не применяет. «Отклонить» принимается
     * всегда и только записывает бой в историю; «Принять» проверяет согласованность и применяет награды в одной
     * транзакции с блокировкой кампании.
     */
    @Transactional
    public ResultApplied submit(PrimalPrincipal principal, long campaignId, UUID battleId, BattleReport report,
                                Action action, Overrides overrides, List<Long> rewardCardHolders) {
        access.require(principal, campaignId);
        BattleContext campaign = campaigns.lockForBattle(campaignId);
        Optional<CampaignBattle> existing = battle(battleId, campaignId);
        if (existing.isPresent() && existing.get().isFinished()) {
            return new ResultApplied(savedNext(existing.get()), campaigns.sheet(principal, campaignId));
        }
        CampaignBattle battle = existing.orElseGet(() ->
                battles.save(new CampaignBattle(battleId, campaignId, report, principal.deviceId())));
        Instant now = clock.instant();

        if (action == Action.DISMISS) {
            battle.finish(CampaignBattle.Status.DISMISSED, report, principal.deviceId(), now, null);
            battles.flush();
            changes.battlesChanged(campaignId); // история боёв и баннер «Идёт бой» у других участников
            return new ResultApplied(Next.CAMPAIGN_SHEET, campaigns.sheet(principal, campaignId));
        }

        requireConsistent(campaign, report);
        if (overrides != null) {
            validate(overrides);
        }
        Computation computation = compute(campaign, report, overrides);
        if (report.victory() && computation.trophyBoss() == null) {
            throw new ApiException(ErrorCode.BOSS_REQUIRED, "Босс не выбран — укажите его через «Редактировать».");
        }
        battle.finish(CampaignBattle.Status.APPLIED, report, principal.deviceId(), now,
                overrides == null ? null : json.writeValueAsString(overrides));
        battles.flush(); // трофей ссылается на запись боя
        changes.battlesChanged(campaignId);
        outcomes.apply(campaignId, new Outcome(battleId, computation.trophyBoss(), computation.completedQuest(),
                computation.plan(), computation.ending()));
        // Карты наград — из правил задания и при «Редактировать»: это карты из коробки, а не начисления
        Computation byRules = overrides == null ? computation : compute(campaign, report, null);
        List<String> cards = rewardCards(byRules);
        if (!cards.isEmpty()) {
            inventory.giveRewardCards(campaignId, cards, rewardCardHolders);
        }
        return new ResultApplied(next(report), campaigns.sheet(principal, campaignId));
    }

    /**
     * Согласованность с кампанией ({@code api.md} §7.3): глава не закрыта другой победой и не изменена, кампания
     * не ждёт перехода и не пройдена, задание открыто, в главе 11 — финальный босс.
     */
    private void requireConsistent(BattleContext campaign, BattleReport report) {
        List<String> reasons = new ArrayList<>();
        ClosedBy closedBy = null;
        if (campaign.status() == Campaign.Status.COMPLETED) {
            reasons.add("Кампания уже пройдена.");
        } else if (report.progressSeq() != campaign.progressSeq()) {
            Optional<CampaignBattle> closing = battles
                    .findFirstByCampaignIdAndProgressSeqAndStatusAndResultOrderBySubmittedAtDesc(campaign.campaignId(),
                            report.progressSeq(), CampaignBattle.Status.APPLIED, CampaignBattle.Result.VICTORY);
            if (closing.isPresent()) {
                CampaignBattle victory = closing.get();
                closedBy = new ClosedBy(queries.actor(victory.getSubmittedByDeviceId()), victory.getSubmittedAt(),
                        queries.boss(victory));
                reasons.add(chapterClosed(report.chapter()) + ": принята победа"
                        + (closedBy.boss() == null ? "" : " в бою с боссом «" + closedBy.boss().name() + "»")
                        + " (результат от: " + closedBy.submittedBy().name() + ")."
                        + " Результаты боёв, начатых раньше, не принимаются.");
            } else {
                reasons.add("Глава изменилась, пока шёл бой. Результаты боёв, начатых раньше, не принимаются.");
            }
        } else if (campaign.status() == Campaign.Status.CHAPTER_TRANSITION) {
            reasons.add(chapterClosed(campaign.chapter()) + " и ждёт перехода главы.");
        }
        if (report.purpose() == Purpose.QUEST
                && campaign.openQuests().stream().noneMatch(quest -> quest.number() == report.questNumber())) {
            reasons.add("Задание " + report.questNumber() + " больше не открыто: его выполнили или время истекло.");
        }
        if (report.purpose() == Purpose.FINAL && campaign.finalBossCode() != null
                && !campaign.finalBossCode().equals(report.bossCode())) {
            reasons.add("В главе 11 принимается только бой с боссом «" + catalog.bossName(campaign.finalBossCode()) + "».");
        }
        if (!reasons.isEmpty()) {
            ApiException changed = new ApiException(ErrorCode.CAMPAIGN_CHANGED, "Пока шёл бой, кампания изменилась.")
                    .with("reasons", reasons);
            throw closedBy == null ? changed : changed.with("closedBy", closedBy);
        }
    }

    /**
     * Общие правила ({@code data-model.md} §4.3): победа — трофей и 2 стихии босса каждому охотнику, по заданию —
     * задание выполнено и его награды за победу; поражение по заданию — награды за поражение.
     */
    private Computation compute(BattleContext campaign, BattleReport report, Overrides overrides) {
        boolean victory = report.victory();
        QuestDef quest = report.purpose() == Purpose.QUEST
                ? catalog.quest(report.questNumber()).orElseThrow(() ->
                        new ApiException(ErrorCode.VALIDATION_FAILED, "Нет задания " + report.questNumber() + "."))
                : null;
        CampaignFacts facts = applier.facts(campaign.campaignId());
        Ending ending = !victory ? Ending.NONE : report.purpose() == Purpose.FINAL ? Ending.CAMPAIGN : Ending.CHAPTER;
        Integer completed = victory && quest != null ? quest.number() : null;

        if (overrides != null) {
            List<Effect> actions = new ArrayList<>();
            Map<ResourceCode, Integer> given = new LinkedHashMap<>(overrides.perHunter());
            given.values().removeIf(quantity -> quantity == 0);
            if (!given.isEmpty()) {
                actions.add(new Effect.Resources(given, null));
            }
            overrides.openQuests().forEach(number -> actions.add(new Effect.OpenQuest(number, null)));
            overrides.achievements().forEach(code -> actions.add(new Effect.GrantAchievement(code, null)));
            return new Computation(quest, victory ? overrides.bossCode() : null, completed, new Plan(actions, List.of()),
                    ending, facts, List.of());
        }

        String boss = !victory ? null : switch (report.purpose()) {
            case PROLOGUE -> BattleSetupService.PROLOGUE_BOSS;
            case FINAL -> campaign.finalBossCode() != null ? campaign.finalBossCode() : report.bossCode();
            case QUEST -> quest.bossCode();
            case FREE -> report.bossCode();
        };
        List<Effect> effects = quest == null ? List.of() : victory ? quest.victory() : quest.defeat();
        Plan planned = catalog.planner().plan(effects, facts, Context.QUEST);
        List<Effect> actions = new ArrayList<>();
        if (boss != null) {
            BossDef def = catalog.boss(boss).orElseThrow(() ->
                    new ApiException(ErrorCode.VALIDATION_FAILED, "Неизвестный босс: " + boss + "."));
            if (def.element() != null) {
                actions.add(new Effect.Resources(Map.of(ResourceCode.valueOf(def.element().name()), 2), null));
            }
        }
        actions.addAll(planned.actions());
        return new Computation(quest, boss, completed, new Plan(actions, planned.explanations()), ending, facts, effects);
    }

    private static List<String> rewardCards(Computation computation) {
        return computation.plan().actions().stream()
                .filter(Effect.RewardCards.class::isInstance)
                .flatMap(action -> ((Effect.RewardCards) action).cards().stream())
                .toList();
    }

    /** Награды для окна: стихии босса первыми, уже добавленные задания и полученные достижения не повторяются. */
    private BattleRewards rewards(Computation computation) {
        Map<String, Integer> perHunter = new LinkedHashMap<>();
        List<AchievementRef> achievements = new ArrayList<>();
        List<Integer> openQuests = new ArrayList<>();
        List<String> rewardCards = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CampaignFacts facts = computation.facts();
        for (Effect action : computation.plan().actions()) {
            switch (action) {
                case Effect.Resources resources -> resources.items().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(ResourceCode::ordinal)))
                        .forEach(entry -> perHunter.merge(entry.getKey().name(), entry.getValue(), Integer::sum));
                case Effect.OpenQuest open -> {
                    if (!facts.availableQuests().contains(open.quest()) && !openQuests.contains(open.quest())) {
                        openQuests.add(open.quest());
                    }
                }
                case Effect.GrantAchievement grant -> {
                    if (!facts.achievements().contains(grant.achievement())
                            && achievements.stream().noneMatch(a -> a.code().equals(grant.achievement()))) {
                        achievements.add(new AchievementRef(grant.achievement(), catalog.achievementName(grant.achievement())));
                    }
                }
                case Effect.RewardCards cards -> rewardCards.addAll(cards.cards());
                case Effect.Message message -> messages.add(message.text());
                case Effect.HunterKitUpgrade ignored -> messages.add(KIT_UPGRADE);
                default -> {
                    // кузня, лаборатория, истечение заданий и финальный бой — эффекты глав, не заданий
                }
            }
        }
        if (computation.ending() == Ending.CAMPAIGN && computation.trophyBoss() != null) {
            messages.add("Кампания пройдена! " + catalog.bossName(computation.trophyBoss()) + " повержен.");
        }
        return new BattleRewards(computation.trophyBoss() == null ? null : views.boss(computation.trophyBoss()),
                perHunter, achievements, openQuests, rewardCards, messages);
    }

    private void validate(Overrides overrides) {
        if (overrides.bossCode() != null && catalog.boss(overrides.bossCode()).isEmpty()) {
            throw invalid("Неизвестный босс: " + overrides.bossCode() + ".");
        }
        overrides.openQuests().stream().filter(number -> catalog.quest(number).isEmpty()).findFirst()
                .ifPresent(number -> {
                    throw invalid("Нет задания " + number + ".");
                });
        overrides.achievements().stream()
                .filter(code -> catalog.achievements().stream().noneMatch(def -> def.code().equals(code)))
                .findFirst()
                .ifPresent(code -> {
                    throw invalid("Нет достижения " + code + ".");
                });
    }

    private Optional<CampaignBattle> battle(UUID battleId, long campaignId) {
        Optional<CampaignBattle> battle = battles.findById(battleId);
        if (battle.isPresent() && battle.get().getCampaignId() != campaignId) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Бой не найден.");
        }
        return battle;
    }

    private static Next next(BattleReport report) {
        if (!report.victory()) {
            return Next.CAMPAIGN_SHEET;
        }
        return report.purpose() == Purpose.FINAL ? Next.CAMPAIGN_COMPLETED : Next.CHAPTER_TRANSITION;
    }

    private static Next savedNext(CampaignBattle battle) {
        if (battle.getStatus() != CampaignBattle.Status.APPLIED || battle.getResult() != CampaignBattle.Result.VICTORY) {
            return Next.CAMPAIGN_SHEET;
        }
        return battle.getPurpose() == Purpose.FINAL ? Next.CAMPAIGN_COMPLETED : Next.CHAPTER_TRANSITION;
    }

    private static String chapterClosed(int chapter) {
        return chapter == 0 ? "Пролог уже завершён" : "Глава " + chapter + " уже завершена";
    }

    private static ApiException invalid(String detail) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
    }
}
