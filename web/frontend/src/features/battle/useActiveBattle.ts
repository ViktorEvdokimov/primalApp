import { createContext, useContext, useMemo, useSyncExternalStore } from 'react';
import {
  canUndo,
  createBattleStore,
  isUnfinished,
  nextUndo,
  perform,
  undo,
  type BattleCommand,
  type BattleStore,
  type CommandResult,
  type LocalBattle,
  type RestoreProblem,
  type UndoResult,
} from '../../domain/battle';

/** Текущий бой браузера: один на браузер, как в app (doc/battle.md §7). */
export interface ActiveBattleSnapshot {
  battle: LocalBattle | null;
  /** `false` — бой не сохранится при закрытии вкладки: экран предупреждает. */
  persistent: boolean;
  /** Сохранённый бой найден, но не восстановлен — экран сообщает об этом один раз. */
  restoreProblem: RestoreProblem | null;
}

export interface ActiveBattle {
  getSnapshot(): ActiveBattleSnapshot;
  subscribe(listener: () => void): () => void;
  /** Начинает бой, заменяя текущий. Подтверждение замены незаконченного боя — забота экрана. */
  start(battle: LocalBattle): void;
  dispatch(command: BattleCommand): CommandResult | null;
  undo(): UndoResult | null;
  /** «Новый бой»: бой удаляется из хранилища. */
  clear(): void;
  /**
   * Правка служебных полей боя — отметка о начале дошла, результат отправлен. Меняет только бой с этим
   * `id`: если его уже заменили другим, ничего не происходит.
   */
  update(id: string, change: (battle: LocalBattle) => LocalBattle): void;
  dismissRestoreProblem(): void;
}

/** Бой в хранилище: загрузка при создании, запись после каждой команды. */
export function createActiveBattle(store: BattleStore, now: () => string = () => new Date().toISOString()): ActiveBattle {
  const loaded = store.load();
  let snapshot: ActiveBattleSnapshot = {
    battle: loaded.status === 'LOADED' ? loaded.battle : null,
    persistent: store.persistent,
    restoreProblem: loaded.status === 'DISCARDED' ? loaded.problem : null,
  };
  const listeners = new Set<() => void>();

  const update = (patch: Partial<ActiveBattleSnapshot>) => {
    snapshot = { ...snapshot, ...patch, persistent: store.persistent };
    listeners.forEach((listener) => listener());
  };

  const save = (battle: LocalBattle | null) => {
    if (battle === null) store.clear();
    else store.save(battle);
    update({ battle });
  };

  return {
    getSnapshot: () => snapshot,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    start(battle) {
      save(battle);
    },
    dispatch(command) {
      if (snapshot.battle === null) return null;
      const { battle, result } = perform(snapshot.battle, command, now());
      if (result.ok) save(battle);
      return result;
    },
    undo() {
      if (snapshot.battle === null) return null;
      const result = undo(snapshot.battle, now());
      if (result !== null) save(result.battle);
      return result;
    },
    clear() {
      save(null);
    },
    update(id, change) {
      if (snapshot.battle?.id === id) save(change(snapshot.battle));
    },
    dismissRestoreProblem() {
      update({ restoreProblem: null });
    },
  };
}

/** Подмена боя в тестах: `<ActiveBattleContext.Provider value={createActiveBattle(store)}>`. */
export const ActiveBattleContext = createContext<ActiveBattle | null>(null);

let browserBattle: ActiveBattle | null = null;

function defaultActiveBattle(): ActiveBattle {
  browserBattle ??= createActiveBattle(createBattleStore(() => window.localStorage));
  return browserBattle;
}

export function useActiveBattle() {
  const active = useContext(ActiveBattleContext) ?? defaultActiveBattle();
  const snapshot = useSyncExternalStore(active.subscribe, active.getSnapshot);
  return useMemo(() => {
    const battle = snapshot.battle;
    return {
      ...snapshot,
      hasUnfinished: battle !== null && isUnfinished(battle),
      canUndo: battle !== null && canUndo(battle),
      nextUndo: battle === null ? null : nextUndo(battle),
      /** Бой на момент вызова — для обработчиков, которые срабатывают позже рендера (таймер ввода урона). */
      getBattle: () => active.getSnapshot().battle,
      start: active.start,
      dispatch: active.dispatch,
      undo: active.undo,
      clear: active.clear,
      update: active.update,
      dismissRestoreProblem: active.dismissRestoreProblem,
    };
  }, [active, snapshot]);
}
