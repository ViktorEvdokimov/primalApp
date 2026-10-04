package com.primal.rules.effects;

import com.primal.rules.effects.Condition.All;
import com.primal.rules.effects.Condition.Any;
import com.primal.rules.effects.Condition.ChapterIn;
import com.primal.rules.effects.Condition.HasAchievement;
import com.primal.rules.effects.Condition.HasExpansion;
import com.primal.rules.effects.Condition.Not;
import com.primal.rules.effects.Condition.QuestAvailable;
import com.primal.rules.effects.Effect.Conditional;
import com.primal.rules.model.ResourceCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Формулировки правил для игроков: «Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6».
 * Перенос {@code describeTaskCondition} и {@code describeChapterCondition} из app ({@code ConditionOutcomes.kt}):
 * в главах условие с «иначе» открывает задание («открыть задание 7, иначе добавить задание 8»), в заданиях —
 * добавляет. Вложенное условие описывается после двоеточия (задание 25).
 */
public final class EffectDescriber {

    /** Где описывается правило: у задания или у главы — формулировки немного различаются (как в app). */
    public enum Context { QUEST, CHAPTER }

    private final Function<String, String> achievementName;
    private final Function<String, String> bossName;

    /**
     * @param achievementName название достижения по коду
     * @param bossName        имя босса по коду
     */
    public EffectDescriber(Function<String, String> achievementName, Function<String, String> bossName) {
        this.achievementName = achievementName;
        this.bossName = bossName;
    }

    /** Формулировки всех условий верхнего уровня в порядке списка. */
    public List<String> rules(List<Effect> effects, Context context) {
        List<String> rules = new ArrayList<>();
        for (Effect effect : effects) {
            if (effect instanceof Conditional conditional) {
                rules.add(describe(conditional, context));
            }
        }
        return rules;
    }

    public String describe(Conditional conditional, Context context) {
        String condition = condition(conditional.condition());
        List<Effect> then = conditional.then();
        List<Effect> otherwise = conditional.otherwise();
        if (otherwise.isEmpty() && then.size() == 1) {
            Effect only = then.getFirst();
            if (only instanceof Effect.HunterKitUpgrade) {
                return "Если " + condition + " — улучшение набора охотника";
            }
            if (only instanceof Effect.Message message) {
                return "Если " + condition + ": " + message.text();
            }
            if (only instanceof Conditional nested) {
                return "Если " + condition + ": " + lowerFirst(describe(nested, context));
            }
        }
        String separator = mentionsChapter(conditional.condition()) ? ", то " : ", ";
        boolean chapterWithElse = context == Context.CHAPTER && !otherwise.isEmpty();
        StringBuilder text = new StringBuilder("Если ").append(condition).append(separator)
                .append(effects(then, context, chapterWithElse));
        if (!otherwise.isEmpty()) {
            text.append(", иначе ").append(effects(otherwise, context, false));
        }
        return text.toString();
    }

    public String condition(Condition condition) {
        return switch (condition) {
            case HasAchievement has -> "есть достижение " + quoted(has.achievement());
            case ChapterIn in -> "текущая глава " + joinWords(in.chapters().stream().map(String::valueOf).toList(), "или");
            case QuestAvailable available -> "задание " + available.quest() + " доступно";
            case HasExpansion has -> "есть дополнение «" + has.expansion().displayName() + "»";
            case Not not -> negated(not.condition());
            case All all when onlyAchievements(all.conditions()) -> "есть достижения " + joinWords(quotedAll(all.conditions()), "и");
            case Any any when onlyAchievements(any.conditions()) -> "есть достижение " + joinWords(quotedAll(any.conditions()), "или");
            case All all -> all.conditions().stream().map(this::condition).collect(Collectors.joining(" и "));
            case Any any -> any.conditions().stream().map(this::condition).collect(Collectors.joining(" или "));
        };
    }

    private String negated(Condition condition) {
        return switch (condition) {
            case HasAchievement has -> "нет достижения " + quoted(has.achievement());
            case QuestAvailable available -> "задание " + available.quest() + " ещё не доступно";
            case HasExpansion has -> "нет дополнения «" + has.expansion().displayName() + "»";
            case All all when onlyAchievements(all.conditions()) ->
                    "нет хотя бы одного из достижений " + joinWords(quotedAll(all.conditions()), "и");
            case Any any when onlyAchievements(any.conditions()) ->
                    "нет ни одного из достижений " + joinWords(quotedAll(any.conditions()), "и");
            default -> "не выполнено условие «" + condition(condition) + "»";
        };
    }

    /** Одно действие плана: «добавить задание 6», «добавить достижение «Оледенение»». */
    public String action(Effect effect) {
        return effect(effect, Context.QUEST, false);
    }

    private String effects(List<Effect> effects, Context context, boolean openVerb) {
        return effects.stream().map(effect -> effect(effect, context, openVerb)).collect(Collectors.joining(", "));
    }

    private String effect(Effect effect, Context context, boolean openVerb) {
        return switch (effect) {
            case Effect.OpenQuest open -> (openVerb ? "открыть задание " : "добавить задание ") + open.quest();
            case Effect.GrantAchievement grant -> "добавить достижение " + quoted(grant.achievement());
            case Effect.ExpireQuests expire -> "истекло время заданий "
                    + expire.quests().stream().map(String::valueOf).collect(Collectors.joining(", "));
            case Effect.ExpireAllQuests ignored -> "истекло время всех заданий";
            case Effect.HunterKitUpgrade ignored -> "улучшение набора охотника";
            case Effect.ForgeLevelUp ignored -> "повышение уровня кузни";
            case Effect.LabLevelUp ignored -> "повышение уровня лаборатории";
            case Effect.RewardCards cards -> "карты наград " + String.join(", ", cards.cards());
            case Effect.Message message -> message.text();
            case Effect.FinalBattle battle -> "следующий бой — финальный: " + bossName.apply(battle.boss());
            case Effect.Resources resources -> "каждый охотник получает " + resources(resources.items());
            case Conditional nested -> lowerFirst(describe(nested, context));
        };
    }

    private static String resources(Map<ResourceCode, Integer> items) {
        return items.entrySet().stream()
                .map(entry -> entry.getKey().displayName() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private boolean mentionsChapter(Condition condition) {
        return switch (condition) {
            case ChapterIn ignored -> true;
            case Not not -> mentionsChapter(not.condition());
            case All all -> all.conditions().stream().anyMatch(this::mentionsChapter);
            case Any any -> any.conditions().stream().anyMatch(this::mentionsChapter);
            default -> false;
        };
    }

    private static boolean onlyAchievements(List<Condition> conditions) {
        return conditions.stream().allMatch(HasAchievement.class::isInstance);
    }

    private List<String> quotedAll(List<Condition> conditions) {
        return conditions.stream().map(c -> quoted(((HasAchievement) c).achievement())).toList();
    }

    private String quoted(String achievementCode) {
        return "«" + achievementName.apply(achievementCode) + "»";
    }

    /** «1, 2 или 3». */
    private static String joinWords(List<String> items, String conjunction) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " " + conjunction + " " + items.getLast();
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
