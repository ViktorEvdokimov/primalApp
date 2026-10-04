package com.primal.rules.effects;

import com.primal.rules.effects.Condition.All;
import com.primal.rules.effects.Condition.Any;
import com.primal.rules.effects.Condition.ChapterIn;
import com.primal.rules.effects.Condition.HasAchievement;
import com.primal.rules.effects.Condition.HasExpansion;
import com.primal.rules.effects.Condition.Not;
import com.primal.rules.effects.Condition.QuestAvailable;
import com.primal.rules.effects.Effect.Conditional;
import com.primal.rules.effects.EffectDescriber.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Планировщик эффектов ({@code doc/data-model.md} §4.3) — перенос {@code ConditionOutcomes.kt}: какие действия
 * будут выполнены и почему. Чистая функция: условия вычисляются по снимку кампании до применения набора,
 * поэтому достижения и задания, которые выдаёт этот же набор, на его условия не влияют.
 */
public final class EffectPlanner {

    /**
     * Вид условного правила — от него зависит текст результата и раздел окна наград (как в app): «Условные
     * задания» — {@code QUEST}, «Условные награды» — остальные.
     */
    public enum RuleKind { QUEST, ACHIEVEMENT, KIT, MESSAGE, OTHER }

    private final EffectDescriber describer;
    private final Function<String, String> achievementName;

    public EffectPlanner(EffectDescriber describer, Function<String, String> achievementName) {
        this.describer = describer;
        this.achievementName = achievementName;
    }

    public Plan plan(List<Effect> effects, CampaignFacts facts, Context context) {
        List<Effect> actions = new ArrayList<>();
        List<RuleExplanation> explanations = new ArrayList<>();
        for (Effect effect : effects) {
            if (effect instanceof Conditional conditional) {
                List<Effect> chosen = new ArrayList<>();
                collect(conditional, facts, chosen);
                actions.addAll(chosen);
                explanations.add(new RuleExplanation(describer.describe(conditional, context),
                        result(conditional, chosen, facts, context)));
            } else {
                actions.add(effect);
            }
        }
        return new Plan(actions, explanations);
    }

    /** Правило открывает задания хотя бы в одной ветви — такие пояснения показываются при «Выполнено» вручную. */
    public static boolean opensQuests(Conditional conditional) {
        return kind(conditional) == RuleKind.QUEST;
    }

    /** Условие по снимку кампании. */
    public static boolean holds(Condition condition, CampaignFacts facts) {
        return switch (condition) {
            case HasAchievement has -> facts.achievements().contains(has.achievement());
            case ChapterIn in -> in.chapters().contains(facts.chapter());
            case QuestAvailable available -> facts.availableQuests().contains(available.quest());
            case HasExpansion has -> facts.expansions().contains(has.expansion());
            case Not not -> !holds(not.condition(), facts);
            case All all -> all.conditions().stream().allMatch(c -> holds(c, facts));
            case Any any -> any.conditions().stream().anyMatch(c -> holds(c, facts));
        };
    }

    /** Действия выбранной ветви; вложенные условия вычисляются по тому же снимку. */
    private static void collect(Effect effect, CampaignFacts facts, List<Effect> out) {
        if (effect instanceof Conditional conditional) {
            List<Effect> branch = holds(conditional.condition(), facts) ? conditional.then() : conditional.otherwise();
            branch.forEach(nested -> collect(nested, facts, out));
        } else {
            out.add(effect);
        }
    }

    /** Результат правила — тексты окон наград app. */
    private String result(Conditional conditional, List<Effect> chosen, CampaignFacts facts, Context context) {
        boolean holds = holds(conditional.condition(), facts);
        return switch (kind(conditional)) {
            case QUEST -> chosen.stream()
                    .filter(Effect.OpenQuest.class::isInstance)
                    .map(effect -> "Добавлено задание " + ((Effect.OpenQuest) effect).quest() + ".")
                    .findFirst()
                    .orElse(context == Context.QUEST && conditional.condition() instanceof HasAchievement && !holds
                            ? "Достижения нет, задание не добавляется."
                            : "Условие не выполнено, задание не добавляется.");
            case ACHIEVEMENT -> chosen.stream()
                    .filter(Effect.GrantAchievement.class::isInstance)
                    .map(effect -> ((Effect.GrantAchievement) effect).achievement())
                    .findFirst()
                    .map(code -> facts.achievements().contains(code)
                            ? "Достижение " + quoted(code) + " уже получено."
                            : "Достижение есть, добавлено достижение " + quoted(code) + ".")
                    .orElse("Достижения нет.");
            case KIT -> holds ? "Достижение есть, улучшите набор охотника." : "Достижения нет.";
            case MESSAGE -> chosen.stream()
                    .filter(Effect.Message.class::isInstance)
                    .map(effect -> "Достижение есть: " + ((Effect.Message) effect).text() + ".")
                    .findFirst()
                    .orElse("Достижения нет.");
            case OTHER -> chosen.isEmpty() ? "Условие не выполнено." : "Условие выполнено.";
        };
    }

    /** Вид правила по действиям обеих ветвей: задания важнее достижений, достижения — улучшения набора. */
    public static RuleKind kind(Conditional conditional) {
        List<Effect> all = new ArrayList<>();
        flatten(conditional, all);
        if (all.stream().anyMatch(Effect.OpenQuest.class::isInstance)) {
            return RuleKind.QUEST;
        }
        if (all.stream().anyMatch(Effect.GrantAchievement.class::isInstance)) {
            return RuleKind.ACHIEVEMENT;
        }
        if (all.stream().anyMatch(Effect.HunterKitUpgrade.class::isInstance)) {
            return RuleKind.KIT;
        }
        if (all.stream().anyMatch(Effect.Message.class::isInstance)) {
            return RuleKind.MESSAGE;
        }
        return RuleKind.OTHER;
    }

    /** Все действия обеих ветвей, включая вложенные. */
    private static void flatten(Effect effect, List<Effect> out) {
        if (effect instanceof Conditional conditional) {
            conditional.then().forEach(nested -> flatten(nested, out));
            conditional.otherwise().forEach(nested -> flatten(nested, out));
        } else {
            out.add(effect);
        }
    }

    private String quoted(String code) {
        return "«" + achievementName.apply(code) + "»";
    }
}
