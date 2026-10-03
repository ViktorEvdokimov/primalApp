package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.common.api.ApiNullable;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.ResourceCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

/**
 * Инвентарь охотника и обмен ресурсов ({@code doc/api.md} §5.4): правка без оплаты, продажа карты,
 * преобразование и обмен между охотниками по правилам.
 */
@Tag(name = "campaigns", description = "Кампании")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}")
class InventoryController {

    record ItemRequest(@NotNull(message = "Укажите вид") HunterItem.Kind kind,
                       @NotBlank(message = "Укажите название") @Size(max = 100, message = "Не длиннее 100 символов") String name,
                       @ApiNullable @Min(value = 1, message = "Уровень — от 1 до 3") @Max(value = 3, message = "Уровень — от 1 до 3")
                       Integer level) {
    }

    record ItemChangeRequest(
            @NotBlank(message = "Укажите название") @Size(max = 100, message = "Не длиннее 100 символов") String name,
            @ApiNullable @Min(value = 1, message = "Уровень — от 1 до 3") @Max(value = 3, message = "Уровень — от 1 до 3")
            Integer level) {
    }

    /** {@code gain} — любая материя или стихия кузни сбрасываемой карты. */
    record SellRequest(@NotNull(message = "Укажите, что получить") ResourceCode gain) {
    }

    /** {@code spend} — 1 стихия или 2 материи; {@code gain} — материя. */
    record ConvertRequest(@NotNull @Size(min = 1, max = 2, message = "Потратьте 1 стихию или 2 материи")
                          List<@NotNull ResourceCode> spend,
                          @NotNull(message = "Укажите материю") ResourceCode gain) {
    }

    /** Охотник {@code fromHunterId} отдаёт {@code give} и получает {@code receive} от {@code toHunterId}. */
    record ExchangeRequest(@NotNull Long fromHunterId, @NotNull Long toHunterId,
                           @NotNull Map<ResourceCode, @NotNull @Min(0) @Max(99) Integer> give,
                           @NotNull Map<ResourceCode, @NotNull @Min(0) @Max(99) Integer> receive) {
    }

    /** Оба охотника после обмена: отдающий и получающий. */
    record ExchangeResponse(List<HunterSheet> hunters) {
    }

    private final InventoryService inventory;
    private final ExchangeService exchange;

    InventoryController(InventoryService inventory, ExchangeService exchange) {
        this.inventory = inventory;
        this.exchange = exchange;
    }

    @Operation(operationId = "addItem")
    @ApiResponse(responseCode = "201", description = "Предмет добавлен")
    @PostMapping("/hunters/{hunterId}/items")
    ResponseEntity<HunterSheet> add(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                    @PathVariable long hunterId, @Valid @RequestBody ItemRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventory.add(principal, campaignId, hunterId, body.kind(), body.name(), body.level()));
    }

    @Operation(operationId = "editItem")
    @PatchMapping("/hunters/{hunterId}/items/{itemId}")
    HunterSheet edit(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                     @PathVariable long hunterId, @PathVariable long itemId, @Valid @RequestBody ItemChangeRequest body) {
        return inventory.edit(principal, campaignId, hunterId, itemId, body.name(), body.level());
    }

    @Operation(operationId = "removeItem")
    @DeleteMapping("/hunters/{hunterId}/items/{itemId}")
    HunterSheet remove(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                       @PathVariable long hunterId, @PathVariable long itemId) {
        return inventory.remove(principal, campaignId, hunterId, itemId);
    }

    @Operation(operationId = "sellItem")
    @PostMapping("/hunters/{hunterId}/items/{itemId}/sell")
    HunterSheet sell(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                     @PathVariable long hunterId, @PathVariable long itemId, @Valid @RequestBody SellRequest body) {
        return exchange.sell(principal, campaignId, hunterId, itemId, body.gain());
    }

    @Operation(operationId = "convertResources")
    @PostMapping("/hunters/{hunterId}/convert")
    HunterSheet convert(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                        @PathVariable long hunterId, @Valid @RequestBody ConvertRequest body) {
        return exchange.convert(principal, campaignId, hunterId, body.spend(), body.gain());
    }

    @Operation(operationId = "exchangeResources")
    @PostMapping("/exchange")
    ExchangeResponse exchange(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                              @Valid @RequestBody ExchangeRequest body) {
        return new ExchangeResponse(exchange.exchange(principal, campaignId, body.fromHunterId(), body.toHunterId(),
                body.give(), body.receive()));
    }
}
