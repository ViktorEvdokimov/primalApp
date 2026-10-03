package com.primal.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Логин — в нижнем регистре ({@link Credentials#normalizeLogin}). */
    Optional<AppUser> findByLogin(String login);

    boolean existsByLogin(String login);
}
