import { ActionIcon, Autocomplete, Badge, Button, Group, Stack, Text } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useAddAchievement, useDeleteAchievement } from '../../api/generated/campaigns/campaigns';
import { useAchievements } from '../../api/generated/catalog/catalog';
import type { AchievementItem } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import type { SheetActions } from './useCampaignSheet';

interface AchievementsTabProps {
  campaignId: number;
  achievements: AchievementItem[];
  actions: SheetActions;
}

/**
 * Достижения кампании: список и правка вручную. Название подсказывается из `/catalog/achievements`,
 * сервер сам приводит написание к каталогу («голос  волтьяра» → «Голос Волтьяра»), своё достижение
 * хранится как введено (doc/api.md §5.6).
 */
export function AchievementsTab({ campaignId, achievements, actions }: AchievementsTabProps) {
  const catalog = useAchievements({ query: { staleTime: 60 * 60 * 1000 } });
  const add = useAddAchievement();
  const remove = useDeleteAchievement();
  const [name, setName] = useState('');
  const owned = new Set(achievements.map((item) => item.name));
  const suggestions = [...new Set((catalog.data ?? []).map((item) => item.name))].filter((item) => !owned.has(item));

  const submit = (event?: FormEvent) => {
    event?.preventDefault();
    const trimmed = name.trim();
    if (trimmed === '' || add.isPending) return;
    add.mutateAsync({ campaignId, data: { name: trimmed } }).then(
      (created) => {
        setName('');
        actions.changed((sheet) =>
          sheet.achievements.some((item) => item.id === created.id)
            ? sheet
            : { ...sheet, achievements: [...sheet.achievements, created] },
        );
      },
      (error: unknown) => actions.failed(error),
    );
  };

  const onRemove = (achievement: AchievementItem) => {
    remove.mutateAsync({ campaignId, achievementId: achievement.id }).then(
      () =>
        actions.applied((sheet) => ({
          ...sheet,
          achievements: sheet.achievements.filter((item) => item.id !== achievement.id),
        })),
      (error: unknown) => actions.failed(error),
    );
  };

  return (
    <Stack gap="md">
      {achievements.length === 0 && (
        <Text c="dimmed" data-testid="achievements-empty">
          {ru.sheet.noAchievements}
        </Text>
      )}
      <Stack gap={6}>
        {achievements.map((achievement) => (
          <Group key={achievement.id} justify="space-between" wrap="nowrap" data-testid="achievement" data-name={achievement.name}>
            <Group gap="xs" wrap="nowrap">
              <Text c="teal">{achievement.name}</Text>
              {achievement.code === null && (
                <Badge size="xs" variant="light" color="gray">
                  {ru.sheet.customAchievement}
                </Badge>
              )}
            </Group>
            <ActionIcon
              variant="subtle"
              color="red"
              aria-label={`${ru.sheet.removeAchievement}: ${achievement.name}`}
              loading={remove.isPending && remove.variables.achievementId === achievement.id}
              onClick={() => onRemove(achievement)}
              data-testid="achievement-remove"
            >
              ×
            </ActionIcon>
          </Group>
        ))}
      </Stack>
      <form onSubmit={submit}>
        <Group gap="xs" wrap="nowrap" align="flex-end">
          <Autocomplete
            style={{ flex: 1 }}
            placeholder={ru.sheet.achievementPlaceholder}
            aria-label={ru.sheet.achievementPlaceholder}
            data={suggestions}
            value={name}
            onChange={setName}
            maxLength={100}
            limit={10}
            data-testid="achievement-input"
          />
          <Button type="submit" loading={add.isPending} disabled={name.trim() === ''} data-testid="achievement-add">
            {ru.sheet.addAchievement}
          </Button>
        </Group>
      </form>
    </Stack>
  );
}
