import { Button, Group, MultiSelect, NativeSelect, NumberInput, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useAchievements, useDictionaries, useQuests } from '../../api/generated/catalog/catalog';
import type { BattleRewards, OverridesRequest } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { bossLabel, useBossCatalog } from '../battle/bossCatalog';

const CATALOG_STALE_TIME = 60 * 60 * 1000;
const MAX_QUANTITY = 99;

interface OutcomeEditFormProps {
  victory: boolean;
  rewards: BattleRewards;
  /** Босс боя — если трофея в превью нет (бой без задания без выбранного босса). */
  battleBoss: string | null;
  loading: boolean;
  onCancel: () => void;
  onAccept: (overrides: OverridesRequest) => void;
}

/**
 * «Редактировать» награды (`PostVictoryDialog` app): босс трофея, ресурсы каждому охотнику, задания и
 * достижения. Применяются ровно эти значения — правила не пересчитываются (`api.md` §7.3).
 */
export function OutcomeEditForm({ victory, rewards, battleBoss, loading, onCancel, onAccept }: OutcomeEditFormProps) {
  const bosses = useBossCatalog();
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_TIME } });
  const quests = useQuests({ query: { staleTime: CATALOG_STALE_TIME } });
  const achievements = useAchievements({ query: { staleTime: CATALOG_STALE_TIME } });
  const [bossCode, setBossCode] = useState(rewards.trophy?.code ?? battleBoss ?? '');
  const [perHunter, setPerHunter] = useState<Record<string, number>>({ ...rewards.perHunter });
  const [openQuests, setOpenQuests] = useState(rewards.openQuests.map(String));
  const [granted, setGranted] = useState(rewards.achievements.map((achievement) => achievement.code));

  const resources = [
    ...(dictionaries.data?.elements ?? []),
    ...(dictionaries.data?.materials ?? []),
    ...(dictionaries.data?.plants ?? []),
  ];
  const elementOf = (code: string) => bosses.bosses?.find((boss) => boss.code === code)?.element ?? null;

  // Другой босс — его 2 стихии вместо стихий прежнего (как предзаполнение стихии в app)
  const selectBoss = (code: string) => {
    const before = elementOf(bossCode);
    const after = elementOf(code);
    setBossCode(code);
    if (before === after) return;
    setPerHunter((current) => {
      const next = { ...current };
      if (before !== null) next[before] = Math.max(0, (next[before] ?? 0) - 2);
      if (after !== null) next[after] = (next[after] ?? 0) + 2;
      return next;
    });
  };

  const accept = () =>
    onAccept({
      bossCode: victory && bossCode !== '' ? bossCode : null,
      perHunter: Object.fromEntries(Object.entries(perHunter).filter(([, quantity]) => quantity > 0)),
      openQuests: openQuests.map(Number),
      achievements: granted,
    });

  return (
    <Stack gap="md" data-testid="outcome-edit">
      <Title order={3}>{ru.progression.editTitle}</Title>
      {victory && (
        <NativeSelect
          label={ru.progression.editBoss}
          value={bossCode}
          onChange={(event) => selectBoss(event.currentTarget.value)}
          data={[
            { value: '', label: '—' },
            ...(bosses.bosses ?? []).map((boss) => ({ value: boss.code, label: bossLabel(boss, bosses.elementNames) })),
          ]}
          data-testid="outcome-edit-boss"
        />
      )}
      <div>
        <Text size="sm" fw={500} mb={4}>
          {ru.progression.editResources}
        </Text>
        <SimpleGrid cols={{ base: 2, xs: 3 }} spacing="xs" verticalSpacing="xs">
          {resources.map((resource) => (
            <NumberInput
              key={resource.code}
              label={resource.name}
              size="xs"
              min={0}
              max={MAX_QUANTITY}
              allowDecimal={false}
              allowNegative={false}
              clampBehavior="strict"
              value={perHunter[resource.code] ?? 0}
              onChange={(value) =>
                setPerHunter((current) => ({
                  ...current,
                  // Пустое поле — 0; строку («05») поле отдаёт, пока ввод не стал числом
                  [resource.code]: typeof value === 'number' ? value : Number(value) || 0,
                }))
              }
              data-testid="outcome-edit-resource"
              data-code={resource.code}
            />
          ))}
        </SimpleGrid>
      </div>
      <MultiSelect
        label={ru.progression.editQuests}
        data={(quests.data ?? []).map((quest) => ({ value: String(quest.number), label: `${quest.number}. ${quest.name}` }))}
        value={openQuests}
        onChange={setOpenQuests}
        searchable
        data-testid="outcome-edit-quests"
      />
      <MultiSelect
        label={ru.progression.editAchievements}
        data={(achievements.data ?? []).map((achievement) => ({ value: achievement.code, label: achievement.name }))}
        value={granted}
        onChange={setGranted}
        searchable
        data-testid="outcome-edit-achievements"
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onCancel} data-testid="outcome-edit-cancel">
          {ru.progression.cancel}
        </Button>
        <Button loading={loading} onClick={accept} data-testid="outcome-edit-accept">
          {ru.progression.editAccept}
        </Button>
      </Group>
    </Stack>
  );
}
