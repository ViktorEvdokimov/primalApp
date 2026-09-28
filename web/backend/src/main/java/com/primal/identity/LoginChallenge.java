package com.primal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Запрос кода входа. Сам код не хранится — только {@code HMAC-SHA256(pepper, id ‖ code)}. */
@Entity
@Table(name = "login_challenge")
class LoginChallenge {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private byte[] codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts_left", nullable = false)
    private short attemptsLeft;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "requested_ip")
    private InetAddress requestedIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginChallenge() {
    }

    LoginChallenge(UUID id, String email, byte[] codeHash, Instant expiresAt, int attempts, InetAddress requestedIp,
                   Instant createdAt) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptsLeft = (short) attempts;
        this.requestedIp = requestedIp;
        this.createdAt = createdAt;
    }

    /** Код ещё можно ввести: не использован, не заменён, не устарел, попытки остались. */
    boolean isOpen(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt) && attemptsLeft > 0;
    }

    void failAttempt() {
        attemptsLeft--;
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    byte[] getCodeHash() {
        return codeHash;
    }

    int getAttemptsLeft() {
        return attemptsLeft;
    }
}
