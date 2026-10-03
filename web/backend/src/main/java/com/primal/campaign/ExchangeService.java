package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.campaign.HunterItem.Kind;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.ResourceKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обмен и преобразование ресурсов по правилам ({@code doc/api.md} §5.4, правила «Кузня» и «Лаборатория»):
 * <ul>
 *   <li>обмен — два охотника отдают друг другу ресурсы 1 к 1 внутри типа: стихии на стихии, материи на материи,
 *       растения на растения; отдавать просто так нельзя;</li>
 *   <li>преобразование — 1 стихия вместо 1 материи или 2 материи вместо 1;</li>
 *   <li>продажа — карта из колоды снаряжения вместо 1 материи или 1 стихии кузни этой карты.</li>
 * </ul>
 * Всё «всё или ничего»: при нехватке не меняется ничего ({@code 422 NOT_ENOUGH_RESOURCES}).
 */
@Service
public class ExchangeService {

    private final HunterService hunters;
    private final InventoryService inventory;
    private final CampaignSheetService sheets;
    private final CampaignHunterRepository squad;

    ExchangeService(HunterService hunters, InventoryService inventory, CampaignSheetService sheets,
                    CampaignHunterRepository squad) {
        this.hunters = hunters;
        this.inventory = inventory;
        this.sheets = sheets;
        this.squad = squad;
    }

    /** Охотник {@code fromId} отдаёт {@code give} и получает {@code receive} от {@code toId}. */
    @Transactional
    public List<HunterSheet> exchange(PrimalPrincipal principal, long campaignId, long fromId, long toId,
                                      Map<ResourceCode, Integer> give, Map<ResourceCode, Integer> receive) {
        if (fromId == toId) {
            throw invalid("toHunterId", "Обмен — между двумя разными охотниками.");
        }
        Map<ResourceKind, Integer> given = byKind(give);
        Map<ResourceKind, Integer> received = byKind(receive);
        if (given.isEmpty() && received.isEmpty()) {
            throw invalid("give", "Выберите, чем обменяться.");
        }
        if (!given.equals(received)) {
            throw invalid("give", "Обмен — 1 к 1 внутри типа: стихию на стихию, материю на материю, растение на растение.");
        }
        Map<ResourceCode, Integer> fromChanges = new EnumMap<>(ResourceCode.class);
        Map<ResourceCode, Integer> toChanges = new EnumMap<>(ResourceCode.class);
        give.forEach((code, quantity) -> {
            fromChanges.merge(code, -quantity, Integer::sum);
            toChanges.merge(code, quantity, Integer::sum);
        });
        receive.forEach((code, quantity) -> {
            toChanges.merge(code, -quantity, Integer::sum);
            fromChanges.merge(code, quantity, Integer::sum);
        });
        fromChanges.values().removeIf(quantity -> quantity == 0);
        toChanges.values().removeIf(quantity -> quantity == 0);
        if (!fromChanges.isEmpty()) {
            hunters.adjustResources(principal, campaignId, fromId, fromChanges);
        }
        if (!toChanges.isEmpty()) {
            hunters.adjustResources(principal, campaignId, toId, toChanges);
        }
        return List.of(sheets.hunterSheet(hunterOf(campaignId, fromId)), sheets.hunterSheet(hunterOf(campaignId, toId)));
    }

    /** {@code spend}: 1 стихия или 2 материи; {@code gain} — материя. */
    @Transactional
    public HunterSheet convert(PrimalPrincipal principal, long campaignId, long hunterId, List<ResourceCode> spend,
                               ResourceCode gain) {
        if (gain.kind() != ResourceKind.MATERIAL) {
            throw invalid("gain", "Преобразование даёт материю.");
        }
        boolean element = spend.size() == 1 && spend.getFirst().kind() == ResourceKind.ELEMENT;
        boolean materials = spend.size() == 2 && spend.stream().allMatch(code -> code.kind() == ResourceKind.MATERIAL);
        if (!element && !materials) {
            throw invalid("spend", "Преобразование: 1 стихия или 2 материи вместо 1 материи.");
        }
        Map<ResourceCode, Integer> changes = new EnumMap<>(ResourceCode.class);
        spend.forEach(code -> changes.merge(code, -1, Integer::sum));
        changes.merge(gain, 1, Integer::sum);
        changes.values().removeIf(quantity -> quantity == 0);
        hunters.adjustResources(principal, campaignId, hunterId, changes);
        return sheets.hunterSheet(hunterOf(campaignId, hunterId));
    }

    /** Карта снаряжения или награды сбрасывается: +1 любая материя или +1 стихия кузни этой карты. */
    @Transactional
    public HunterSheet sell(PrimalPrincipal principal, long campaignId, long hunterId, long itemId, ResourceCode gain) {
        HunterItem item = inventory.item(principal, campaignId, hunterId, itemId);
        if (item.getKind() == Kind.POTION) {
            throw invalid("item", "Зелье не сбрасывается вместо ресурса — только карта из колоды снаряжения.");
        }
        boolean material = gain.kind() == ResourceKind.MATERIAL;
        boolean ownElement = item.getElement() != null && gain.name().equals(item.getElement().name());
        if (!material && !ownElement) {
            throw invalid("gain", item.getElement() == null
                    ? "За «" + item.getName() + "» можно получить только материю."
                    : "За «" + item.getName() + "» можно получить материю или стихию «" + item.getElement().displayName() + "».");
        }
        inventory.delete(item);
        hunters.adjustResources(principal, campaignId, hunterId, Map.of(gain, 1));
        return sheets.hunterSheet(hunterOf(campaignId, hunterId));
    }

    private CampaignHunter hunterOf(long campaignId, long hunterId) {
        return squad.findById(hunterId)
                .filter(hunter -> hunter.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Охотник не найден."));
    }

    private static Map<ResourceKind, Integer> byKind(Map<ResourceCode, Integer> resources) {
        Map<ResourceKind, Integer> counts = new EnumMap<>(ResourceKind.class);
        resources.forEach((code, quantity) -> {
            if (quantity < 0) {
                throw invalid("give", "Количество не может быть отрицательным.");
            }
            if (quantity > 0) {
                counts.merge(code.kind(), quantity, Integer::sum);
            }
        });
        return counts;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
