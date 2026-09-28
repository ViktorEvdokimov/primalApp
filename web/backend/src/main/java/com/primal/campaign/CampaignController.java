package com.primal.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.primal.campaign.CampaignService.CampaignChanges;
import com.primal.campaign.CampaignService.NewHunter;
import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.campaign.CampaignSheetDto.CampaignSummary;
import com.primal.common.api.ApiOptional;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.HunterClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Кампании ({@code doc/api.md} §5.1–5.3). */
@Tag(name = "campaigns", description = "Кампании")
@RestController
@RequestMapping("/api/v1/campaigns")
class CampaignController {

    record HunterRequest(
            @NotNull(message = "Выберите класс") @JsonProperty("class") HunterClass hunterClass,
            @ApiOptional @Size(max = 60, message = "Имя игрока — не длиннее 60 символов") String playerName) {
    }

    record CreateCampaignRequest(
            @NotBlank(message = "Введите название кампании") @Size(max = 100, message = "Название — не длиннее 100 символов")
            String name,
            @NotNull(message = "Выберите охотников") List<@Valid @NotNull HunterRequest> hunters) {
    }

    /** Все поля, кроме версии, необязательны: переданное меняется, остальное остаётся. */
    record UpdateCampaignRequest(
            @NotNull(message = "Нет версии кампании") Integer expectedVersion,
            @ApiOptional @Size(max = 100, message = "Название — не длиннее 100 символов")
            @Pattern(regexp = ".*\\S.*", message = "Название не может быть пустым") String name,
            @ApiOptional @Size(max = 20_000, message = "Заметки — не длиннее 20 000 символов") String notes,
            @ApiOptional @Min(value = 0, message = "Глава — от 0 до 11") @Max(value = 11, message = "Глава — от 0 до 11")
            Integer chapter) {
    }

    private final CampaignService campaigns;

    CampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @Operation(operationId = "listCampaigns")
    @GetMapping
    List<CampaignSummary> list(@AuthenticationPrincipal PrimalPrincipal principal) {
        return campaigns.list(principal);
    }

    @Operation(operationId = "createCampaign")
    @ApiResponse(responseCode = "201", description = "Кампания создана: глава 0 «Пролог»")
    @PostMapping
    ResponseEntity<CampaignSheet> create(@AuthenticationPrincipal PrimalPrincipal principal,
                                         @Valid @RequestBody CreateCampaignRequest body) {
        CampaignSheet sheet = campaigns.create(principal, body.name(),
                body.hunters().stream().map(h -> new NewHunter(h.hunterClass(), h.playerName())).toList());
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + sheet.id())).body(sheet);
    }

    @Operation(operationId = "getCampaign")
    @GetMapping("/{id}")
    CampaignSheet get(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id) {
        return campaigns.sheet(principal, id);
    }

    @Operation(operationId = "updateCampaign")
    @PatchMapping("/{id}")
    CampaignSheet update(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id,
                         @Valid @RequestBody UpdateCampaignRequest body) {
        return campaigns.update(principal, id,
                new CampaignChanges(body.expectedVersion(), body.name(), body.notes(), body.chapter()));
    }

    @Operation(operationId = "deleteCampaign")
    @ApiResponse(responseCode = "204", description = "Кампания удалена")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id) {
        campaigns.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
