package com.primal.progression;

import com.primal.access.AccessService;
import com.primal.campaign.Campaign;
import com.primal.campaign.CampaignService;
import com.primal.campaign.CampaignService.BattleContext;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.campaign.CampaignViews;
import com.primal.catalog.BossDef;
import com.primal.catalog.CatalogDtos.Stance;
import com.primal.catalog.CatalogService;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.BattleDto.BattleSetup;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.rules.model.Difficulty;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подготовка к бою кампании ({@code doc/api.md} §6.1): цель боя, босс, уровень враждебности и стойки — как
 * предзаполнение экрана подготовки в {@code app} (задача 42.4).
 */
@Service
public class BattleSetupService {

    /** Босс пролога. */
    static final String PROLOGUE_BOSS = "VIRAXEN";

    /** Цель боя: {@code bossCode} — известный заранее босс; {@code forced} — выбрать другого нельзя. */
    record Target(Purpose purpose, String bossCode, boolean forced) {
    }

    private final CampaignService campaigns;
    private final CampaignBattleQueries battles;
    private final CampaignViews views;
    private final CatalogService catalog;
    private final AccessService access;

    BattleSetupService(CampaignService campaigns, CampaignBattleQueries battles, CampaignViews views,
                       CatalogService catalog, AccessService access) {
        this.campaigns = campaigns;
        this.battles = battles;
        this.views = views;
        this.catalog = catalog;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public BattleSetup setup(PrimalPrincipal principal, long campaignId, Integer questNumber) {
        access.require(principal, campaignId);
        BattleContext campaign = campaigns.battleContext(campaignId);
        Target target = target(campaign, questNumber);
        int difficulty = difficulty(campaign, target);
        List<Stance> stances = target.bossCode() == null
                ? List.of()
                : boss(target.bossCode()).stances(difficulty).stream().map(Stance::of).toList();
        return new BattleSetup(
                campaignId,
                campaign.chapter(),
                campaign.progressSeq(),
                target.purpose(),
                difficulty,
                campaign.hunterCount(),
                campaign.openQuests(),
                target.bossCode() == null ? null : views.boss(target.bossCode()),
                stances,
                target.forced() ? views.boss(target.bossCode()) : null,
                battles.active(campaignId, campaign.progressSeq()));
    }

    /**
     * Цель боя по состоянию кампании; отказы одни и те же для подготовки и отметки о начале. Финальный бой
     * главы 11 — только с финальным боссом; в прологе — бой с Вираксеном; задание должно быть открыто.
     */
    Target target(BattleContext campaign, Integer questNumber) {
        if (campaign.status() == Campaign.Status.CHAPTER_TRANSITION) {
            throw new ApiException(ErrorCode.CHAPTER_TRANSITION_PENDING,
                    "Глава " + campaign.chapter() + " завершена: сначала завершите переход главы.");
        }
        if (campaign.status() == Campaign.Status.COMPLETED) {
            throw new ApiException(ErrorCode.CAMPAIGN_CHANGED, "Кампания пройдена: новых боёв в ней нет.");
        }
        if (campaign.finalBossCode() != null) {
            if (questNumber != null) {
                throw new ApiException(ErrorCode.FINAL_BOSS_REQUIRED,
                        "Доступен только финальный бой: " + catalog.bossName(campaign.finalBossCode()) + ".");
            }
            return new Target(Purpose.FINAL, campaign.finalBossCode(), true);
        }
        if (campaign.chapter() == 0) {
            if (questNumber != null) {
                throw new ApiException(ErrorCode.QUEST_NOT_OPEN, "В прологе заданий нет: бой с Вираксеном.");
            }
            return new Target(Purpose.PROLOGUE, PROLOGUE_BOSS, true);
        }
        if (questNumber != null) {
            QuestItem quest = campaign.openQuests().stream()
                    .filter(item -> item.number() == questNumber)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.QUEST_NOT_OPEN, "Задание " + questNumber + " не открыто."));
            return new Target(Purpose.QUEST, quest.boss().code(), false);
        }
        return new Target(Purpose.FREE, null, false);
    }

    /**
     * Уровень враждебности: в прологе 0, иначе по главе; если у босса такого уровня нет, а доступен только
     * один (Пробуждённый — 3), берётся он (42.4).
     */
    int difficulty(BattleContext campaign, Target target) {
        int difficulty = target.purpose() == Purpose.PROLOGUE ? 0 : Difficulty.forChapter(campaign.chapter());
        if (target.bossCode() == null) {
            return difficulty;
        }
        BossDef boss = boss(target.bossCode());
        if (boss.stances(difficulty).isEmpty() && boss.stances().size() == 1) {
            return boss.stances().firstKey();
        }
        return difficulty;
    }

    private BossDef boss(String code) {
        return catalog.boss(code).orElseThrow();
    }
}
