package com.primal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Пользователь: номер телефона — логин, пароль хранится только хешем. У аккаунтов, созданных входом по
 * почте, телефона и пароля может не быть: они задают их в настройках на запомненном устройстве. Почта
 * осталась у таких аккаунтов и больше не используется.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code null} — пароль не задан: аккаунт создан по почте и входит только с запомненного устройства. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Логин: {@code +79123456789} ({@link Credentials#normalizePhone}); {@code null} — ещё не задан. */
    @Column(length = 16)
    private String phone;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    AppUser(String phone, String passwordHash, String displayName, Instant createdAt) {
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
