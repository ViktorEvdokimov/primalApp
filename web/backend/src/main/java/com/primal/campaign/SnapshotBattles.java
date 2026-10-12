package com.primal.campaign;

import com.primal.campaign.CampaignSnapshotDto.SnapshotBattle;
import com.primal.identity.PrimalPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Бои кампании в снимке мобильного приложения ({@code doc/api.md} §11). Реализует модуль {@code progression}, где
 * хранятся бои: так {@code campaign} не зависит от {@code progression} (как {@link CampaignBattles}).
 */
public interface SnapshotBattles {

    /** Все бои кампании, новые сначала. */
    List<SnapshotBattle> list(long campaignId);

    /**
     * Бои из снимка дописываются к истории: новые создаются от имени устройства запроса, у идущих сохраняется
     * итог из приложения, сохранённые итоги не меняются. Бои, которых нет в снимке, остаются как есть.
     *
     * @return идентификаторы боёв кампании после записи — на них могут ссылаться трофеи
     */
    Set<UUID> merge(PrimalPrincipal principal, long campaignId, List<SnapshotBattle> battles);
}
