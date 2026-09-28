package com.primal.identity;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {

    /** Запрос кода с блокировкой строки: параллельные попытки не обходят счётчик. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoginChallenge c where c.id = :id")
    Optional<LoginChallenge> lockById(UUID id);

    /** Новый запрос кода отменяет прежние. */
    @Modifying
    @Query("update LoginChallenge c set c.consumedAt = :now where c.email = :email and c.consumedAt is null")
    int consumeOpen(String email, Instant now);

    @Modifying
    @Query("delete from LoginChallenge c where c.createdAt < :before")
    int deleteCreatedBefore(Instant before);
}
