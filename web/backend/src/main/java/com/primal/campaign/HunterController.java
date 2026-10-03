package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.common.api.ApiOptional;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.Plant;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.SkillBranch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Охотники кампании: имя игрока, навыки, ресурсы ({@code doc/api.md} §5.4). */
@Tag(name = "campaigns", description = "Кампании")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/hunters/{hunterId}")
class HunterController {

    record RenameHunterRequest(
            @ApiOptional @Size(max = 60, message = "Имя игрока — не длиннее 60 символов") String playerName) {
    }

    record SkillRequest(@NotNull(message = "Укажите ветвь") SkillBranch branch,
                        @Min(value = 1, message = "Ступень — 1 или 2") @Max(value = 2, message = "Ступень — 1 или 2") int tier) {
    }

    record AdjustResourcesRequest(
            @NotEmpty(message = "Нет изменений")
            Map<ResourceCode, @NotNull @Min(value = -1000, message = "Слишком большое изменение")
                    @Max(value = 1000, message = "Слишком большое изменение") Integer> changes) {
    }

    record ResourcesResponse(Map<String, Integer> resources) {
    }

    /** {@code item} — код предмета планшета кузни: {@code FIRE_01} (каталог {@code GET /catalog/forge}). */
    record CraftRequest(@NotBlank(message = "Укажите снаряжение") @Size(max = 16, message = "Неизвестное снаряжение")
                        String item) {
    }

    /** Созданное снаряжение — карту {@code level}-го уровня игрок берёт из коробки; охотник после списания. */
    record CraftResponse(String item, String name, int level, HunterSheet hunter) {
    }

    /** {@code potion} — код зелья ({@code LAB_02}); {@code plants} — потраченные растения по порядку цены. */
    record BrewRequest(@NotBlank(message = "Укажите зелье") @Size(max = 16, message = "Неизвестное зелье") String potion,
                       @NotNull(message = "Укажите растения") @Size(min = 1, max = 4, message = "Укажите растения")
                       List<@NotNull(message = "Укажите растение") Plant> plants) {
    }

    /** Приготовленное зелье — карту {@code level}-го уровня игрок берёт из коробки; охотник после списания. */
    record BrewResponse(String potion, String name, int level, HunterSheet hunter) {
    }

    private final HunterService hunters;
    private final ForgeService forge;
    private final LabService lab;

    HunterController(HunterService hunters, ForgeService forge, LabService lab) {
        this.hunters = hunters;
        this.forge = forge;
        this.lab = lab;
    }

    @Operation(operationId = "renameHunter")
    @PatchMapping
    HunterSheet rename(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                       @PathVariable long hunterId, @Valid @RequestBody RenameHunterRequest body) {
        return hunters.rename(principal, campaignId, hunterId, body.playerName());
    }

    @Operation(operationId = "unlockSkill")
    @ApiResponse(responseCode = "201", description = "Ступень открыта")
    @PostMapping("/skills")
    ResponseEntity<HunterSheet> unlockSkill(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                            @PathVariable long hunterId, @Valid @RequestBody SkillRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hunters.unlockSkill(principal, campaignId, hunterId, body.branch(), body.tier()));
    }

    @Operation(operationId = "lockSkill")
    @DeleteMapping("/skills/{branch}/{tier}")
    HunterSheet lockSkill(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                          @PathVariable long hunterId, @PathVariable SkillBranch branch, @PathVariable int tier) {
        return hunters.lockSkill(principal, campaignId, hunterId, branch, tier);
    }

    @Operation(operationId = "craftEquipment")
    @ApiResponse(responseCode = "201", description = "Снаряжение создано, ресурсы списаны")
    @PostMapping("/forge")
    ResponseEntity<CraftResponse> craft(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                        @PathVariable long hunterId, @Valid @RequestBody CraftRequest body) {
        ForgeService.Crafted crafted = forge.craft(principal, campaignId, hunterId, body.item());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CraftResponse(crafted.code(), crafted.name(), crafted.level(), crafted.hunter()));
    }

    @Operation(operationId = "brewPotion")
    @ApiResponse(responseCode = "201", description = "Зелье приготовлено, растения списаны")
    @PostMapping("/lab")
    ResponseEntity<BrewResponse> brew(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                      @PathVariable long hunterId, @Valid @RequestBody BrewRequest body) {
        LabService.Brewed brewed = lab.brew(principal, campaignId, hunterId, body.potion(), body.plants());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BrewResponse(brewed.code(), brewed.name(), brewed.level(), brewed.hunter()));
    }

    @Operation(operationId = "adjustResources")
    @PostMapping("/resources/adjust")
    ResourcesResponse adjustResources(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                      @PathVariable long hunterId, @Valid @RequestBody AdjustResourcesRequest body) {
        return new ResourcesResponse(hunters.adjustResources(principal, campaignId, hunterId, body.changes()));
    }
}
