package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.campaign.HunterItem.Kind;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.Element;
import com.primal.rules.model.ForgeSlot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Инвентарь охотников ({@code doc/api.md} §5.4): стартовый набор новой кампании, правка без оплаты, предметы
 * из кузни, лаборатории и карт наград. Любое изменение увеличивает версию кампании.
 */
@Service
public class InventoryService {

    /** Стартовое снаряжение (правила, «Начальный набор охотника»): основное оружие класса, шлем, доспех, «Алемор». */
    static final String BASIC_HELMET = "Основной шлем";
    static final String BASIC_ARMOR = "Основной доспех";
    static final String STARTING_POTION = "Алемор";
    static final String STARTING_POTION_SOURCE = "LAB_01";

    private final CampaignService campaigns;
    private final CampaignHunterRepository hunters;
    private final HunterItemRepository items;
    private final AccessService access;
    private final CampaignSheetService sheets;
    private final Clock clock;

    InventoryService(CampaignService campaigns, CampaignHunterRepository hunters, HunterItemRepository items,
                     AccessService access, CampaignSheetService sheets, Clock clock) {
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.items = items;
        this.access = access;
        this.sheets = sheets;
        this.clock = clock;
    }

    /** Стартовый набор охотника новой кампании: 3 карты снаряжения 1-го уровня и зелье «Алемор». */
    static List<HunterItem> startingKit(CampaignHunter hunter, Instant now) {
        long id = hunter.getId();
        return List.of(
                new HunterItem(id, Kind.EQUIPMENT, ForgeSlot.weaponOf(hunter.getHunterClass()).displayName(), 1, null, null, now),
                new HunterItem(id, Kind.EQUIPMENT, BASIC_HELMET, 1, null, null, now),
                new HunterItem(id, Kind.EQUIPMENT, BASIC_ARMOR, 1, null, null, now),
                new HunterItem(id, Kind.POTION, STARTING_POTION, 1, null, STARTING_POTION_SOURCE, now));
    }

    /** Карта из кузни или лаборатории — в той же транзакции, что и списание ресурсов. */
    void addCrafted(long hunterId, Kind kind, String name, int level, Element element, String source) {
        items.save(new HunterItem(hunterId, kind, name, level, element, source, clock.instant()));
    }

    /**
     * Карты наград принятой победы: карта {@code cards[i]} — охотнику {@code holders[i]}. Держатель не указан
     * (итог отправлен без выбора) — первому охотнику отряда.
     */
    @Transactional
    public void giveRewardCards(long campaignId, List<String> cards, List<Long> holders) {
        List<CampaignHunter> squad = hunters.findByCampaignIdOrderByPosition(campaignId);
        Instant now = clock.instant();
        for (int index = 0; index < cards.size(); index++) {
            Long holder = holders != null && index < holders.size() ? holders.get(index) : null;
            long hunterId = squad.stream().map(CampaignHunter::getId).filter(id -> id.equals(holder)).findFirst()
                    .orElse(squad.getFirst().getId());
            items.save(new HunterItem(hunterId, Kind.REWARD, rewardName(cards.get(index)), null, null, cards.get(index), now));
        }
        items.flush();
    }

    static String rewardName(String card) {
        return "Карта награды №" + card;
    }

    /** Предмет, добавленный вручную, — без оплаты. */
    @Transactional
    public HunterSheet add(PrimalPrincipal principal, long campaignId, long hunterId, Kind kind, String name, Integer level) {
        CampaignHunter hunter = hunter(principal, campaignId, hunterId);
        items.save(new HunterItem(hunter.getId(), kind, name.strip(), level(kind, level), null, null, clock.instant()));
        touch(campaignId);
        return sheets.hunterSheet(hunter);
    }

    /** Новое название и уровень; стихия и происхождение остаются. */
    @Transactional
    public HunterSheet edit(PrimalPrincipal principal, long campaignId, long hunterId, long itemId, String name, Integer level) {
        HunterItem item = item(principal, campaignId, hunterId, itemId);
        item.edit(name.strip(), level(item.getKind(), level));
        touch(campaignId);
        return sheets.hunterSheet(hunters.getReferenceById(hunterId));
    }

    @Transactional
    public HunterSheet remove(PrimalPrincipal principal, long campaignId, long hunterId, long itemId) {
        items.delete(item(principal, campaignId, hunterId, itemId));
        touch(campaignId);
        return sheets.hunterSheet(hunters.getReferenceById(hunterId));
    }

    /** Карта охотника кампании — для правки, удаления и продажи; чужая или удалённая — 404. */
    HunterItem item(PrimalPrincipal principal, long campaignId, long hunterId, long itemId) {
        hunter(principal, campaignId, hunterId);
        return items.findById(itemId)
                .filter(item -> item.getHunterId() == hunterId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Предмет не найден."));
    }

    void delete(HunterItem item) {
        items.delete(item);
        items.flush();
    }

    private CampaignHunter hunter(PrimalPrincipal principal, long campaignId, long hunterId) {
        access.require(principal, campaignId);
        return hunters.findById(hunterId)
                .filter(hunter -> hunter.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Охотник не найден."));
    }

    /** У снаряжения и зелья уровень 1–3 (по умолчанию 1), у карты награды уровня нет. */
    private static Integer level(Kind kind, Integer level) {
        if (kind == Kind.REWARD) {
            return null;
        }
        if (level == null) {
            return 1;
        }
        if (level < 1 || level > 3) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                    .with("errors", List.of(Map.of("field", "level", "message", "Уровень — от 1 до 3")));
        }
        return level;
    }

    private void touch(long campaignId) {
        items.flush();
        campaigns.touch(campaigns.campaign(campaignId));
    }
}
