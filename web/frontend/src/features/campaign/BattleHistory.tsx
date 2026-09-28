import { Alert, Badge, Button, Card, Group, Loader, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { abandonBattle, useListBattles } from '../../api/generated/battles/battles';
import { isCampaignQuery } from '../../api/useLiveUpdates';
import type { BattleView } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { clockTime } from '../progression/campaignBattle';
import { BossLabel } from './BossLabel';
import { errorMessage } from './useCampaignSheet';

const STATUS_COLORS: Record<BattleView['status'], string> = {
  IN_PROGRESS: 'blue',
  APPLIED: 'teal',
  DISMISSED: 'gray',
  ABANDONED: 'gray',
};

function day(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

/**
 * История боёв кампании (`api.md` §6.3): чем кончился бой, задание, глава, кто начал и кто отправил итог.
 * Идущий бой можно пометить брошенным («Снять отметку») — например, забытую отметку: это меняет только
 * предупреждения, итог такого боя по-прежнему можно отправить.
 */
export function BattleHistory({ campaignId }: { campaignId: number }) {
  const queryClient = useQueryClient();
  const battles = useListBattles(campaignId, undefined, { query: { retry: false } });
  const [abandoning, setAbandoning] = useState<string | null>(null);

  const abandon = async (battleId: string) => {
    setAbandoning(battleId);
    try {
      await abandonBattle(campaignId, battleId);
      // Баннер «Идёт бой» и история — запросы кампании
      await queryClient.invalidateQueries({ predicate: (query) => isCampaignQuery(query, campaignId) });
    } catch (error) {
      notifications.show({ color: 'red', message: errorMessage(error) });
    } finally {
      setAbandoning(null);
    }
  };

  if (battles.isPending) return <Loader size="sm" />;
  if (battles.isError) return <Alert color="red">{ru.history.loadError}</Alert>;
  if (battles.data.length === 0) {
    return (
      <Text c="dimmed" data-testid="history-empty">
        {ru.history.empty}
      </Text>
    );
  }
  return (
    <Stack gap="xs">
      {battles.data.map((battle) => (
        <Card key={battle.id} withBorder padding="sm" data-testid="history-item" data-status={battle.status} data-result={battle.result ?? ''}>
          <Stack gap={4}>
            <Group justify="space-between" wrap="nowrap">
              <Text fw={600}>
                {battle.result === null ? ru.history.status.IN_PROGRESS : ru.history.result[battle.result]}
                {' · '}
                {battle.questNumber !== null
                  ? ru.progression.questShort(battle.questNumber)
                  : ru.progression.purpose[battle.purpose === 'QUEST' ? 'FREE' : battle.purpose]}
                {' · '}
                {ru.campaigns.chapter(battle.chapter)}
              </Text>
              <Badge color={STATUS_COLORS[battle.status]} variant="light" style={{ textTransform: 'none' }}>
                {ru.history.status[battle.status]}
              </Badge>
            </Group>
            {battle.boss !== null && <BossLabel boss={battle.boss} />}
            <Text size="xs" c="dimmed">
              {ru.history.started(battle.startedBy.name, `${day(battle.startedAt)} ${clockTime(battle.startedAt)}`)}
            </Text>
            {battle.submittedBy !== null && battle.submittedAt !== null && (
              <Text size="xs" c="dimmed">
                {ru.history.submitted(battle.submittedBy.name, `${day(battle.submittedAt)} ${clockTime(battle.submittedAt)}`)}
              </Text>
            )}
            {battle.status === 'IN_PROGRESS' && (
              <Group justify="flex-end">
                <Button
                  size="compact-xs"
                  variant="subtle"
                  color="gray"
                  loading={abandoning === battle.id}
                  onClick={() => void abandon(battle.id)}
                  data-testid="history-abandon"
                >
                  {ru.history.abandon}
                </Button>
              </Group>
            )}
          </Stack>
        </Card>
      ))}
    </Stack>
  );
}
