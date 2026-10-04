package com.primal.catalog;

import com.primal.common.api.ApiNullable;
import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.EffectDescriber.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Задания и главы для API каталога (doc/api.md §4): безусловные эффекты верхнего уровня раскладываются по
 * полям, условия — готовыми формулировками в {@code rules}. Эффекты в исходном виде наружу не выходят.
 */
@Component
public class CatalogViews {

    private final CatalogService catalog;

    public CatalogViews(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record BossRef(String code, String name, @ApiNullable String element) {
    }

    public record AchievementRef(String code, String name) {
    }

    public record Rewards(
            Map<String, Integer> resources,
            List<Integer> openQuests,
            List<AchievementRef> achievements,
            List<String> rewardCards,
            List<String> messages,
            List<String> rules) {
    }

    public record Quest(int number, String name, BossRef boss, @ApiNullable String expansion, Rewards victory, Rewards expired) {
    }

    public record DecisionOption(String code, String label, List<AchievementRef> achievements) {
    }

    public record DecisionView(String code, String question, List<DecisionOption> options) {
    }

    public record Chapter(
            int chapter,
            Map<String, Integer> resources,
            List<Integer> openQuests,
            List<Integer> expireQuests,
            boolean expireAllQuests,
            boolean forgeLevelUp,
            boolean labLevelUp,
            boolean hunterKitUpgrade,
            List<AchievementRef> achievements,
            List<String> messages,
            @ApiNullable BossRef finalBattle,
            List<String> rules,
            List<DecisionView> decisions) {
    }

    public Quest quest(QuestDef def) {
        BossDef boss = catalog.boss(def.bossCode()).orElseThrow();
        return new Quest(def.number(), def.name(), bossRef(boss.code()),
                def.expansion() == null ? null : def.expansion().name(),
                rewards(def.victory()), rewards(def.expired()));
    }

    public Chapter chapter(ChapterDef def) {
        Summary summary = summarize(def.effects());
        List<DecisionView> decisions = def.decisions().stream().map(this::decision).toList();
        return new Chapter(def.chapter(), summary.resources, summary.openQuests, summary.expireQuests,
                summary.expireAll, summary.forge, summary.lab, summary.kit, summary.achievements, summary.messages,
                summary.finalBattle, catalog.describer().rules(def.effects(), Context.CHAPTER), decisions);
    }

    private Rewards rewards(List<Effect> effects) {
        Summary summary = summarize(effects);
        return new Rewards(summary.resources, summary.openQuests, summary.achievements, summary.rewardCards,
                summary.messages, catalog.describer().rules(effects, Context.QUEST));
    }

    private DecisionView decision(Decision decision) {
        return new DecisionView(decision.code(), decision.question(), decision.options().stream()
                .map(option -> new DecisionOption(option.code(), option.label(), summarize(option.effects()).achievements))
                .toList());
    }

    private BossRef bossRef(String code) {
        BossDef boss = catalog.boss(code).orElseThrow();
        return new BossRef(boss.code(), boss.name(), boss.element() == null ? null : boss.element().name());
    }

    private Summary summarize(List<Effect> effects) {
        Summary summary = new Summary();
        for (Effect effect : effects) {
            switch (effect) {
                case Effect.Resources resources -> resources.items().forEach((code, quantity) ->
                        summary.resources.merge(code.name(), quantity, Integer::sum));
                case Effect.OpenQuest open -> summary.openQuests.add(open.quest());
                case Effect.ExpireQuests expire -> summary.expireQuests.addAll(expire.quests());
                case Effect.ExpireAllQuests ignored -> summary.expireAll = true;
                case Effect.GrantAchievement grant ->
                        summary.achievements.add(new AchievementRef(grant.achievement(), catalog.achievementName(grant.achievement())));
                case Effect.ForgeLevelUp ignored -> summary.forge = true;
                case Effect.LabLevelUp ignored -> summary.lab = true;
                case Effect.HunterKitUpgrade ignored -> summary.kit = true;
                case Effect.RewardCards cards -> summary.rewardCards.addAll(cards.cards());
                case Effect.Message message -> summary.messages.add(message.text());
                case Effect.FinalBattle battle -> summary.finalBattle = bossRef(battle.boss());
                case Effect.Conditional ignored -> {
                    // условия описываются формулировками в rules
                }
            }
        }
        return summary;
    }

    private static final class Summary {
        final Map<String, Integer> resources = new LinkedHashMap<>();
        final List<Integer> openQuests = new ArrayList<>();
        final List<Integer> expireQuests = new ArrayList<>();
        final List<AchievementRef> achievements = new ArrayList<>();
        final List<String> rewardCards = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        boolean expireAll;
        boolean forge;
        boolean lab;
        boolean kit;
        BossRef finalBattle;
    }
}
