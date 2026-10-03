package com.primal.catalog;

import com.primal.rules.model.Element;
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
        List<ForgeItemDef> forge,
        List<LabPotionDef> lab,
        String checksum) {

    public Optional<LabPotionDef> labPotion(String code) {
        return lab.stream().filter(potion -> potion.code().equals(code)).findFirst();
    }

    /** Предметы кузни стихии в порядке планшета. */
    public List<ForgeItemDef> forge(Element element) {
        return forge.stream().filter(item -> item.element() == element).toList();
    }

    public Optional<ForgeItemDef> forgeItem(String code) {
        return forge.stream().filter(item -> item.code().equals(code)).findFirst();
    }

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
