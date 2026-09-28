package com.primal.campaign;

import com.primal.rules.model.SkillBranch;
import com.primal.rules.model.SkillTree;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Открытая ступень навыка охотника: строка есть — ступень открыта. */
@Entity
@Table(name = "hunter_skill")
public class HunterSkill {

    /** Ветвь хранится буквой кода: A, B, V, G, D. */
    @Embeddable
    public record Key(
            @Column(name = "hunter_id", nullable = false) long hunterId,
            @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 1) String branch,
            @Column(nullable = false) short tier) {
    }

    @EmbeddedId
    private Key key;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected HunterSkill() {
    }

    HunterSkill(long hunterId, SkillBranch branch, int tier, Instant now) {
        this.key = new Key(hunterId, branch.name(), (short) tier);
        this.unlockedAt = now;
    }

    public long getHunterId() {
        return key.hunterId();
    }

    public SkillTree.Skill toSkill() {
        return new SkillTree.Skill(SkillBranch.valueOf(key.branch()), key.tier());
    }
}
