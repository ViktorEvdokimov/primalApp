package com.primal.rules.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.rules.model.SkillTree.Skill;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Сценарии {@code SkillValidatorImpl} мобильного приложения. */
@DisplayName("Древо навыков")
class SkillTreeTest {

    @Nested
    @DisplayName("Открытие ступени")
    class Unlock {

        @Test
        @DisplayName("ступень 1 открывается у пустой ветви")
        void firstTier() {
            // вызов и проверка
            assertThat(SkillTree.canUnlock(Set.of(), SkillBranch.A, 1)).isTrue();
        }

        @Test
        @DisplayName("ступень 2 — только после ступени 1")
        void secondTierNeedsFirst() {
            // вызов и проверка
            assertThat(SkillTree.canUnlock(Set.of(), SkillBranch.B, 2)).isFalse();
            assertThat(SkillTree.canUnlock(Set.of(new Skill(SkillBranch.B, 1)), SkillBranch.B, 2)).isTrue();
        }

        @Test
        @DisplayName("ступень 1 другой ветви не открывает ступень 2")
        void otherBranchDoesNotCount() {
            // вызов и проверка
            assertThat(SkillTree.canUnlock(Set.of(new Skill(SkillBranch.A, 1)), SkillBranch.V, 2)).isFalse();
        }

        @Test
        @DisplayName("открытую ступень повторно не открыть; ступени 3 нет")
        void alreadyUnlockedOrMissing() {
            // подготовка
            Set<Skill> unlocked = Set.of(new Skill(SkillBranch.G, 1));

            // вызов и проверка
            assertThat(SkillTree.canUnlock(unlocked, SkillBranch.G, 1)).isFalse();
            assertThat(SkillTree.canUnlock(unlocked, SkillBranch.G, 3)).isFalse();
        }
    }

    @Nested
    @DisplayName("Снятие ступени")
    class Lock {

        @Test
        @DisplayName("ступень 1 не снимается при открытой ступени 2")
        void firstTierWithSecond() {
            // подготовка
            Set<Skill> unlocked = Set.of(new Skill(SkillBranch.D, 1), new Skill(SkillBranch.D, 2));

            // вызов и проверка
            assertThat(SkillTree.canLock(unlocked, SkillBranch.D, 1)).isFalse();
            assertThat(SkillTree.canLock(unlocked, SkillBranch.D, 2)).isTrue();
        }

        @Test
        @DisplayName("закрытую ступень не снять")
        void notUnlocked() {
            // вызов и проверка
            assertThat(SkillTree.canLock(Set.of(), SkillBranch.A, 1)).isFalse();
        }
    }

    @Test
    @DisplayName("доступные ступени: ступень 1 всех ветвей, ступень 2 — где открыта ступень 1; полностью открытая ветвь исчезает")
    void unlockable() {
        // подготовка
        Set<Skill> unlocked = Set.of(new Skill(SkillBranch.A, 1), new Skill(SkillBranch.B, 1), new Skill(SkillBranch.B, 2));

        // вызов
        var result = SkillTree.unlockable(unlocked);

        // проверка
        assertThat(result).containsExactly(
                new Skill(SkillBranch.A, 2),
                new Skill(SkillBranch.V, 1),
                new Skill(SkillBranch.G, 1),
                new Skill(SkillBranch.D, 1));
    }
}
