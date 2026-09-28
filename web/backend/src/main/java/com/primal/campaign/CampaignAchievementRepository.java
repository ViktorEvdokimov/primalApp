package com.primal.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignAchievementRepository extends JpaRepository<CampaignAchievement, Long> {

    List<CampaignAchievement> findByCampaignIdOrderById(long campaignId);
}
