package com.primal.campaign;

import com.primal.rules.model.HunterClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Охотник отряда: класс и имя игрока. Удаляется вместе с кампанией (каскад в БД). */
@Entity
@Table(name = "campaign_hunter")
public class CampaignHunter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hunter_class", nullable = false, length = 16)
    private HunterClass hunterClass;

    @Column(name = "player_name", nullable = false, length = 60)
    private String playerName;

    @Column(nullable = false)
    private short position;

    protected CampaignHunter() {
    }

    CampaignHunter(long campaignId, HunterClass hunterClass, String playerName, int position) {
        this.campaignId = campaignId;
        this.hunterClass = hunterClass;
        this.playerName = playerName;
        this.position = (short) position;
    }

    void rename(String playerName) {
        this.playerName = playerName;
    }

    public Long getId() {
        return id;
    }

    public long getCampaignId() {
        return campaignId;
    }

    public HunterClass getHunterClass() {
        return hunterClass;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getPosition() {
        return position;
    }
}
