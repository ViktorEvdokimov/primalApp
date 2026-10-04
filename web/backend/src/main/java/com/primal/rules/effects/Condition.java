package com.primal.rules.effects;

import com.primal.rules.model.Expansion;
import java.util.List;

/** Условие эффекта (doc/data-model.md §4.3). */
public sealed interface Condition {

    /** У отряда есть достижение. */
    record HasAchievement(String achievement) implements Condition {
    }

    /** Текущая глава книги кампании — одна из перечисленных. */
    record ChapterIn(List<Integer> chapters) implements Condition {
        public ChapterIn {
            chapters = List.copyOf(chapters);
        }
    }

    /** Задание добавлено: открыто или уже выполнено. */
    record QuestAvailable(int quest) implements Condition {
    }

    /** У владельца кампании есть дополнение (настройка аккаунта, qa № 138). */
    record HasExpansion(Expansion expansion) implements Condition {
    }

    record Not(Condition condition) implements Condition {
    }

    record All(List<Condition> conditions) implements Condition {
        public All {
            conditions = List.copyOf(conditions);
        }
    }

    record Any(List<Condition> conditions) implements Condition {
        public Any {
            conditions = List.copyOf(conditions);
        }
    }
}
