package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.catalog.BossDef;
import com.primal.catalog.CatalogService;
import com.primal.catalog.QuestDef;
import com.primal.rules.model.Element;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Боссы и задания каталога в том виде, в каком их показывает лист кампании. */
@Component
public class CampaignViews {

    private final CatalogService catalog;

    CampaignViews(CatalogService catalog) {
        this.catalog = catalog;
    }

    public CampaignBoss boss(String code) {
        BossDef def = catalog.boss(code).orElseThrow();
        return new CampaignBoss(def.code(), def.name(), def.element() == null ? null : def.element().name());
    }

    /** Стихия босса; у Пробуждённого стихии нет. */
    public Optional<Element> bossElement(String code) {
        return catalog.boss(code).map(BossDef::element);
    }

    /** Задание каталога; {@code closedInChapter} — у выполненного или истёкшего задания кампании. */
    public QuestItem quest(int number, Integer closedInChapter) {
        QuestDef def = catalog.quest(number).orElseThrow();
        return new QuestItem(def.number(), def.name(), boss(def.bossCode()), closedInChapter);
    }
}
