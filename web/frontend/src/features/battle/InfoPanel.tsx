import { Divider, Group, Stack, Text } from '@mantine/core';
import type { BattleState, StanceChange } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import type { BattleParam } from './useHighlights';

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX'];

function romanStance(stance: number): string {
  return ROMAN[stance - 1] ?? String(stance);
}

function stanceChangeText(change: StanceChange): string {
  switch (change.mode) {
    case 'HEALTH':
      return ru.battle.stanceChangeHealth(change.atHealth);
    case 'ON_DEMAND':
      return ru.battle.stanceChangeOnDemand;
    case 'FINAL':
      return ru.battle.stanceChangeFinal;
  }
}

function statusText(hardened: boolean, resilient: boolean): string {
  const statuses = [hardened && ru.battle.hardened, resilient && ru.battle.resilient].filter(Boolean);
  return statuses.length > 0 ? statuses.join(', ') : ru.battle.statusNormal;
}

interface ParamProps {
  text: string;
  highlighted: boolean;
  bold?: boolean;
  testId: string;
}

/** Изменённое значение 1 секунду показывается красным и жирным (задача 37.1 app). */
function Param({ text, highlighted, bold = false, testId }: ParamProps) {
  return (
    <Text
      c={highlighted ? 'red' : undefined}
      fw={highlighted || bold ? 700 : 400}
      data-highlighted={highlighted || undefined}
      data-testid={testId}
    >
      {text}
    </Text>
  );
}

/** Информационная панель боя (app/doc/behavior.md §2.1). */
export function InfoPanel({ state, highlighted }: { state: BattleState; highlighted: ReadonlySet<BattleParam> }) {
  const monster = state.monster;
  const is = (param: BattleParam) => highlighted.has(param);
  return (
    <Stack gap={4}>
      <Group justify="space-between">
        <Param text={ru.battle.phase(romanStance(monster.stance))} highlighted={is('STANCE')} bold testId="battle-phase" />
        <Param text={ru.battle.round(Math.min(state.round, state.maxRounds), state.maxRounds)} highlighted={is('ROUND')} testId="battle-round" />
      </Group>
      <Divider my={4} />
      <Group justify="space-between">
        <Param text={ru.battle.health(monster.health)} highlighted={is('HEALTH')} testId="battle-health" />
        <Param text={ru.battle.rage(monster.rage)} highlighted={is('RAGE')} testId="battle-rage" />
      </Group>
      <Group justify="space-between">
        <Param
          text={ru.battle.accumulated(monster.accumulatedDamage)}
          highlighted={is('ACCUMULATED_DAMAGE')}
          testId="battle-accumulated"
        />
        <Param text={ru.battle.toughness(monster.toughness)} highlighted={is('TOUGHNESS')} testId="battle-toughness" />
      </Group>
      <Group justify="space-between">
        <Param
          text={ru.battle.status(statusText(monster.hardened, monster.resilient))}
          highlighted={is('HARDENED') || is('RESILIENT')}
          testId="battle-status"
        />
        <Param
          text={ru.battle.stanceChange(stanceChangeText(monster.stanceChange))}
          highlighted={is('STANCE_CHANGE')}
          testId="battle-stance-change"
        />
      </Group>
    </Stack>
  );
}
