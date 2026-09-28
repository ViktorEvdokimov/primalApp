import { useCallback, useEffect, useRef, useState } from 'react';
import type { BattleState } from '../../domain/battle';

/** Параметры информационной панели, которые подсвечиваются после изменения (задача 37.1 app). */
export type BattleParam =
  | 'STANCE'
  | 'ROUND'
  | 'HEALTH'
  | 'RAGE'
  | 'ACCUMULATED_DAMAGE'
  | 'TOUGHNESS'
  | 'STANCE_CHANGE'
  | 'HARDENED'
  | 'RESILIENT';

export const HIGHLIGHT_MS = 1000;

/** Какие параметры панели отличаются между двумя состояниями боя. */
export function changedParams(before: BattleState, after: BattleState): BattleParam[] {
  const a = before.monster;
  const b = after.monster;
  const changes: [BattleParam, boolean][] = [
    ['STANCE', a.stance !== b.stance],
    ['ROUND', before.round !== after.round],
    ['HEALTH', a.health !== b.health],
    ['RAGE', a.rage !== b.rage],
    ['ACCUMULATED_DAMAGE', a.accumulatedDamage !== b.accumulatedDamage],
    ['TOUGHNESS', a.toughness !== b.toughness],
    ['STANCE_CHANGE', JSON.stringify(a.stanceChange) !== JSON.stringify(b.stanceChange)],
    ['HARDENED', a.hardened !== b.hardened],
    ['RESILIENT', a.resilient !== b.resilient],
  ];
  return changes.filter(([, changed]) => changed).map(([param]) => param);
}

/**
 * Подсветка изменённых параметров на 1 секунду; повторное изменение продлевает подсветку.
 * `highlight(before, after)` вызывается после каждой команды и отмены.
 */
export function useHighlights() {
  const [highlighted, setHighlighted] = useState<ReadonlySet<BattleParam>>(new Set());
  const timers = useRef(new Map<BattleParam, ReturnType<typeof setTimeout>>());

  useEffect(() => {
    const active = timers.current;
    return () => active.forEach((id) => clearTimeout(id));
  }, []);

  const highlight = useCallback((before: BattleState, after: BattleState) => {
    const params = changedParams(before, after);
    if (params.length === 0) return;
    setHighlighted((current) => new Set([...current, ...params]));
    for (const param of params) {
      const previous = timers.current.get(param);
      if (previous !== undefined) clearTimeout(previous);
      timers.current.set(
        param,
        setTimeout(() => {
          timers.current.delete(param);
          setHighlighted((current) => {
            const next = new Set(current);
            next.delete(param);
            return next;
          });
        }, HIGHLIGHT_MS),
      );
    }
  }, []);

  return { highlighted, highlight };
}
