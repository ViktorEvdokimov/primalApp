package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.ActiveBattle;
import com.primal.campaign.CampaignSheetDto.RecentBattle;
import java.util.List;

/**
 * Бои кампании для листа. Реализует модуль {@code progression}, где хранятся отметки о начале и итоги боёв:
 * так {@code campaign} не зависит от {@code progression}.
 */
public interface CampaignBattles {

    /** Идущие бои для предупреждений: начаты недавно и при текущем {@code progressSeq} ({@code api.md} §6.3). */
    List<ActiveBattle> active(long campaignId, int progressSeq);

    /** Пять последних завершённых боёв, новые сначала; полный список — {@code GET /campaigns/{id}/battles}. */
    List<RecentBattle> recent(long campaignId);
}
