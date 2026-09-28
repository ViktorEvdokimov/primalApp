package com.primal.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Кто открыл ссылку: вошедший пользователь — доступ на всех его устройствах ({@code userId}), гость — только
 * на этом устройстве ({@code deviceId}). Записывается ровно одно из двух.
 */
@Entity
@Table(name = "share_access")
public class ShareAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_link_id", nullable = false)
    private UUID shareLinkId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ShareAccess() {
    }

    Long getUserId() {
        return userId;
    }

    UUID getDeviceId() {
        return deviceId;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
