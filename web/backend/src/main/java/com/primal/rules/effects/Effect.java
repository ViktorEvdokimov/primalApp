package com.primal.rules.effects;

import com.primal.rules.model.Expansion;
import com.primal.rules.model.ResourceCode;
import java.util.List;
import java.util.Map;

/**
 * Эффект награды или инструкции главы — язык эффектов (doc/data-model.md §4.3).
 * {@code expansion} — пометка, из какого дополнения инструкция; в первой версии на расчёт не влияет.
 */
public sealed interface Effect {

    Expansion expansion();

    /** Каждый охотник получает материи и растения. */
    record Resources(Map<ResourceCode, Integer> items, Expansion expansion) implements Effect {
        public Resources {
            items = Map.copyOf(items);
        }
    }

    /** Добавить задание. */
    record OpenQuest(int quest, Expansion expansion) implements Effect {
    }

    /** Истекло время заданий (только открытых). */
    record ExpireQuests(List<Integer> quests, Expansion expansion) implements Effect {
        public ExpireQuests {
            quests = List.copyOf(quests);
        }
    }

    /** Истекло время всех заданий (глава 11). */
    record ExpireAllQuests(Expansion expansion) implements Effect {
    }

    record GrantAchievement(String achievement, Expansion expansion) implements Effect {
    }

    record ForgeLevelUp(Expansion expansion) implements Effect {
    }

    record LabLevelUp(Expansion expansion) implements Effect {
    }

    /** «Каждый охотник улучшает свой набор охотника». */
    record HunterKitUpgrade(Expansion expansion) implements Effect {
    }

    /** Карты наград — игроки берут их из коробки. */
    record RewardCards(List<String> cards, Expansion expansion) implements Effect {
        public RewardCards {
            cards = List.copyOf(cards);
        }
    }

    /** Инструкция игрокам текстом. */
    record Message(String text, Expansion expansion) implements Effect {
    }

    /** Следующий бой — только с этим боссом. */
    record FinalBattle(String boss, Expansion expansion) implements Effect {
    }

    /** Условие: если {@code condition}, то {@code then}, иначе {@code otherwise} (может быть пустым). */
    record Conditional(Condition condition, List<Effect> then, List<Effect> otherwise, Expansion expansion) implements Effect {
        public Conditional {
            then = List.copyOf(then);
            otherwise = List.copyOf(otherwise);
        }
    }
}
