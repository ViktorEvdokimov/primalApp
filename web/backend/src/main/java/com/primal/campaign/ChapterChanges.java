package com.primal.campaign;

import com.primal.access.AccessService.CampaignAccess;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.rules.effects.Plan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переход главы над кампанией ({@code doc/api.md} §8): после принятой победы кампания ждёт перехода; «Принять»
 * применяет эффекты решений и главы и открывает новую главу, «Отклонить» оставляет главу прежней.
 */
@Service
public class ChapterChanges {

    /** Кампания ждёт перехода из главы {@code chapter}. */
    public record PendingChapter(long campaignId, int chapter, int version) {
    }

    private final CampaignService campaigns;
    private final CampaignRepository repository;
    private final PlanApplier applier;

    ChapterChanges(CampaignService campaigns, CampaignRepository repository, PlanApplier applier) {
        this.campaigns = campaigns;
        this.repository = repository;
        this.applier = applier;
    }

    /** Ожидающий переход; перехода нет — {@code 409 CAMPAIGN_CHANGED}. */
    @Transactional(readOnly = true)
    public PendingChapter pending(long campaignId) {
        return pending(campaigns.campaign(campaignId));
    }

    /**
     * Блокирует кампанию до конца транзакции и проверяет, что подтверждён актуальный переход: устаревшая
     * версия — {@code 409 VERSION_CONFLICT} с актуальным листом (список последствий мог измениться).
     */
    @Transactional
    public PendingChapter lock(long campaignId, int expectedVersion, CampaignAccess access) {
        Campaign campaign = repository.lockById(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Кампания не найдена."));
        campaigns.requireVersion(campaign, expectedVersion, access);
        return pending(campaign);
    }

    /**
     * «Принять»: сначала эффекты выбранных вариантов решений, затем эффекты главы — уже в новой главе, затем
     * последствия невыполненных заданий, у которых истекло время.
     */
    @Transactional
    public void accept(long campaignId, int toChapter, Plan decisions, Plan chapter, Plan expiries) {
        Campaign campaign = campaigns.campaign(campaignId);
        campaign.enterChapter(toChapter);
        if (!decisions.actions().isEmpty()) {
            applier.apply(campaignId, decisions, CampaignAchievement.Source.DECISION);
        }
        applier.apply(campaignId, chapter, CampaignAchievement.Source.CHAPTER);
        if (!expiries.actions().isEmpty()) {
            applier.apply(campaignId, expiries, CampaignAchievement.Source.QUEST);
        }
    }

    /** «Отклонить»: эффекты не применяются, глава прежняя. */
    @Transactional
    public void reject(long campaignId) {
        Campaign campaign = campaigns.campaign(campaignId);
        campaign.stayInChapter();
        campaigns.touch(campaign);
    }

    private static PendingChapter pending(Campaign campaign) {
        if (campaign.getStatus() != Campaign.Status.CHAPTER_TRANSITION) {
            throw new ApiException(ErrorCode.CAMPAIGN_CHANGED, "Переход главы не ожидается: сначала нужна победа в бою.");
        }
        return new PendingChapter(campaign.getId(), campaign.getChapter(), campaign.getVersion());
    }
}
