import { ActionIcon, Button, Card, Group, NativeSelect, NumberInput, Select, Stack, Table, Text } from '@mantine/core';
import { useState } from 'react';
import { useResetBossStances, useUpdateBossStances } from '../../api/generated/admin/admin';
import type { AdminBoss, AdminCatalog, StanceInput, StanceInputMode } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useCatalogCache, useSaveFlow } from './adminHooks';
import { Actions, EditedBadge } from './adminShared';

type Difficulties = Record<string, StanceInput[]>;

const MODES: StanceInputMode[] = ['HEALTH', 'ON_DEMAND', 'FINAL'];

function difficultiesOf(boss: AdminBoss): Difficulties {
  return Object.fromEntries(
    Object.entries(boss.difficulties).map(([level, stances]) => [
      level,
      stances.map((stance) => ({
        toughnessPerHunter: stance.toughnessPerHunter,
        mode: stance.stanceChange.mode,
        atHealth: stance.stanceChange.atHealth,
      })),
    ]),
  );
}

/** Незаполненное: стоек меньше 3, у смены по здоровью нет порога. */
function problems(difficulties: Difficulties): number {
  return Object.values(difficulties).reduce(
    (sum, stances) =>
      sum + (stances.length < 3 ? 1 : 0) + stances.filter((s) => s.mode === 'HEALTH' && !(s.atHealth !== null && s.atHealth > 0)).length,
    0,
  );
}

/** Характеристики монстров (qa № 138): стойки по уровням враждебности — прочность за охотника и смена стойки. */
export function BossEditor({ catalog }: { catalog: AdminCatalog }) {
  const [code, setCode] = useState(catalog.bosses[0]?.code ?? '');
  const boss = catalog.bosses.find((item) => item.code === code);
  return (
    <Stack gap="md">
      <Select
        label={ru.admin.monster}
        searchable
        value={code}
        data={catalog.bosses.map((item) => ({
          value: item.code,
          label: item.name + (item.edited ? ` · ${ru.admin.edited}` : ''),
        }))}
        onChange={(value) => value !== null && setCode(value)}
        allowDeselect={false}
        data-testid="admin-boss"
      />
      <Text size="xs" c="dimmed">
        {ru.admin.bossHint}
      </Text>
      {boss !== undefined && <BossForm key={`${boss.code}-${String(boss.edited)}`} boss={boss} />}
    </Stack>
  );
}

function BossForm({ boss }: { boss: AdminBoss }) {
  const cache = useCatalogCache();
  const update = useUpdateBossStances();
  const reset = useResetBossStances();
  const flow = useSaveFlow(cache.boss);
  const [difficulties, setDifficulties] = useState<Difficulties>(() => difficultiesOf(boss));
  const dirty = JSON.stringify(difficulties) !== JSON.stringify(difficultiesOf(boss));
  const setStances = (level: string, stances: StanceInput[]) => setDifficulties({ ...difficulties, [level]: stances });

  return (
    <Stack gap="md" data-testid="admin-boss-form" data-code={boss.code}>
      <EditedBadge edited={boss.edited} />
      {Object.entries(difficulties).map(([level, stances]) => (
        <Card key={level} withBorder padding="sm" data-testid="admin-boss-level" data-level={level}>
          <Text fw={600} size="sm" mb="xs">
            {ru.sheet.difficulty(Number(level))}
          </Text>
          <Table withRowBorders={false} verticalSpacing={4}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{ru.admin.stance}</Table.Th>
                <Table.Th>{ru.admin.toughness}</Table.Th>
                <Table.Th>{ru.admin.stanceChange}</Table.Th>
                <Table.Th>{ru.admin.atHealth}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {stances.map((stance, index) => {
                const set = (patch: Partial<StanceInput>) =>
                  setStances(level, stances.map((s, i) => (i === index ? { ...s, ...patch } : s)));
                return (
                  <Table.Tr key={index} data-testid="admin-stance">
                    <Table.Td>{index + 1}</Table.Td>
                    <Table.Td>
                      <NumberInput
                        size="xs"
                        w={80}
                        min={1}
                        aria-label={ru.admin.toughness}
                        placeholder={ru.admin.noToughness}
                        value={stance.toughnessPerHunter ?? ''}
                        onChange={(value) => set({ toughnessPerHunter: value === '' ? null : Number(value) })}
                        data-testid="admin-stance-toughness"
                      />
                    </Table.Td>
                    <Table.Td>
                      <NativeSelect
                        size="xs"
                        aria-label={ru.admin.stanceChange}
                        value={stance.mode}
                        data={MODES.map((mode) => ({ value: mode, label: ru.admin.stanceModes[mode] ?? mode }))}
                        onChange={(event) => {
                          const mode = event.currentTarget.value as StanceInputMode;
                          set({ mode, atHealth: mode === 'HEALTH' ? (stance.atHealth ?? 6) : null });
                        }}
                        data-testid="admin-stance-mode"
                      />
                    </Table.Td>
                    <Table.Td>
                      {stance.mode === 'HEALTH' && (
                        <NumberInput
                          size="xs"
                          w={64}
                          min={1}
                          max={9}
                          aria-label={ru.admin.atHealth}
                          value={stance.atHealth ?? ''}
                          onChange={(value) => set({ atHealth: value === '' ? null : Number(value) })}
                          data-testid="admin-stance-health"
                        />
                      )}
                    </Table.Td>
                    <Table.Td>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        aria-label={ru.admin.remove}
                        disabled={stances.length <= 3}
                        onClick={() => setStances(level, stances.filter((_, i) => i !== index))}
                      >
                        ×
                      </ActionIcon>
                    </Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
          <Group mt="xs">
            <Button
              size="compact-xs"
              variant="subtle"
              disabled={stances.length >= 9}
              onClick={() =>
                setStances(level, [...stances.slice(0, -1), { toughnessPerHunter: null, mode: 'HEALTH', atHealth: 3 }, ...stances.slice(-1)])
              }
              data-testid="admin-stance-add"
            >
              + {ru.admin.addStance}
            </Button>
          </Group>
        </Card>
      ))}
      <Actions
        error={flow.error}
        missing={problems(difficulties)}
        dirty={dirty}
        edited={boss.edited}
        saving={update.isPending}
        onSave={() => void flow.save(update.mutateAsync({ code: boss.code, data: { difficulties } }))}
        onDiscard={() => {
          setDifficulties(difficultiesOf(boss));
          flow.clearError();
        }}
        onReset={() => flow.reset(reset.mutateAsync({ code: boss.code }))}
      />
    </Stack>
  );
}
