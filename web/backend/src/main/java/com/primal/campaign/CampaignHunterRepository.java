package com.primal.campaign;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignHunterRepository extends JpaRepository<CampaignHunter, Long> {

    List<CampaignHunter> findByCampaignIdOrderByPosition(long campaignId);

    List<CampaignHunter> findByCampaignIdInOrderByPosition(Collection<Long> campaignIds);

    long countByCampaignId(long campaignId);
}
