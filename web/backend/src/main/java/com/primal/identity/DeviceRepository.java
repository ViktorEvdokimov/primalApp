package com.primal.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByTokenHash(byte[] tokenHash);

    /** Действующие устройства пользователя, недавние сверху. */
    @Query("select d from Device d where d.userId = :userId and d.revokedAt is null order by d.lastSeenAt desc")
    List<Device> findActiveByUser(long userId);

    /** Визит устройства; при продлении cookie — и время продления. */
    @Transactional
    @Modifying
    @Query("""
            update Device d set d.lastSeenAt = :now,
                d.cookieRenewedAt = case when :renewCookie = true then :now else d.cookieRenewedAt end
            where d.id = :id""")
    int touch(UUID id, Instant now, boolean renewCookie);

    @Transactional
    @Modifying
    @Query("update Device d set d.revokedAt = :now where d.id = :id and d.revokedAt is null")
    int revoke(UUID id, Instant now);

    @Transactional
    @Modifying
    @Query("update Device d set d.revokedAt = :now where d.userId = :userId and d.id <> :keep and d.revokedAt is null")
    int revokeOthers(long userId, UUID keep, Instant now);
}
