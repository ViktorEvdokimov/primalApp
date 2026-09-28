package com.primal.catalog;

import com.primal.rules.effects.Condition;
import com.primal.rules.effects.Decision;
import com.primal.rules.effects.Effect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Ссылочная целостность каталога: в YAML внешних ключей нет, поэтому до записи в БД проверяется, что каждое
 * задание, достижение и босс, на которые ссылаются задания, эффекты и условия, существуют, а главы идут от 1 до 11.
 */
final class CatalogIntegrity {

    static final int CHAPTERS = 11;

    private CatalogIntegrity() {
    }

    /** Все найденные нарушения; пустой список — каталог целостен. */
    static List<String> problems(Catalog catalog) {
        Set<String> bosses = catalog.bosses().stream().map(BossDef::code).collect(Collectors.toSet());
        Set<String> achievements = catalog.achievements().stream().map(AchievementDef::code).collect(Collectors.toSet());
        Set<Integer> quests = new HashSet<>();
        List<String> problems = new ArrayList<>();

        for (QuestDef quest : catalog.quests()) {
            if (!quests.add(quest.number())) {
                problems.add("задание " + quest.number() + " повторяется");
            }
        }
        References refs = new References(bosses, achievements, quests, problems);
        for (QuestDef quest : catalog.quests()) {
            String where = "задание " + quest.number();
            if (!bosses.contains(quest.bossCode())) {
                problems.add(where + ": нет босса " + quest.bossCode());
            }
            refs.effects(quest.victory(), where + ", победа");
            refs.effects(quest.defeat(), where + ", поражение");
        }

        List<Integer> chapterNumbers = catalog.chapters().stream().map(ChapterDef::chapter).toList();
        List<Integer> expected = IntStream.rangeClosed(1, CHAPTERS).boxed().toList();
        if (!chapterNumbers.equals(expected)) {
            problems.add("главы должны идти по порядку 1–" + CHAPTERS + ", а указаны " + chapterNumbers);
        }
        for (ChapterDef chapter : catalog.chapters()) {
            String where = "глава " + chapter.chapter();
            refs.effects(chapter.effects(), where);
            Set<String> decisionCodes = new HashSet<>();
            for (Decision decision : chapter.decisions()) {
                if (!decisionCodes.add(decision.code())) {
                    problems.add(where + ": решение " + decision.code() + " повторяется");
                }
                for (Decision.Option option : decision.options()) {
                    refs.effects(option.effects(), where + ", " + decision.code() + "." + option.code());
                }
            }
        }
        return problems;
    }

    private record References(Set<String> bosses, Set<String> achievements, Set<Integer> quests, List<String> problems) {

        void effects(List<Effect> effects, String where) {
            for (Effect effect : effects) {
                switch (effect) {
                    case Effect.OpenQuest open -> quest(open.quest(), where);
                    case Effect.ExpireQuests expire -> expire.quests().forEach(number -> quest(number, where));
                    case Effect.GrantAchievement grant -> achievement(grant.achievement(), where);
                    case Effect.FinalBattle battle -> {
                        if (!bosses.contains(battle.boss())) {
                            problems.add(where + ": нет босса " + battle.boss());
                        }
                    }
                    case Effect.Conditional conditional -> {
                        condition(conditional.condition(), where);
                        effects(conditional.then(), where);
                        effects(conditional.otherwise(), where);
                    }
                    default -> {
                        // остальные эффекты ни на что не ссылаются
                    }
                }
            }
        }

        void condition(Condition condition, String where) {
            switch (condition) {
                case Condition.HasAchievement has -> achievement(has.achievement(), where);
                case Condition.QuestAvailable available -> quest(available.quest(), where);
                case Condition.ChapterIn in -> in.chapters().stream()
                        .filter(chapter -> chapter < 0 || chapter > CHAPTERS)
                        .forEach(chapter -> problems.add(where + ": глава " + chapter + " вне 0–" + CHAPTERS));
                case Condition.Not not -> condition(not.condition(), where);
                case Condition.All all -> all.conditions().forEach(c -> condition(c, where));
                case Condition.Any any -> any.conditions().forEach(c -> condition(c, where));
            }
        }

        private void quest(int number, String where) {
            if (!quests.contains(number)) {
                problems.add(where + ": нет задания " + number);
            }
        }

        private void achievement(String code, String where) {
            if (!achievements.contains(code)) {
                problems.add(where + ": нет достижения " + code);
            }
        }
    }
}
