import { Alert, Button, Group, Text } from '@mantine/core';
import { Link } from 'react-router';
import type { LocalBattle } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { hasPendingResult } from './campaignBattle';

/**
 * «Результат боя не отправлен» (D-13): бой кампании закончен в этом браузере, но итог ещё не на сервере —
 * в меню и на листе этой кампании. `campaignId` — показывать только для этой кампании.
 */
export function PendingResultBanner({ battle, campaignId }: { battle: LocalBattle | null; campaignId?: number }) {
  if (!hasPendingResult(battle) || (campaignId !== undefined && battle.campaign.id !== campaignId)) return null;
  return (
    <Alert color="yellow" data-testid="pending-result">
      <Group justify="space-between" gap="xs">
        <Text size="sm">
          {ru.progression.pendingResult} {battle.campaign.name}
        </Text>
        <Button
          size="xs"
          component={Link}
          to={`/campaigns/${battle.campaign.id}/outcome`}
          data-testid="pending-result-send"
        >
          {ru.progression.pendingResultAction}
        </Button>
      </Group>
    </Alert>
  );
}
