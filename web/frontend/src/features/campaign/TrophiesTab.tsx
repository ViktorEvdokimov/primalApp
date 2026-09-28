import { Group, Stack, Text } from '@mantine/core';
import type { TrophyItem } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { BossLabel } from './BossLabel';

/** Трофеи — побеждённые боссы и главы побед; появляются с отчётами о победе (этап 5). */
export function TrophiesTab({ trophies }: { trophies: TrophyItem[] }) {
  if (trophies.length === 0) {
    return (
      <Text c="dimmed" data-testid="trophies-empty">
        {ru.sheet.noTrophies}
      </Text>
    );
  }
  return (
    <Stack gap={6}>
      {trophies.map((trophy) => (
        <Group key={trophy.boss.code} justify="space-between" wrap="nowrap" data-testid="trophy" data-boss={trophy.boss.name}>
          <BossLabel boss={trophy.boss} />
          <Text size="sm" c="dimmed">
            {ru.sheet.trophyChapters(trophy.chapters.map((chapter) => ru.campaigns.chapter(chapter)).join(', '))}
          </Text>
        </Group>
      ))}
    </Stack>
  );
}
