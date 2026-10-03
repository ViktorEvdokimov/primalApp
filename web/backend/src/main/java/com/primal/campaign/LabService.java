package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.catalog.CatalogService;
import com.primal.catalog.LabPotionDef;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.Plant;
import com.primal.rules.model.ResourceCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Лаборатория ({@code doc/api.md} §5.4): охотник готовит зелье и тратит 2 растения с планшета. Если на планшете
 * растение на выбор («A/B») или любое, игрок указывает, какое тратит. Уровень карты зелья — уровень лаборатории.
 */
@Service
public class LabService {

    /** Приготовленное зелье и охотник после списания: ресурсы и инвентарь с новой картой. */
    public record Brewed(String code, String name, int level, HunterSheet hunter) {
    }

    private final CatalogService catalog;
    private final AccessService access;
    private final CampaignService campaigns;
    private final CampaignHunterRepository hunters;
    private final HunterService hunterService;
    private final InventoryService inventory;
    private final CampaignSheetService sheets;

    LabService(CatalogService catalog, AccessService access, CampaignService campaigns,
               CampaignHunterRepository hunters, HunterService hunterService, InventoryService inventory,
               CampaignSheetService sheets) {
        this.catalog = catalog;
        this.access = access;
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.hunterService = hunterService;
        this.inventory = inventory;
        this.sheets = sheets;
    }

    /** {@code plants} — растения по порядку единиц цены зелья: для «Имперума» — [ANTHEMON, NILLEA]. */
    @Transactional
    public Brewed brew(PrimalPrincipal principal, long campaignId, long hunterId, String potionCode, List<Plant> plants) {
        access.require(principal, campaignId);
        CampaignHunter hunter = hunters.findById(hunterId)
                .filter(candidate -> candidate.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Охотник не найден."));
        LabPotionDef potion = catalog.labPotion(potionCode)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Зелья " + potionCode + " нет в лаборатории."));
        if (plants.size() != potion.units().size()) {
            throw wrongPlants("Для «" + potion.name() + "» нужно " + potion.units().size() + " растения.");
        }
        Map<ResourceCode, Integer> cost = new EnumMap<>(ResourceCode.class);
        for (int index = 0; index < plants.size(); index++) {
            Plant plant = plants.get(index);
            if (!potion.accepts(index, plant)) {
                throw wrongPlants("«" + plant.displayName() + "» не подходит для «" + potion.name() + "».");
            }
            cost.merge(ResourceCode.valueOf(plant.name()), -1, Integer::sum);
        }
        int level = campaigns.campaign(campaignId).getLabLevel();
        hunterService.adjustResources(principal, campaignId, hunterId, cost);
        inventory.addCrafted(hunterId, HunterItem.Kind.POTION, potion.name(), level, null, potion.code());
        return new Brewed(potion.code(), potion.name(), level, sheets.hunterSheet(hunter));
    }

    private static ApiException wrongPlants(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", "plants", "message", message)));
    }
}
