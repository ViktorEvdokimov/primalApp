package com.primal.progression;

import com.primal.campaign.CampaignBattles;
import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.campaign.CampaignSheetDto.Actor;
import com.primal.campaign.CampaignSheetDto.ActorKind;
import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.campaign.CampaignSheetDto.RecentBattle;
import com.primal.campaign.CampaignViews;
import com.primal.common.config.PrimalProperties;
import com.primal.identity.AccountService;
import com.primal.identity.AccountService.Author;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Чтение боёв кампании: идущие бои для предупреждений и история. Идущий бой — отметка {@code IN_PROGRESS},
 * начатая меньше {@code primal.campaign.battle-warning-hours} назад при текущем {@code progress_seq}; остальные
 * отметки устарели ({@code stale}) и не предупреждают ({@code doc/api.md} §6.3).
 */
@Service
class CampaignBattleQueries implements CampaignBattles {

    private final CampaignBattleRepository battles;
    private final CampaignViews views;
    private final AccountService accounts;
    private final Duration warningPeriod;
    private final Clock clock;

    CampaignBattleQueries(CampaignBattleRepository battles, CampaignViews views, AccountService accounts,
                          PrimalProperties properties, Clock clock) {
        this.battles = battles;
        this.views = views;
        this.accounts = accounts;
        this.warningPeriod = Duration.ofHours(properties.campaign().battleWarningHours());
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveBattle> active(long campaignId, int progressSeq) {
        Instant now = clock.instant();
        return battles.findByCampaignIdAndStatusOrderByStartedAtDesc(campaignId, CampaignBattle.Status.IN_PROGRESS).stream()
                .filter(battle -> !isStale(battle, progressSeq, now))
                .map(this::activeBattle)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentBattle> recent(long campaignId) {
        return battles.findTop5ByCampaignIdAndStatusInOrderBySubmittedAtDesc(campaignId,
                        EnumSet.of(CampaignBattle.Status.APPLIED, CampaignBattle.Status.DISMISSED)).stream()
                .map(battle -> new RecentBattle(battle.getId(), battle.getResult().name(), battle.getQuestNumber(),
                        boss(battle), battle.getChapter(), battle.getStatus().name(),
                        actor(battle.getSubmittedByDeviceId()), battle.getSubmittedAt()))
                .toList();
    }

    /** Идущая отметка устарела: начата давно или при другом {@code progressSeq} (до принятой победы, правки главы). */
    boolean isStale(CampaignBattle battle, int progressSeq, Instant now) {
        return battle.getStatus() == CampaignBattle.Status.IN_PROGRESS
                && (battle.getStartedAt().isBefore(now.minus(warningPeriod)) || battle.getProgressSeq() != progressSeq);
    }

    ActiveBattle activeBattle(CampaignBattle battle) {
        return new ActiveBattle(battle.getId(), actor(battle.getStartedByDeviceId()), battle.getStartedAt(),
                battle.getQuestNumber() == null ? null : views.quest(battle.getQuestNumber(), null), boss(battle));
    }

    CampaignBoss boss(CampaignBattle battle) {
        return battle.getBossCode() == null ? null : views.boss(battle.getBossCode());
    }

    Actor actor(UUID deviceId) {
        Author author = accounts.author(deviceId);
        return new Actor(author.guest() ? ActorKind.GUEST : ActorKind.USER, author.name());
    }
}
