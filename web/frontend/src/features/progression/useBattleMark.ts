import { useMemo } from 'react';
import { abandonBattle, startBattle } from '../../api/generated/battles/battles';
import type { LocalBattle } from '../../domain/battle';
import { useActiveBattle } from '../battle/useActiveBattle';
import { isCampaignBattle, startRequest } from './campaignBattle';

/**
 * Отметки о начале боя кампании (`battle.md` §9): отметка отправляется без ожидания ответа — нет сети, бой
 * всё равно идёт (`startMarked = false`), запись появится с результатом. Брошенный бой снимает отметку.
 */
export function useBattleMark() {
  const active = useActiveBattle();
  const { update } = active;
  return useMemo(
    () => ({
      /** Отметка о начале: ошибка ничему не мешает. */
      mark(battle: LocalBattle) {
        if (!isCampaignBattle(battle)) return;
        startBattle(battle.campaign.id, startRequest(battle)).then(
          () =>
            update(battle.id, (current) =>
              current.campaign === null ? current : { ...current, campaign: { ...current.campaign, startMarked: true } },
            ),
          () => undefined,
        );
      },
      /** «Новый бой» или замена боя: отметка незаконченного боя кампании снимается. */
      abandon(battle: LocalBattle) {
        if (!isCampaignBattle(battle) || battle.submission?.status === 'SENT') return;
        abandonBattle(battle.campaign.id, battle.id).catch(() => undefined);
      },
    }),
    [update],
  );
}
