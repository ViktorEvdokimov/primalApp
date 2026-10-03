package com.primal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Пользователь: логин (свободное поле) и пароль, пароль хранится только хешем. У аккаунтов, созданных
 * входом по почте, пароля может не быть: они задают его в настройках на запомненном устройстве, а логин
 * получают вида {@code user<id>}. Почта осталась у таких аккаунтов и больше не используется.
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

    /** Логин: нижний регистр ({@link Credentials#normalizeLogin}); у аккаунта по почте — {@code user<id>}. */
    @Column(length = 32, nullable = false)
    private String login;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    AppUser(String login, String passwordHash, String displayName, Instant createdAt) {
        this.login = login;
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

    public String getLogin() {
        return login;
    }

    void setLogin(String login) {
        this.login = login;
    }

    public String getDisplayName() {
        return displayName;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
