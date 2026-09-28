import { Button, Card, Group, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useState } from 'react';
import { useCompleteQuest, useReopenQuest } from '../../api/generated/campaigns/campaigns';
import type { QuestItem, QuestLists } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { BossLabel } from './BossLabel';
import { QuestEditor } from './QuestEditor';
import type { SheetActions } from './useCampaignSheet';

interface QuestsTabProps {
  campaignId: number;
  chapter: number;
  quests: QuestLists;
  actions: SheetActions;
}

/**
 * Задания кампании: открытые с «Выполнено», выполненные серым с «Отмена», истёкшие серым; редактор открытых.
 * «Выполнено» открывает только задания из наград победы — без ресурсов и достижений (qa 70).
 */
export function QuestsTab({ campaignId, chapter, quests, actions }: QuestsTabProps) {
  const complete = useCompleteQuest();
  const reopen = useReopenQuest();
  const [editing, setEditing] = useState(false);

  const onComplete = (quest: QuestItem) => {
    complete.mutateAsync({ campaignId, number: quest.number }).then(
      (response) => {
        actions.changed((sheet) => ({
          ...sheet,
          quests: {
            ...sheet.quests,
            open: sheet.quests.open.filter((item) => item.number !== quest.number),
            completed: [...sheet.quests.completed, { ...quest, closedInChapter: chapter }],
          },
        }));
        notifications.show({ color: 'teal', message: ru.sheet.questCompleted(quest.number, response.opened) });
      },
      (error: unknown) => actions.failed(error),
    );
  };

  const onReopen = (quest: QuestItem) => {
    reopen.mutateAsync({ campaignId, number: quest.number }).then(
      (lists) => actions.applied((sheet) => ({ ...sheet, quests: lists })),
      (error: unknown) => actions.failed(error),
    );
  };

  const busy = (number: number) =>
    (complete.isPending && complete.variables.number === number) || (reopen.isPending && reopen.variables.number === number);

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Text fw={600}>{ru.sheet.openQuests}</Text>
        <Button size="xs" variant="light" onClick={() => setEditing(true)} data-testid="quests-edit">
          {ru.sheet.editQuests}
        </Button>
      </Group>

      {quests.open.length === 0 && (
        <Text c="dimmed" data-testid="quests-empty">
          {ru.sheet.noOpenQuests}
        </Text>
      )}
      {quests.open.map((quest) => (
        <Card key={quest.number} withBorder padding="sm" data-testid="quest-open" data-number={quest.number}>
          <Group justify="space-between" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={600}>{`${quest.number}. ${quest.name}`}</Text>
              <BossLabel boss={quest.boss} />
            </Stack>
            <Button size="xs" loading={busy(quest.number)} onClick={() => onComplete(quest)} data-testid="quest-complete">
              {ru.sheet.complete}
            </Button>
          </Group>
        </Card>
      ))}

      {quests.completed.length > 0 && (
        <Stack gap={4} data-testid="quests-completed">
          <Text c="dimmed">{ru.sheet.completedQuests}</Text>
          {quests.completed.map((quest) => (
            <Group key={quest.number} justify="space-between" wrap="nowrap" data-testid="quest-completed" data-number={quest.number}>
              <Text c="dimmed" size="sm">{`${quest.number}. ${quest.name}`}</Text>
              <Button
                size="compact-xs"
                variant="subtle"
                color="gray"
                loading={busy(quest.number)}
                onClick={() => onReopen(quest)}
                data-testid="quest-reopen"
              >
                {ru.sheet.reopen}
              </Button>
            </Group>
          ))}
        </Stack>
      )}

      {quests.expired.length > 0 && (
        <Stack gap={4} data-testid="quests-expired">
          <Text c="dimmed">{ru.sheet.expiredQuests}</Text>
          {quests.expired.map((quest) => (
            <Text key={quest.number} c="dimmed" size="sm" data-testid="quest-expired" data-number={quest.number}>
              {`${quest.number}. ${quest.name}`}
            </Text>
          ))}
        </Stack>
      )}

      <QuestEditor campaignId={campaignId} quests={quests} opened={editing} onClose={() => setEditing(false)} actions={actions} />
    </Stack>
  );
}
