package com.primal.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Телефон — в формате {@code +79123456789} ({@link Credentials#normalizePhone}). */
    Optional<AppUser> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
