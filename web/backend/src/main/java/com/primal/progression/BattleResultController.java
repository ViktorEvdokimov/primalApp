package com.primal.progression;

import com.primal.common.api.ApiNullable;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.BattleResultDto.ResultApplied;
import com.primal.progression.BattleResultDto.ResultPreview;
import com.primal.progression.BattleResultService.Action;
import com.primal.progression.BattleResultService.Overrides;
import com.primal.progression.CampaignBattle.DefeatReason;
import com.primal.progression.CampaignBattle.Result;
import com.primal.rules.model.ResourceCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Результат боя кампании: превью наград, «Принять», «Отклонить» ({@code doc/api.md} §7). */
@Tag(name = "battles", description = "Бои кампании")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/battles/{battleId}")
class BattleResultController {

    /** Награды, исправленные через «Редактировать»: применяются ровно они. */
    record OverridesRequest(
            @ApiNullable @Size(max = 32) String bossCode,
            @NotNull Map<ResourceCode, @NotNull @Min(0) @Max(99) Integer> perHunter,
            @NotNull List<@NotNull Integer> openQuests,
            @NotNull List<@NotNull String> achievements) {
    }

    /**
     * Результат боя: поля старта из подготовки (на случай, если отметка о начале не дошла) и итог. Для превью
     * {@code action} и {@code overrides} не нужны; для итога {@code action} обязателен.
     */
    record BattleResultRequest(
            @ApiNullable Integer questNumber,
            @ApiNullable @Size(max = 32) String bossCode,
            @NotNull @Min(0) @Max(3) Integer difficulty,
            @NotNull @Min(0) @Max(11) Integer chapter,
            @NotNull @Min(0) Integer progressSeq,
            @NotNull Result result,
            @ApiNullable DefeatReason defeatReason,
            @ApiNullable @Min(1) @Max(10) Integer roundsPlayed,
            @NotNull Instant startedAt,
            @NotNull Instant finishedAt,
            @ApiNullable Action action,
            @ApiNullable @Valid OverridesRequest overrides,
            /* кому выдать карты наград: id охотника на каждую карту из превью по порядку */
            @ApiNullable @Size(max = 16) List<@NotNull Long> rewardCardHolders) {

        BattleReport report() {
            if (result == Result.DEFEAT && defeatReason == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "У поражения нужна причина: ROUNDS или SURRENDER.");
            }
            return new BattleReport(BattleReport.purposeOf(questNumber, chapter), questNumber, bossCode, difficulty,
                    chapter, progressSeq, result, defeatReason, roundsPlayed, startedAt, finishedAt);
        }
    }

    private final BattleResultService results;

    BattleResultController(BattleResultService results) {
        this.results = results;
    }

    @Operation(operationId = "previewBattleResult")
    @PostMapping("/result/preview")
    ResultPreview preview(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                          @PathVariable UUID battleId, @Valid @RequestBody BattleResultRequest body) {
        return results.preview(principal, campaignId, battleId, body.report());
    }

    @Operation(operationId = "submitBattleResult")
    @PostMapping("/result")
    ResultApplied submit(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                         @PathVariable UUID battleId, @Valid @RequestBody BattleResultRequest body) {
        if (body.action() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Укажите action: ACCEPT или DISMISS.");
        }
        OverridesRequest o = body.overrides();
        Overrides overrides = o == null ? null : new Overrides(o.bossCode(), o.perHunter(), o.openQuests(), o.achievements());
        return results.submit(principal, campaignId, battleId, body.report(), body.action(), overrides,
                body.rewardCardHolders());
    }
}
