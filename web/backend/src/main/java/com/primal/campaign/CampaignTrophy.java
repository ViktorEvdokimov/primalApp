package com.primal.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Трофей: босс, побеждённый в главе; создаётся при принятии победы (задача 5.2). */
@Entity
@Table(name = "campaign_trophy")
public class CampaignTrophy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private long campaignId;

    @Column(name = "boss_code", nullable = false, length = 32)
    private String bossCode;

    @Column(nullable = false)
    private short chapter;

    @Column(name = "campaign_battle_id", unique = true)
    private UUID campaignBattleId;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    protected CampaignTrophy() {
    }

    CampaignTrophy(long campaignId, String bossCode, int chapter, UUID campaignBattleId, Instant now) {
        this.campaignId = campaignId;
        this.bossCode = bossCode;
        this.chapter = (short) chapter;
        this.campaignBattleId = campaignBattleId;
        this.acquiredAt = now;
    }

    public String getBossCode() {
        return bossCode;
    }

    public int getChapter() {
        return chapter;
    }
}
