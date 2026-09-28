package com.primal.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignQuestRepository extends JpaRepository<CampaignQuest, CampaignQuest.Key> {

    List<CampaignQuest> findByKeyCampaignId(long campaignId);
}
