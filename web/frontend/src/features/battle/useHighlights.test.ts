import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createBattle, type BattleState } from '../../domain/battle';
import { changedParams, HIGHLIGHT_MS, useHighlights } from './useHighlights';
import { vibrationFor } from './useVibration';

const before = createBattle({ hunterCount: 4, toughnessPerHunter: 1, stanceChange: { mode: 'HEALTH', atHealth: 7 } });

function withMonster(patch: Partial<BattleState['monster']>, state: BattleState = before): BattleState {
  return { ...state, monster: { ...state.monster, ...patch } };
}

describe('Подсветка изменённых параметров', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('изменённые параметры определяются по состояниям до и после команды', () => {
    // подготовка
    const after: BattleState = {
      ...withMonster({ health: 9, accumulatedDamage: 1, stanceChange: { mode: 'FINAL' }, hardened: true }),
      round: 2,
    };

    // вызов и проверка
    expect(changedParams(before, after)).toEqual(['ROUND', 'HEALTH', 'ACCUMULATED_DAMAGE', 'STANCE_CHANGE', 'HARDENED']);
    expect(changedParams(before, { ...before })).toEqual([]);
  });

  it('изменённый параметр подсвечен 1 секунду', () => {
    // подготовка
    const { result } = renderHook(() => useHighlights());

    // вызов
    act(() => result.current.highlight(before, withMonster({ health: 9 })));

    // проверка
    expect(result.current.highlighted).toEqual(new Set(['HEALTH']));
    act(() => vi.advanceTimersByTime(HIGHLIGHT_MS - 1));
    expect(result.current.highlighted.has('HEALTH')).toBe(true);
    act(() => vi.advanceTimersByTime(1));
    expect(result.current.highlighted.size).toBe(0);
  });

  it('повторное изменение продлевает подсветку', () => {
    // подготовка
    const { result } = renderHook(() => useHighlights());
    act(() => result.current.highlight(before, withMonster({ rage: 5 })));
    act(() => vi.advanceTimersByTime(600));

    // вызов
    act(() => result.current.highlight(withMonster({ rage: 5 }), withMonster({ rage: 6 })));
    act(() => vi.advanceTimersByTime(600));

    // проверка: с первого изменения прошло 1,2 с, со второго — 0,6 с
    expect(result.current.highlighted.has('RAGE')).toBe(true);
    act(() => vi.advanceTimersByTime(400));
    expect(result.current.highlighted.has('RAGE')).toBe(false);
  });

  it('параметры гаснут независимо', () => {
    // подготовка
    const { result } = renderHook(() => useHighlights());
    act(() => result.current.highlight(before, withMonster({ health: 9 })));
    act(() => vi.advanceTimersByTime(500));

    // вызов
    act(() => result.current.highlight(before, withMonster({ rage: 8 })));
    act(() => vi.advanceTimersByTime(500));

    // проверка
    expect(result.current.highlighted).toEqual(new Set(['RAGE']));
  });
});

describe('Вибрация', () => {
  it('двойная при нанесении ран, иначе короткая', () => {
    // вызов и проверка
    expect(vibrationFor([{ type: 'DAMAGE_ACCUMULATED', amount: 8 }, { type: 'WOUNDS_INFLICTED', count: 2 }])).toBe(
      'DOUBLE',
    );
    expect(vibrationFor([{ type: 'DAMAGE_ACCUMULATED', amount: 3 }])).toBe('SHORT');
    expect(vibrationFor([])).toBe('SHORT');
  });
});
