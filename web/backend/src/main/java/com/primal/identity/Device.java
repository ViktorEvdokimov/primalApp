package com.primal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Запомненный браузер. Токен из cookie {@code PRIMAL_DEVICE} не хранится — только его SHA-256. */
@Entity
@Table(name = "device")
class Device {

    @Id
    private UUID id;

    /** {@code null} — гость, пришедший по ссылке-приглашению. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private byte[] tokenHash;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "cookie_renewed_at", nullable = false)
    private Instant cookieRenewedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Device() {
    }

    Device(UUID id, Long userId, byte[] tokenHash, String userAgent, Instant now) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.cookieRenewedAt = now;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isGuest() {
        return userId == null;
    }

    /** Гостевое устройство входит в аккаунт: его доступы по ссылкам переходят пользователю. Cookie выдаётся заново. */
    void attachTo(long userId, String userAgent, Instant now) {
        this.userId = userId;
        this.userAgent = userAgent;
        this.lastSeenAt = now;
        this.cookieRenewedAt = now;
    }

    void revoke(Instant now) {
        revokedAt = now;
    }

    void rename(String displayName) {
        this.displayName = displayName;
    }

    UUID getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    String getDisplayName() {
        return displayName;
    }

    String getUserAgent() {
        return userAgent;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastSeenAt() {
        return lastSeenAt;
    }

    Instant getCookieRenewedAt() {
        return cookieRenewedAt;
    }
}
