package com.primal.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignTrophyRepository extends JpaRepository<CampaignTrophy, Long> {

    List<CampaignTrophy> findByCampaignIdOrderByChapterAscIdAsc(long campaignId);
}
