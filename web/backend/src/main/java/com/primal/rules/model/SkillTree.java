package com.primal.rules.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Древо навыков охотника — перенос {@code SkillValidatorImpl} из app: в каждой ветви две ступени,
 * ступень 2 открывается только после ступени 1.
 */
public final class SkillTree {

    public static final int TIERS = 2;

    /** Открытая ступень навыка. */
    public record Skill(SkillBranch branch, int tier) {
    }

    private SkillTree() {
    }

    /** Можно открыть: ступень ещё закрыта, а для ступени 2 открыта ступень 1. */
    public static boolean canUnlock(Set<Skill> unlocked, SkillBranch branch, int tier) {
        if (tier < 1 || tier > TIERS || unlocked.contains(new Skill(branch, tier))) {
            return false;
        }
        return tier == 1 || unlocked.contains(new Skill(branch, 1));
    }

    /** Можно снять (исправление ошибки): ступень открыта, и это не ступень 1 при открытой ступени 2. */
    public static boolean canLock(Set<Skill> unlocked, SkillBranch branch, int tier) {
        if (!unlocked.contains(new Skill(branch, tier))) {
            return false;
        }
        return tier != 1 || !unlocked.contains(new Skill(branch, 2));
    }

    /** Ступени, которые можно открыть сейчас, по порядку ветвей. */
    public static List<Skill> unlockable(Set<Skill> unlocked) {
        List<Skill> result = new ArrayList<>();
        for (SkillBranch branch : SkillBranch.values()) {
            for (int tier = 1; tier <= TIERS; tier++) {
                if (canUnlock(unlocked, branch, tier)) {
                    result.add(new Skill(branch, tier));
                }
            }
        }
        return result;
    }
}
