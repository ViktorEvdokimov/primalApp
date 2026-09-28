import { Button, Card, Group, Stack, Text, Title } from '@mantine/core';
import type { QuestItem } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { BossLabel } from '../campaign/BossLabel';

interface QuestSelectProps {
  quests: QuestItem[];
  onSelect: (questNumber: number | null) => void;
}

/**
 * Выбор задания перед боем кампании (`QuestSelectDialog` app): открытые задания и «Продолжить без задания».
 * В прологе и главе 11 выбора нет — подготовка открывается сразу.
 */
export function QuestSelect({ quests, onSelect }: QuestSelectProps) {
  return (
    <Stack gap="md" data-testid="quest-select">
      <div>
        <Title order={3}>{ru.progression.selectTitle}</Title>
        <Text size="sm" c="dimmed">
          {ru.progression.selectHint}
        </Text>
      </div>
      {quests.length === 0 && (
        <Text c="dimmed" data-testid="quest-select-empty">
          {ru.progression.noOpenQuests}
        </Text>
      )}
      {quests.map((quest) => (
        <Card
          key={quest.number}
          withBorder
          padding="sm"
          component="button"
          onClick={() => onSelect(quest.number)}
          style={{ cursor: 'pointer', textAlign: 'left' }}
          data-testid="quest-select-item"
          data-number={quest.number}
        >
          <Group justify="space-between" wrap="nowrap">
            <Text fw={600}>{ru.progression.quest(quest.number, quest.name)}</Text>
            <BossLabel boss={quest.boss} />
          </Group>
        </Card>
      ))}
      <Button variant="light" onClick={() => onSelect(null)} data-testid="quest-select-free">
        {ru.progression.withoutQuest}
      </Button>
    </Stack>
  );
}
