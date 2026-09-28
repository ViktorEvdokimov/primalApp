package com.primal.progression;

import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.campaign.CampaignSheetDto.Actor;
import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.catalog.CatalogDtos.Stance;
import com.primal.common.api.ApiNullable;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.progression.CampaignBattle.Result;
import com.primal.progression.CampaignBattle.Status;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ответы API боёв кампании ({@code doc/api.md} §6). */
public final class BattleDto {

    private BattleDto() {
    }

    /**
     * Подготовка к бою кампании: снимок главы и {@code progressSeq} браузер сохраняет в бою и отправляет с
     * результатом.
     *
     * @param boss       босс задания, пролога или финала; {@code null} — бой без задания, босса выбирает игрок
     * @param stances    стойки {@code boss} на уровне {@code difficulty}
     * @param forcedBoss босс без выбора (пролог, финальный бой): задание и босса выбрать нельзя
     */
    public record BattleSetup(
            long campaignId,
            int chapter,
            int progressSeq,
            Purpose purpose,
            int difficulty,
            int hunterCount,
            List<QuestItem> openQuests,
            @ApiNullable CampaignBoss boss,
            List<Stance> stances,
            @ApiNullable CampaignBoss forcedBoss,
            List<ActiveBattle> activeBattles) {
    }

    /** Отметка о начале боя: {@code otherActiveBattles} — идущие бои других участников для предупреждения. */
    public record StartedBattle(UUID id, Status status, List<ActiveBattle> otherActiveBattles) {
    }

    /** Бой в истории кампании; {@code stale} — отметка устарела и не предупреждает ({@code api.md} §6.3). */
    public record BattleView(
            UUID id,
            Status status,
            Purpose purpose,
            @ApiNullable Integer questNumber,
            @ApiNullable CampaignBoss boss,
            int difficulty,
            int chapter,
            @ApiNullable Result result,
            @ApiNullable Integer roundsPlayed,
            Actor startedBy,
            Instant startedAt,
            @ApiNullable Actor submittedBy,
            @ApiNullable Instant submittedAt,
            boolean stale) {
    }
}
