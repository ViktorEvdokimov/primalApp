package com.primal.campaign;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CampaignRepository extends JpaRepository<Campaign, Long> {

    long countByOwnerId(long ownerId);

    List<Campaign> findByOwnerIdOrderByUpdatedAtDesc(long ownerId);

    /** Кампания с блокировкой строки до конца транзакции: итоги боёв одной кампании применяются по очереди. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> lockById(long id);
}
