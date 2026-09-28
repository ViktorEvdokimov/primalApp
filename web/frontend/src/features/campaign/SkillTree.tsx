import { Button, SimpleGrid, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useLockSkill, useUnlockSkill } from '../../api/generated/campaigns/campaigns';
import type { HunterSheet, Named, SkillStep, SkillStepBranch } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { withHunter, type SheetActions } from './useCampaignSheet';

const BRANCHES: SkillStepBranch[] = ['A', 'B', 'V', 'G', 'D'];
const TIERS = [1, 2] as const;

const has = (steps: SkillStep[], branch: SkillStepBranch, tier: number) =>
  steps.some((step) => step.branch === branch && step.tier === tier);

interface SkillTreeProps {
  campaignId: number;
  hunter: HunterSheet;
  /** Буквы ветвей из `/catalog/dictionaries`: A → «А», V → «В». */
  branches: Named[];
  actions: SheetActions;
}

/**
 * Древо навыков: 5 ветвей по 2 ступени, ступень 2 — только после ступени 1 (`SkillTree` на сервере).
 * Состояние кнопки — не только цвет, но и `aria-pressed` с подписью «Открыт»/«Закрыт» (как задача 42.2 app).
 * Снять можно открытую ступень, кроме ступени 1 при открытой ступени 2.
 */
export function SkillTree({ campaignId, hunter, branches, actions }: SkillTreeProps) {
  const unlock = useUnlockSkill();
  const lock = useLockSkill();
  const letters = new Map(branches.map((branch) => [branch.code, branch.name]));
  // Кнопки, чей запрос ещё идёт: повторное нажатие до ответа отправило бы тот же запрос второй раз
  const [pending, setPending] = useState<ReadonlySet<string>>(new Set());

  const toggle = (branch: SkillStepBranch, tier: number, open: boolean) => {
    const key = `${branch}${tier}`;
    setPending((current) => new Set(current).add(key));
    const request = open
      ? lock.mutateAsync({ campaignId, hunterId: hunter.id, branch, tier })
      : unlock.mutateAsync({ campaignId, hunterId: hunter.id, data: { branch, tier } });
    request
      .then(
        (updated) => actions.applied((sheet) => withHunter(sheet, hunter.id, () => updated)),
        (error: unknown) => actions.failed(error),
      )
      .finally(() =>
        setPending((current) => {
          const next = new Set(current);
          next.delete(key);
          return next;
        }),
      );
  };

  return (
    <Stack gap="xs" data-testid="skill-tree">
      <Text fw={600}>{ru.sheet.skills}</Text>
      <SimpleGrid cols={5} spacing="xs" verticalSpacing="xs">
        {TIERS.map((tier) =>
          BRANCHES.map((branch) => {
            const open = has(hunter.skills, branch, tier);
            const label = `${letters.get(branch) ?? branch}${tier}`;
            const busy = pending.has(`${branch}${tier}`);
            // Открытая ступень 1 при открытой ступени 2 не снимается, но выглядит открытой, а не серой
            const pinned = open && tier === 1 && has(hunter.skills, branch, 2);
            const allowed = open ? !pinned : has(hunter.unlockableSkills, branch, tier);
            return (
              <Button
                key={`${branch}${tier}`}
                variant={open ? 'filled' : 'default'}
                color={open ? 'teal' : undefined}
                aria-pressed={open}
                aria-label={`${label}: ${open ? ru.sheet.skillOpen : ru.sheet.skillClosed}`}
                aria-disabled={pinned || undefined}
                disabled={!open && !allowed}
                loading={busy}
                onClick={() => {
                  if (allowed) toggle(branch, tier, open);
                }}
                style={pinned ? { cursor: 'default' } : undefined}
                data-testid={`skill-${branch}${tier}`}
                data-skill={label}
                px={0}
              >
                {label}
              </Button>
            );
          }),
        )}
      </SimpleGrid>
    </Stack>
  );
}
