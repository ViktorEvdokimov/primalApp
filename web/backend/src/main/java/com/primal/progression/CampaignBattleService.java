package com.primal.progression;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignService;
import com.primal.campaign.CampaignService.BattleContext;
import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.catalog.CatalogService;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.BattleDto.BattleView;
import com.primal.progression.BattleDto.StartedBattle;
import com.primal.progression.BattleSetupService.Target;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.realtime.ChangeEvents;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отметки о начале боя и история боёв кампании ({@code doc/api.md} §6.2–6.3). Отметки только предупреждают
 * других участников: начать бой можно всегда, даже если идёт другой.
 */
@Service
public class CampaignBattleService {

    /** Отметка о начале: снимок главы и {@code progressSeq} — из подготовки к бою. */
    public record StartCommand(UUID id, Integer questNumber, String bossCode, int difficulty, int chapter, int progressSeq,
                               Instant startedAt) {
    }

    /** {@code created} — запись создана этим запросом, а не повтором с тем же {@code id}. */
    public record Started(StartedBattle battle, boolean created) {
    }

    private final CampaignBattleRepository battles;
    private final CampaignBattleQueries queries;
    private final BattleSetupService setup;
    private final CampaignService campaigns;
    private final CatalogService catalog;
    private final AccessService access;
    private final JdbcTemplate jdbc;
    private final ChangeEvents changes;
    private final Clock clock;

    CampaignBattleService(CampaignBattleRepository battles, CampaignBattleQueries queries, BattleSetupService setup,
                          CampaignService campaigns, CatalogService catalog, AccessService access, JdbcTemplate jdbc,
                          ChangeEvents changes, Clock clock) {
        this.battles = battles;
        this.queries = queries;
        this.setup = setup;
        this.campaigns = campaigns;
        this.catalog = catalog;
        this.access = access;
        this.jdbc = jdbc;
        this.changes = changes;
        this.clock = clock;
    }

    /**
     * Отметка о начале боя. Повтор с тем же {@code id} возвращает существующую запись; идущие бои не мешают, а
     * возвращаются в {@code otherActiveBattles} для предупреждения. Проверки — как у подготовки к бою.
     */
    @Transactional
    public Started start(PrimalPrincipal principal, long campaignId, StartCommand command) {
        access.require(principal, campaignId);
        BattleContext campaign = campaigns.battleContext(campaignId);
        Optional<CampaignBattle> existing = battles.findById(command.id());
        if (existing.isPresent()) {
            return new Started(started(own(existing.get(), campaignId), campaign), false);
        }

        Target target = setup.target(campaign, command.questNumber());
        if (command.bossCode() != null && catalog.boss(command.bossCode()).isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Неизвестный босс: " + command.bossCode() + ".");
        }
        if (target.purpose() == Purpose.FINAL && !target.bossCode().equals(command.bossCode())) {
            throw new ApiException(ErrorCode.FINAL_BOSS_REQUIRED,
                    "Доступен только финальный бой: " + catalog.bossName(target.bossCode()) + ".");
        }
        // Время старта — от браузера (бой мог начаться без сети), но не позже настоящего момента
        Instant now = clock.instant();
        Instant startedAt = command.startedAt().isAfter(now) ? now : command.startedAt();
        // Два одинаковых запроса одновременно (повтор при обрыве сети) не должны столкнуться на первичном ключе
        int inserted = jdbc.update("""
                        insert into campaign_battle (id, campaign_id, purpose, quest_number, boss_code, difficulty, chapter,
                                                     progress_seq, status, started_by_device_id, started_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?)
                        on conflict (id) do nothing""",
                command.id(), campaignId, target.purpose().name(),
                new SqlParameterValue(Types.SMALLINT, target.purpose() == Purpose.QUEST ? command.questNumber() : null),
                new SqlParameterValue(Types.VARCHAR, command.bossCode()),
                command.difficulty(), command.chapter(), command.progressSeq(), principal.deviceId(),
                OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC));
        CampaignBattle battle = own(battles.findById(command.id()).orElseThrow(), campaignId);
        if (inserted == 1) {
            changes.battlesChanged(campaignId); // баннер «Идёт бой» у других участников
        }
        return new Started(started(battle, campaign), inserted == 1);
    }

    /** Бои кампании, новые сначала; {@code status} — только бои с этим статусом. */
    @Transactional(readOnly = true)
    public List<BattleView> list(PrimalPrincipal principal, long campaignId, CampaignBattle.Status status) {
        access.require(principal, campaignId);
        int progressSeq = campaigns.battleContext(campaignId).progressSeq();
        Instant now = clock.instant();
        List<CampaignBattle> found = status == null
                ? battles.findByCampaignIdOrderByStartedAtDesc(campaignId)
                : battles.findByCampaignIdAndStatusOrderByStartedAtDesc(campaignId, status);
        return found.stream()
                .map(battle -> new BattleView(
                        battle.getId(),
                        battle.getStatus(),
                        battle.getPurpose(),
                        battle.getQuestNumber(),
                        queries.boss(battle),
                        battle.getDifficulty(),
                        battle.getChapter(),
                        battle.getResult(),
                        battle.getRoundsPlayed(),
                        queries.actor(battle.getStartedByDeviceId()),
                        battle.getStartedAt(),
                        battle.getSubmittedAt() == null ? null : queries.actor(battle.getSubmittedByDeviceId()),
                        battle.getSubmittedAt(),
                        queries.isStale(battle, progressSeq, now)))
                .toList();
    }

    /**
     * «Бросить бой» или снять чужую забытую отметку — любой участник кампании. Меняются только предупреждения:
     * итог такого боя по-прежнему можно отправить.
     */
    @Transactional
    public void abandon(PrimalPrincipal principal, long campaignId, UUID battleId) {
        access.require(principal, campaignId);
        CampaignBattle battle = battles.findByIdAndCampaignId(battleId, campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Бой не найден."));
        battle.abandon();
        changes.battlesChanged(campaignId);
    }

    private StartedBattle started(CampaignBattle battle, BattleContext campaign) {
        List<ActiveBattle> others = queries.active(campaign.campaignId(), campaign.progressSeq()).stream()
                .filter(active -> !active.id().equals(battle.getId()))
                .toList();
        return new StartedBattle(battle.getId(), battle.getStatus(), others);
    }

    /** Идентификатор боя другой кампании не раскрывается. */
    private static CampaignBattle own(CampaignBattle battle, long campaignId) {
        if (battle.getCampaignId() != campaignId) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Бой не найден.");
        }
        return battle;
    }
}
