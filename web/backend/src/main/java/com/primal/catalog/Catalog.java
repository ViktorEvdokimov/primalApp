package com.primal.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Каталог игры в памяти.
 *
 * @param checksum SHA-256 файлов каталога — ETag ответов {@code /catalog/**} и контрольная сумма миграции
 */
public record Catalog(
        List<BossDef> bosses,
        List<AchievementDef> achievements,
        List<QuestDef> quests,
        List<ChapterDef> chapters,
        String checksum) {

    public Optional<BossDef> boss(String code) {
        return bosses.stream().filter(boss -> boss.code().equals(code)).findFirst();
    }

    public Optional<AchievementDef> achievement(String code) {
        return achievements.stream().filter(achievement -> achievement.code().equals(code)).findFirst();
    }

    public Optional<QuestDef> quest(int number) {
        return quests.stream().filter(quest -> quest.number() == number).findFirst();
    }

    public Optional<ChapterDef> chapter(int number) {
        return chapters.stream().filter(chapter -> chapter.chapter() == number).findFirst();
    }
}
