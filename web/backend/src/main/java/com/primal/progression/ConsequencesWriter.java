package com.primal.progression;

import com.primal.campaign.BattleOutcomes.Ending;
import com.primal.catalog.CatalogService;
import com.primal.catalog.CatalogViews.AchievementRef;
import com.primal.progression.BattleResultDto.BattleRewards;
import com.primal.rules.model.ResourceCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Тексты окон подтверждения «Отклонить» — итога боя ({@code doc/api.md} §7.2) и наград главы (§8): что не
 * произойдёт. Строятся из того же плана, что и превью, поэтому пустых пунктов нет.
 */
@Component
class ConsequencesWriter {

    private final CatalogService catalog;

    ConsequencesWriter(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** «Отклонить» итог боя. */
    List<String> write(Integer completedQuest, BattleRewards rewards, Ending ending, int chapter) {
        List<String> lines = new ArrayList<>();
        if (completedQuest != null) {
            lines.add("Задание " + completedQuest + " «" + catalog.quest(completedQuest).orElseThrow().name()
                    + "» не будет отмечено выполненным.");
        }
        if (rewards.trophy() != null) {
            lines.add("Трофей «" + rewards.trophy().name() + "» не будет получен.");
        }
        resources(rewards.perHunter(), lines);
        openQuests(rewards.openQuests(), lines);
        achievements(rewards.achievements(), "", lines);
        switch (ending) {
            case CHAPTER -> lines.add(chapter == 0
                    ? "Переход в главу 1 не состоится, останется пролог."
                    : "Переход в главу " + (chapter + 1) + " не состоится, глава останется " + chapter + ".");
            case CAMPAIGN -> lines.add("Кампания не будет завершена.");
            case NONE -> {
            }
        }
        if (lines.isEmpty()) {
            lines.add("Бой будет записан в историю, кампания не изменится.");
        } else {
            lines.add("Награды можно внести вручную на листе кампании.");
        }
        return lines;
    }

    /** «Отклонить» награды главы: эффекты не применятся, глава останется прежней. */
    List<String> transition(int fromChapter, int toChapter, ChapterTransitionService.Changes changes) {
        List<String> lines = new ArrayList<>();
        lines.add((fromChapter == 0 ? "Останется пролог." : "Глава останется " + fromChapter + ".")
                + " После следующей победы переход в главу " + toChapter + " будет предложен снова.");
        achievements(changes.decisionAchievements(), " (решение главы)", lines);
        resources(changes.perHunter(), lines);
        openQuests(changes.openQuests(), lines);
        achievements(changes.chapterAchievements(), "", lines);
        List<String> expiring = changes.expireQuests().stream()
                .filter(ChapterTransitionDto.ExpiringQuest::wasOpen)
                .map(quest -> String.valueOf(quest.number()))
                .toList();
        if (expiring.size() == 1) {
            lines.add("Не истечёт время задания " + expiring.getFirst() + ".");
        } else if (expiring.size() > 1) {
            lines.add("Не истечёт время заданий " + String.join(", ", expiring) + ".");
        }
        if (changes.forgeLevelUp()) {
            lines.add("Кузня не улучшится.");
        }
        if (changes.labLevelUp()) {
            lines.add("Лаборатория не улучшится.");
        }
        if (changes.hunterKitUpgrade()) {
            lines.add("Не будет улучшения набора охотника.");
        }
        if (!changes.rewardCards().isEmpty()) {
            lines.add("Не будут выданы карты наград: " + String.join(", ", changes.rewardCards()) + ".");
        }
        if (changes.finalBattle() != null) {
            lines.add("Финальный бой с боссом «" + catalog.bossName(changes.finalBattle()) + "» не будет назначен.");
        }
        lines.add("Главу, задания и достижения можно изменить вручную на листе кампании.");
        return lines;
    }

    private static void resources(Map<String, Integer> perHunter, List<String> lines) {
        if (!perHunter.isEmpty()) {
            lines.add("Охотники не получат ресурсы: " + perHunter.entrySet().stream()
                    .map(entry -> ResourceCode.valueOf(entry.getKey()).displayName() + " " + entry.getValue())
                    .collect(Collectors.joining(", ")) + ".");
        }
    }

    private static void openQuests(List<Integer> quests, List<String> lines) {
        if (quests.size() == 1) {
            lines.add("Не будет добавлено задание " + quests.getFirst() + ".");
        } else if (quests.size() > 1) {
            lines.add("Не будут добавлены задания " + quests.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ".");
        }
    }

    private static void achievements(List<AchievementRef> achievements, String note, List<String> lines) {
        List<String> names = achievements.stream().map(a -> "«" + a.name() + "»").toList();
        if (names.size() == 1) {
            lines.add("Не будет получено достижение " + names.getFirst() + note + ".");
        } else if (names.size() > 1) {
            lines.add("Не будут получены достижения " + String.join(", ", names) + note + ".");
        }
    }
}
