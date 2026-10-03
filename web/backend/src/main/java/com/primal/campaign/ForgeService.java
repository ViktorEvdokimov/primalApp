package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.catalog.CatalogService;
import com.primal.catalog.ForgeItemDef;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.ResourceCode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кузня ({@code doc/api.md} §5.4): охотник создаёт снаряжение с планшета открытой кузни текущего уровня и
 * тратит 1 стихию кузни и материи, указанные на планшете. Оружие — только своего класса. Преобразование
 * ресурсов (стихия вместо материи и т. п.) игроки делают сами: правят запас и создают снова.
 */
@Service
public class ForgeService {

    /** Созданное снаряжение и охотник после списания: ресурсы и инвентарь с новой картой. */
    public record Crafted(String code, String name, int level, HunterSheet hunter) {
    }

    private final CatalogService catalog;
    private final AccessService access;
    private final CampaignService campaigns;
    private final CampaignHunterRepository hunters;
    private final CampaignSheetService sheets;
    private final HunterService hunterService;
    private final InventoryService inventory;

    ForgeService(CatalogService catalog, AccessService access, CampaignService campaigns,
                 CampaignHunterRepository hunters, CampaignSheetService sheets, HunterService hunterService,
                 InventoryService inventory) {
        this.catalog = catalog;
        this.access = access;
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.sheets = sheets;
        this.hunterService = hunterService;
        this.inventory = inventory;
    }

    @Transactional
    public Crafted craft(PrimalPrincipal principal, long campaignId, long hunterId, String itemCode) {
        access.require(principal, campaignId);
        CampaignHunter hunter = hunters.findById(hunterId)
                .filter(candidate -> candidate.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Охотник не найден."));
        ForgeItemDef item = catalog.forgeItem(itemCode)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Снаряжения " + itemCode + " нет в кузне."));
        if (!sheets.openForges(campaignId).contains(item.element())) {
            throw unavailable("Кузня стихии «" + item.element().displayName()
                    + "» ещё не открыта: она откроется после победы над боссом этой стихии.");
        }
        if (!item.slot().availableTo(hunter.getHunterClass())) {
            throw unavailable("«" + item.name() + "» — оружие класса " + item.slot().hunterClass().displayName()
                    + ": его может создать только этот охотник.");
        }
        int level = campaigns.campaign(campaignId).getForgeLevel();
        Map<ResourceCode, Integer> cost = new EnumMap<>(ResourceCode.class);
        cost.put(ResourceCode.valueOf(item.element().name()), -1);
        item.cost(level).forEach((material, quantity) -> cost.merge(ResourceCode.valueOf(material.name()), -quantity, Integer::sum));
        hunterService.adjustResources(principal, campaignId, hunterId, cost);
        inventory.addCrafted(hunterId, HunterItem.Kind.EQUIPMENT, item.name(), level, item.element(), item.code());
        return new Crafted(item.code(), item.name(), level, sheets.hunterSheet(hunter));
    }

    private static ApiException unavailable(String detail) {
        return new ApiException(ErrorCode.FORGE_UNAVAILABLE, detail);
    }
}
