package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.catalog.AchievementDef;
import com.primal.catalog.CatalogService;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.AchievementNames;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Достижения кампании вручную ({@code doc/api.md} §5.6). Название нормализуется и сопоставляется с каталогом;
 * равнозначное достижение в другом написании заменяется новым (42.1 app).
 */
@Service
public class AchievementService {

    /** Добавленное достижение; {@code matchedCatalog} — название узнано в каталоге, у достижения есть код. */
    public record Added(CampaignAchievement achievement, boolean matchedCatalog) {
    }

    private final CampaignService campaigns;
    private final CampaignAchievementRepository achievements;
    private final CatalogService catalog;
    private final AccessService access;
    private final Clock clock;

    AchievementService(CampaignService campaigns, CampaignAchievementRepository achievements, CatalogService catalog,
                       AccessService access, Clock clock) {
        this.campaigns = campaigns;
        this.achievements = achievements;
        this.catalog = catalog;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Added add(PrimalPrincipal principal, long campaignId, String rawName) {
        access.require(principal, campaignId);
        Campaign campaign = campaigns.campaign(campaignId);
        String typed = rawName.strip().replaceAll("\\s+", " ");
        Optional<AchievementDef> known = catalog.achievementByName(typed);
        String code = known.map(AchievementDef::code).orElse(null);
        String name = known.map(AchievementDef::name).orElse(typed);
        String normalized = AchievementNames.normalize(name);

        // Равнозначное достижение (то же название в другом написании или тот же код) заменяется новым
        achievements.findByCampaignIdOrderById(campaignId).stream()
                .filter(existing -> existing.getNormalizedName().equals(normalized)
                        || (code != null && code.equals(existing.getAchievementCode())))
                .forEach(achievements::delete);
        achievements.flush();
        CampaignAchievement added = achievements.saveAndFlush(new CampaignAchievement(campaignId, code, name, normalized,
                CampaignAchievement.Source.MANUAL, campaign.getChapter(), clock.instant()));
        campaigns.touch(campaign);
        return new Added(added, known.isPresent());
    }

    @Transactional
    public void delete(PrimalPrincipal principal, long campaignId, long achievementId) {
        access.require(principal, campaignId);
        CampaignAchievement achievement = achievements.findById(achievementId)
                .filter(found -> found.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Достижение не найдено."));
        achievements.delete(achievement);
        achievements.flush();
        campaigns.touch(campaigns.campaign(campaignId));
    }
}
