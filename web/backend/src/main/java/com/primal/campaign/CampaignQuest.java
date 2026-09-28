package com.primal.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** Задание в кампании: открыто, выполнено или истекло; задание в кампании бывает только один раз. */
@Entity
@Table(name = "campaign_quest")
public class CampaignQuest {

    public enum Status { OPEN, COMPLETED, EXPIRED }

    @Embeddable
    public record Key(
            @Column(name = "campaign_id", nullable = false) long campaignId,
            @Column(name = "quest_number", nullable = false) short questNumber) {
    }

    @EmbeddedId
    private Key key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "opened_in_chapter", nullable = false)
    private short openedInChapter;

    @Column(name = "closed_in_chapter")
    private Short closedInChapter;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampaignQuest() {
    }

    CampaignQuest(long campaignId, int questNumber, int chapter, Instant now) {
        this.key = new Key(campaignId, (short) questNumber);
        this.status = Status.OPEN;
        this.openedInChapter = (short) chapter;
        this.updatedAt = now;
    }

    void close(Status status, int chapter, Instant now) {
        this.status = status;
        this.closedInChapter = (short) chapter;
        this.updatedAt = now;
    }

    void reopen(Instant now) {
        this.status = Status.OPEN;
        this.closedInChapter = null;
        this.updatedAt = now;
    }

    public int getQuestNumber() {
        return key.questNumber();
    }

    public Status getStatus() {
        return status;
    }

    public int getOpenedInChapter() {
        return openedInChapter;
    }

    public Integer getClosedInChapter() {
        return closedInChapter == null ? null : closedInChapter.intValue();
    }
}
