import { useCallback } from 'react';
import type { BattleEvent } from '../../domain/battle';

export type VibrationKind = 'SHORT' | 'DOUBLE';

/** Как в app: короткий отклик 30 мс, двойной — 30, пауза 40, 30. */
export const VIBRATION_PATTERNS: Record<VibrationKind, number | number[]> = {
  SHORT: 30,
  DOUBLE: [30, 40, 30],
};

/** Двойная вибрация — когда нанесены раны, иначе короткая (doc/battle.md §5). */
export function vibrationFor(events: readonly BattleEvent[]): VibrationKind {
  return events.some((event) => event.type === 'WOUNDS_INFLICTED') ? 'DOUBLE' : 'SHORT';
}

/** Vibration API есть не везде (например, в Safari нет) — тогда отклика просто нет. */
export function useVibration() {
  return useCallback((kind: VibrationKind) => {
    try {
      navigator.vibrate?.(VIBRATION_PATTERNS[kind]);
    } catch {
      // вибрация запрещена настройками браузера
    }
  }, []);
}
