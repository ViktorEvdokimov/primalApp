import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import {
  BATTLE_STORAGE_KEY,
  createBattleStore,
  createLocalBattle,
  type KeyValueStorage,
  type LocalBattle,
} from '../../domain/battle';
import { memoryStorage } from '../../test/render';
import { ActiveBattleContext, createActiveBattle, useActiveBattle, type ActiveBattle } from './useActiveBattle';

const NOW = '2026-09-27T18:00:00.000Z';

function expedition(): LocalBattle {
  return createLocalBattle({
    id: 'battle-1',
    mode: 'EXPEDITION',
    campaign: null,
    boss: null,
    difficulty: 0,
    stances: [],
    params: { hunterCount: 4, toughnessPerHunter: 1, stanceChange: { mode: 'ON_DEMAND' } },
    now: NOW,
  });
}

/** «Открытие сайта»: новый объект боя поверх того же хранилища. */
function openSite(storage: KeyValueStorage): ActiveBattle {
  return createActiveBattle(createBattleStore(() => storage), () => NOW);
}

function renderActiveBattle(active: ActiveBattle) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ActiveBattleContext.Provider value={active}>{children}</ActiveBattleContext.Provider>
  );
  return renderHook(() => useActiveBattle(), { wrapper });
}

describe('Текущий бой браузера', () => {
  it('перезагрузка страницы посреди боя возвращает тот же бой с той же историей отмены', () => {
    // подготовка
    const storage = memoryStorage();
    const before = openSite(storage);
    before.start(expedition());
    before.dispatch({ type: 'DEAL_DAMAGE', amount: 5 });
    before.dispatch({ type: 'END_ROUND' });

    // вызов
    const after = openSite(storage);

    // проверка
    expect(after.getSnapshot().battle).toEqual(before.getSnapshot().battle);
    expect(after.undo()?.message).toBe('Отменено: завершение раунда 1');
    expect(after.getSnapshot().battle?.state.round).toBe(1);
    expect(openSite(storage).getSnapshot().battle?.history).toHaveLength(1);
  });

  it('команды и отмена обновляют экран и хранилище', () => {
    // подготовка
    const storage = memoryStorage();
    const { result } = renderActiveBattle(openSite(storage));
    act(() => result.current.start(expedition()));

    // вызов
    act(() => {
      result.current.dispatch({ type: 'DEAL_DAMAGE', amount: 5 });
    });

    // проверка
    expect(result.current.battle?.state.monster).toMatchObject({ health: 9, accumulatedDamage: 1 });
    expect(result.current.canUndo).toBe(true);
    expect(result.current.nextUndo).toBe('урон +5');
    expect(JSON.parse(storage.data.get(BATTLE_STORAGE_KEY) ?? '{}').state.monster.health).toBe(9);

    act(() => {
      result.current.undo();
    });
    expect(result.current.battle?.state.monster.health).toBe(10);
    expect(result.current.canUndo).toBe(false);
  });

  it('отклонённая команда возвращает причину и не меняет бой', () => {
    // подготовка
    const { result } = renderActiveBattle(openSite(memoryStorage()));
    act(() => result.current.start(expedition()));
    const battle = result.current.battle;

    // вызов
    let outcome: ReturnType<typeof result.current.dispatch> = null;
    act(() => {
      outcome = result.current.dispatch({ type: 'HEAL_WOUND' });
    });

    // проверка
    expect(outcome).toMatchObject({ ok: false, reason: 'HEALTH_FULL' });
    expect(result.current.battle).toBe(battle);
  });

  it('«Вернуться к бою» — только при незаконченном бое', () => {
    // подготовка
    const { result } = renderActiveBattle(openSite(memoryStorage()));
    expect(result.current.hasUnfinished).toBe(false);

    // вызов и проверка
    act(() => result.current.start(expedition()));
    expect(result.current.hasUnfinished).toBe(true);
    act(() => {
      result.current.dispatch({ type: 'SURRENDER' });
    });
    expect(result.current.hasUnfinished).toBe(false);
  });

  it('«Новый бой» удаляет бой из хранилища', () => {
    // подготовка
    const storage = memoryStorage();
    const { result } = renderActiveBattle(openSite(storage));
    act(() => result.current.start(expedition()));

    // вызов
    act(() => result.current.clear());

    // проверка
    expect(result.current.battle).toBeNull();
    expect(storage.data.has(BATTLE_STORAGE_KEY)).toBe(false);
  });

  it('без localStorage бой идёт, а экран получает флаг предупреждения', () => {
    // подготовка
    const active = createActiveBattle(
      createBattleStore(() => {
        throw new DOMException('Доступ запрещён', 'SecurityError');
      }),
    );
    const { result } = renderActiveBattle(active);

    // вызов
    act(() => result.current.start(expedition()));
    act(() => {
      result.current.dispatch({ type: 'DEAL_DAMAGE', amount: 4 });
    });

    // проверка
    expect(result.current.persistent).toBe(false);
    expect(result.current.battle?.state.monster.health).toBe(9);
  });

  it('испорченный сохранённый бой: сообщение о проблеме до закрытия', () => {
    // подготовка
    const storage = memoryStorage();
    storage.setItem(BATTLE_STORAGE_KEY, 'не JSON');
    const { result } = renderActiveBattle(openSite(storage));
    expect(result.current.restoreProblem).toBe('CORRUPTED');
    expect(result.current.battle).toBeNull();

    // вызов
    act(() => result.current.dismissRestoreProblem());

    // проверка
    expect(result.current.restoreProblem).toBeNull();
  });
});
