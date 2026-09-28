package com.primal.progression;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignBattleRepository extends JpaRepository<CampaignBattle, UUID> {

    Optional<CampaignBattle> findByIdAndCampaignId(UUID id, long campaignId);

    /** Бои кампании, новые сначала. */
    List<CampaignBattle> findByCampaignIdOrderByStartedAtDesc(long campaignId);

    List<CampaignBattle> findByCampaignIdAndStatusOrderByStartedAtDesc(long campaignId, CampaignBattle.Status status);

    /** Принятая победа, закрывшая главу с этим {@code progressSeq}, — для причины отказа в результате. */
    Optional<CampaignBattle> findFirstByCampaignIdAndProgressSeqAndStatusAndResultOrderBySubmittedAtDesc(
            long campaignId, int progressSeq, CampaignBattle.Status status, CampaignBattle.Result result);

    /** Последние завершённые бои для листа кампании. */
    List<CampaignBattle> findTop5ByCampaignIdAndStatusInOrderBySubmittedAtDesc(long campaignId,
                                                                               Collection<CampaignBattle.Status> statuses);
}
