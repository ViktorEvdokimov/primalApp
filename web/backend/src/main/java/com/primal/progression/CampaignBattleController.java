package com.primal.progression;

import com.primal.common.api.ApiNullable;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.BattleDto.BattleSetup;
import com.primal.progression.BattleDto.BattleView;
import com.primal.progression.BattleDto.StartedBattle;
import com.primal.progression.CampaignBattleService.StartCommand;
import com.primal.progression.CampaignBattleService.Started;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Бой кампании: подготовка, отметки о начале, идущие бои и история ({@code doc/api.md} §6). */
@Tag(name = "battles", description = "Бои кампании")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}")
class CampaignBattleController {

    /**
     * Отметка о начале боя: {@code id} создаёт браузер; {@code questNumber} — {@code null} в прологе, финале и
     * бою без задания; {@code bossCode} — {@code null}, если параметры босса введены вручную.
     */
    record StartBattleRequest(
            @NotNull(message = "Нет идентификатора боя") UUID id,
            @ApiNullable Integer questNumber,
            @ApiNullable @Size(max = 32) String bossCode,
            @NotNull @Min(0) @Max(3) Integer difficulty,
            @NotNull @Min(0) @Max(11) Integer chapter,
            @NotNull @Min(0) Integer progressSeq,
            @NotNull Instant startedAt) {
    }

    private final BattleSetupService setup;
    private final CampaignBattleService battles;

    CampaignBattleController(BattleSetupService setup, CampaignBattleService battles) {
        this.setup = setup;
        this.battles = battles;
    }

    @Operation(operationId = "getBattleSetup")
    @GetMapping("/battle-setup")
    BattleSetup setup(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                      @RequestParam(required = false) Integer questNumber) {
        return setup.setup(principal, campaignId, questNumber);
    }

    @Operation(operationId = "startBattle")
    @ApiResponse(responseCode = "201", description = "Отметка о начале боя создана")
    @ApiResponse(responseCode = "200", description = "Повтор с тем же id: отметка уже есть")
    @PostMapping("/battles")
    ResponseEntity<StartedBattle> start(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                        @Valid @RequestBody StartBattleRequest body) {
        Started started = battles.start(principal, campaignId, new StartCommand(body.id(), body.questNumber(),
                body.bossCode(), body.difficulty(), body.chapter(), body.progressSeq(), body.startedAt()));
        if (!started.created()) {
            return ResponseEntity.ok(started.battle());
        }
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaignId + "/battles/" + body.id()))
                .body(started.battle());
    }

    @Operation(operationId = "listBattles")
    @GetMapping("/battles")
    List<BattleView> list(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                          @RequestParam(required = false) CampaignBattle.Status status) {
        return battles.list(principal, campaignId, status);
    }

    @Operation(operationId = "abandonBattle")
    @ApiResponse(responseCode = "204", description = "Отметка снята: бой брошен")
    @DeleteMapping("/battles/{battleId}")
    ResponseEntity<Void> abandon(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                 @PathVariable UUID battleId) {
        battles.abandon(principal, campaignId, battleId);
        return ResponseEntity.noContent().build();
    }
}
