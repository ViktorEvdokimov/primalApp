package com.primal.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    /** Действующая ссылка кампании — не больше одной (уникальный индекс {@code ux_share_link_campaign}). */
    Optional<ShareLink> findFirstByCampaignIdAndRevokedAtIsNull(long campaignId);
}
