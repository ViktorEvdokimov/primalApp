import { useMemo } from 'react';
import { useBosses, useDictionaries } from '../../api/generated/catalog/catalog';
import type { Boss, Stance } from '../../api/generated/primal.schemas';
import type { BattleBoss, Difficulty, StanceDef } from '../../domain/battle';
import { MANUAL_STANCE, stanceFormFrom, type StanceFormValues } from './stanceForm';

/** Каталог меняется только с выпуском сайта: сервер отдаёт его с ETag и кэшем на час. */
const CATALOG_STALE_TIME = 60 * 60 * 1000;

const DIFFICULTIES: readonly Difficulty[] = [0, 1, 2, 3];

export function isDifficulty(value: number): value is Difficulty {
  return (DIFFICULTIES as readonly number[]).includes(value);
}

/** Боссы в порядке каталога (стихия, затем имя; Пробуждённый последним) и русские названия стихий. */
export function useBossCatalog() {
  const bosses = useBosses({ query: { staleTime: CATALOG_STALE_TIME } });
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_TIME } });
  const sorted = useMemo(
    () => (bosses.data === undefined ? undefined : [...bosses.data].sort((a, b) => a.sortOrder - b.sortOrder)),
    [bosses.data],
  );
  const elementNames = useMemo(
    () => new Map((dictionaries.data?.elements ?? []).map((element) => [element.code, element.name])),
    [dictionaries.data],
  );
  return {
    bosses: sorted,
    elementNames,
    isLoading: bosses.isPending,
    isError: bosses.isError,
    refetch: bosses.refetch,
  };
}

/** «Коралл - Коровон», как в списке мобильного приложения; у Пробуждённого стихии нет. */
export function bossLabel(boss: Boss, elementNames: ReadonlyMap<string, string>): string {
  const element = boss.element === null ? undefined : elementNames.get(boss.element);
  return element === undefined ? boss.name : `${element} - ${boss.name}`;
}

/** Уровни враждебности, для которых у босса есть стойки (у Пробуждённого — только 3). */
export function availableDifficulties(boss: Boss): Difficulty[] {
  return DIFFICULTIES.filter((level) => (boss.difficulties[String(level)]?.length ?? 0) > 0);
}

/** Стойки босса на уровне враждебности — снимок для боя (doc/battle.md §7). */
export function bossStances(boss: Boss, difficulty: Difficulty): StanceDef[] {
  return (boss.difficulties[String(difficulty)] ?? []).map(toStanceDef);
}

export function toBattleBoss(boss: Boss): BattleBoss {
  return { code: boss.code, name: boss.name, element: boss.element };
}

function toStanceDef(stance: Stance): StanceDef {
  const { mode, atHealth } = stance.stanceChange;
  return {
    stance: stance.stance,
    toughnessPerHunter: stance.toughnessPerHunter,
    stanceChange:
      mode === 'HEALTH' && atHealth !== null ? { mode, atHealth } : { mode: mode === 'FINAL' ? 'FINAL' : 'ON_DEMAND' },
  };
}

/** Стойка I босса на уровне враждебности — префилл полей подготовки (D-2); без босса — ручной ввод. */
export function firstStance(boss: Boss | undefined, difficulty: Difficulty): StanceFormValues {
  return boss === undefined ? MANUAL_STANCE : stanceFormFrom(bossStances(boss, difficulty)[0] ?? null);
}
