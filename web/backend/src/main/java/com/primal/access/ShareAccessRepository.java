package com.primal.access;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShareAccessRepository extends JpaRepository<ShareAccess, Long> {

    /** Кто открыл ссылку — окно «Поделиться» владельца. */
    List<ShareAccess> findByShareLinkIdOrderByJoinedAt(UUID shareLinkId);
}
