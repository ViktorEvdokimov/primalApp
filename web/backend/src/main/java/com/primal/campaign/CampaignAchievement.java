package com.primal.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Достижение кампании: из каталога ({@code achievementCode}) или своё, введённое вручную. */
@Entity
@Table(name = "campaign_achievement")
public class CampaignAchievement {

    public enum Source { QUEST, CHAPTER, DECISION, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private long campaignId;

    @Column(name = "achievement_code", length = 48)
    private String achievementCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Source source;

    @Column(name = "granted_in_chapter", nullable = false)
    private short grantedInChapter;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected CampaignAchievement() {
    }

    CampaignAchievement(long campaignId, String achievementCode, String name, String normalizedName, Source source,
                        int chapter, Instant now) {
        this.campaignId = campaignId;
        this.achievementCode = achievementCode;
        this.name = name;
        this.normalizedName = normalizedName;
        this.source = source;
        this.grantedInChapter = (short) chapter;
        this.grantedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getCampaignId() {
        return campaignId;
    }

    public String getAchievementCode() {
        return achievementCode;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public Source getSource() {
        return source;
    }

    public int getGrantedInChapter() {
        return grantedInChapter;
    }
}
