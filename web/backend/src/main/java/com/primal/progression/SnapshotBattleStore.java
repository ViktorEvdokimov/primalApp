package com.primal.progression;

import com.primal.campaign.CampaignSnapshotDto.SnapshotBattle;
import com.primal.campaign.SnapshotBattles;
import com.primal.catalog.CatalogService;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.CampaignBattle.DefeatReason;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.progression.CampaignBattle.Result;
import com.primal.progression.CampaignBattle.Status;
import com.primal.realtime.ChangeEvents;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Бои кампании в снимке мобильного приложения ({@code doc/api.md} §11). Приложение ведёт бой и считает итог само,
 * поэтому бои из снимка только дописываются к истории: сохранённый итог не меняется.
 */
@Service
class SnapshotBattleStore implements SnapshotBattles {

    private final CampaignBattleRepository battles;
    private final CampaignBattleQueries queries;
    private final CatalogService catalog;
    private final ChangeEvents changes;
    private final Clock clock;

    SnapshotBattleStore(CampaignBattleRepository battles, CampaignBattleQueries queries, CatalogService catalog,
                        ChangeEvents changes, Clock clock) {
        this.battles = battles;
        this.queries = queries;
        this.catalog = catalog;
        this.changes = changes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotBattle> list(long campaignId) {
        return battles.findByCampaignIdOrderByStartedAtDesc(campaignId).stream()
                .map(battle -> new SnapshotBattle(
                        battle.getId(),
                        battle.getPurpose().name(),
                        battle.getQuestNumber(),
                        battle.getBossCode(),
                        battle.getDifficulty(),
                        battle.getChapter(),
                        battle.getProgressSeq(),
                        battle.getStatus().name(),
                        battle.getResult() == null ? null : battle.getResult().name(),
                        battle.getDefeatReason() == null ? null : battle.getDefeatReason().name(),
                        battle.getRoundsPlayed(),
                        queries.actor(battle.getStartedByDeviceId()),
                        battle.getStartedAt(),
                        battle.getSubmittedAt() == null ? null : queries.actor(battle.getSubmittedByDeviceId()),
                        battle.getSubmittedAt(),
                        battle.getFinishedAt()))
                .toList();
    }

    @Override
    @Transactional
    public Set<UUID> merge(PrimalPrincipal principal, long campaignId, List<SnapshotBattle> snapshot) {
        Instant now = clock.instant();
        boolean changed = false;
        for (SnapshotBattle battle : snapshot) {
            if (battle == null || battle.id() == null) {
                continue;
            }
            Status status = parse(Status.class, battle.status(), "status");
            BattleReport report = report(battle, now);
            Optional<CampaignBattle> existing = battles.findById(battle.id());
            if (existing.isPresent() && existing.get().getCampaignId() != campaignId) {
                continue; // бой другой кампании не раскрывается и не меняется
            }
            CampaignBattle record = existing.orElse(null);
            if (record == null) {
                record = battles.save(new CampaignBattle(battle.id(), campaignId, report, principal.deviceId()));
                changed = true;
            }
            if (record.isFinished()) {
                continue;
            }
            if ((status == Status.APPLIED || status == Status.DISMISSED) && report.result() != null) {
                Instant submittedAt = battle.submittedAt() == null || battle.submittedAt().isAfter(now) ? now : battle.submittedAt();
                record.finish(status, report, principal.deviceId(), submittedAt, null);
                changed = true;
            } else if (status == Status.ABANDONED && record.getStatus() == Status.IN_PROGRESS) {
                record.abandon();
                changed = true;
            }
        }
        if (changed) {
            battles.flush();
            changes.battlesChanged(campaignId);
        }
        return battles.findByCampaignIdOrderByStartedAtDesc(campaignId).stream()
                .map(CampaignBattle::getId)
                .collect(Collectors.toSet());
    }

    /** Поля боя из снимка с проверкой по правилам таблицы {@code campaign_battle}. */
    private BattleReport report(SnapshotBattle battle, Instant now) {
        Purpose purpose = parse(Purpose.class, battle.purpose(), "purpose");
        Result result = battle.result() == null ? null : parse(Result.class, battle.result(), "result");
        DefeatReason reason = result != Result.DEFEAT ? null
                : battle.defeatReason() == null ? DefeatReason.ROUNDS : parse(DefeatReason.class, battle.defeatReason(), "defeatReason");
        require(purpose != Purpose.QUEST || (battle.questNumber() != null && catalog.quest(battle.questNumber()).isPresent()),
                "Нет задания " + battle.questNumber() + ".");
        require(battle.bossCode() == null || catalog.boss(battle.bossCode()).isPresent(),
                "Неизвестный босс: " + battle.bossCode() + ".");
        require(battle.difficulty() >= 0 && battle.difficulty() <= 3, "Уровень враждебности — от 0 до 3.");
        require(battle.chapter() >= 0 && battle.chapter() <= 11, "Глава — от 0 до 11.");
        require(battle.roundsPlayed() == null || (battle.roundsPlayed() >= 1 && battle.roundsPlayed() <= 10),
                "Раунды — от 1 до 10.");
        require(battle.startedAt() != null, "Нет времени начала боя.");
        Instant startedAt = battle.startedAt().isAfter(now) ? now : battle.startedAt();
        return new BattleReport(purpose, purpose == Purpose.QUEST ? battle.questNumber() : null, battle.bossCode(),
                battle.difficulty(), battle.chapter(), battle.progressSeq(), result, reason, battle.roundsPlayed(),
                startedAt, battle.finishedAt());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("Неверное значение " + field + " боя: " + value + ".");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message)
                .with("errors", List.of(Map.of("field", "battles", "message", message)));
    }
}
