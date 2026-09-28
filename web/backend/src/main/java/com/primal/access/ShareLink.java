package com.primal.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Ссылка-приглашение на кампанию с правом редактирования. Действующая у кампании одна; перевыпуск = отзыв
 * старой и новая строка, поэтому доступы по старой ссылке пропадают сразу ({@code doc/data-model.md} §3.5).
 */
@Entity
@Table(name = "share_link")
public class ShareLink {

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private long campaignId;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(nullable = false, length = 8)
    private String permission = "EDIT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ShareLink() {
    }

    ShareLink(UUID id, long campaignId, long createdBy, Instant now) {
        this.id = id;
        this.campaignId = campaignId;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    UUID getId() {
        return id;
    }

    long getCampaignId() {
        return campaignId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
