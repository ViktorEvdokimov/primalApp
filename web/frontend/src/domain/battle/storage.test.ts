import { describe, expect, it } from 'vitest';
import { perform, undo } from './history';
import { createLocalBattle } from './localBattle';
import { BATTLE_STORAGE_KEY, createBattleStore, parseStoredBattle, type KeyValueStorage } from './storage';
import type { BattleCommand, LocalBattle } from './types';

const NOW = '2026-09-27T18:00:00.000Z';

function expedition(): LocalBattle {
  return createLocalBattle({
    id: 'battle-1',
    mode: 'EXPEDITION',
    campaign: null,
    boss: { code: 'VIRAXEN', name: 'Вираксен', element: 'FEATHER' },
    difficulty: 0,
    stances: [
      { stance: 1, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
      { stance: 2, toughnessPerHunter: 3, stanceChange: { mode: 'FINAL' } },
    ],
    params: { hunterCount: 2, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
    now: NOW,
  });
}

function performAll(battle: LocalBattle, commands: BattleCommand[]): LocalBattle {
  return commands.reduce((current, command) => perform(current, command, NOW).battle, battle);
}

/** Web Storage в памяти. */
function memoryStorage(): KeyValueStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: (key) => void data.delete(key),
  };
}

/** Хранилище, которое не принимает запись боя: переполнено. */
function fullStorage(): KeyValueStorage & { data: Map<string, string> } {
  const storage = memoryStorage();
  return {
    ...storage,
    setItem: (key, value) => {
      if (key === BATTLE_STORAGE_KEY) throw new DOMException('Хранилище переполнено', 'QuotaExceededError');
      storage.setItem(key, value);
    },
  };
}

describe('Хранение боя', () => {
  it('пустое хранилище — боя нет', () => {
    // вызов
    const stored = createBattleStore(() => memoryStorage()).load();

    // проверка
    expect(stored).toEqual({ status: 'EMPTY' });
  });

  it('бой записывается под ключом primal.battle и читается после перезагрузки', () => {
    // подготовка
    const storage = memoryStorage();
    const battle = performAll(expedition(), [{ type: 'DEAL_DAMAGE', amount: 5 }]);

    // вызов
    createBattleStore(() => storage).save(battle);
    const stored = createBattleStore(() => storage).load();

    // проверка
    expect(storage.data.has(BATTLE_STORAGE_KEY)).toBe(true);
    expect(stored).toEqual({ status: 'LOADED', battle, migratedFrom: null });
  });

  it('бой восстанавливается вместе с историей отмены', () => {
    // подготовка
    const storage = memoryStorage();
    const battle = performAll(expedition(), [
      { type: 'DEAL_DAMAGE', amount: 13 },
      { type: 'CONFIRM_STANCE', toughnessPerHunter: 3, stanceChange: { mode: 'FINAL' } },
      { type: 'END_ROUND' },
    ]);
    createBattleStore(() => storage).save(battle);

    // вызов
    const stored = createBattleStore(() => storage).load();
    if (stored.status !== 'LOADED') throw new Error('бой не восстановлен');
    const restored = undo(stored.battle, NOW);

    // проверка
    expect(stored.battle.history.map((entry) => entry.description)).toEqual([
      'урон +13',
      'параметры стойки 2',
      'завершение раунда 1',
    ]);
    expect(restored?.battle.state.round).toBe(1);
    expect(restored?.battle.state.monster).toMatchObject({ stance: 2, toughness: 6, accumulatedDamage: 1, health: 7 });
  });

  it('«Новый бой» удаляет запись', () => {
    // подготовка
    const storage = memoryStorage();
    const store = createBattleStore(() => storage);
    store.save(expedition());

    // вызов
    store.clear();

    // проверка
    expect(storage.data.has(BATTLE_STORAGE_KEY)).toBe(false);
    expect(store.load()).toEqual({ status: 'EMPTY' });
  });
});

describe('Формат записи', () => {
  it('испорченная запись отбрасывается и удаляется', () => {
    // подготовка
    const storage = memoryStorage();
    storage.setItem(BATTLE_STORAGE_KEY, '{"schemaVersion": 1, "state": ');

    // вызов
    const stored = createBattleStore(() => storage).load();

    // проверка
    expect(stored).toEqual({ status: 'DISCARDED', problem: 'CORRUPTED' });
    expect(storage.data.has(BATTLE_STORAGE_KEY)).toBe(false);
  });

  it.each<[string, (battle: LocalBattle) => unknown]>([
    ['нет состояния боя', (battle) => ({ ...battle, state: undefined })],
    ['здоровье больше 10', (battle) => ({ ...battle, state: { ...battle.state, monster: { ...battle.state.monster, health: 11 } } })],
    ['прочность 0', (battle) => ({ ...battle, state: { ...battle.state, monster: { ...battle.state.monster, toughness: 0 } } })],
    ['неизвестный режим смены стойки', (battle) => ({ ...battle, stances: [{ stance: 1, toughnessPerHunter: 2, stanceChange: { mode: 'SOMETIMES' } }] })],
    ['кампания без данных кампании', (battle) => ({ ...battle, mode: 'CAMPAIGN' })],
    ['снимок истории испорчен', (battle) => ({ ...battle, history: [{ state: null, description: 'урон +1' }] })],
  ])('запись с ошибкой формата отбрасывается: %s', (_, corrupt) => {
    // вызов
    const stored = parseStoredBattle(JSON.stringify(corrupt(expedition())));

    // проверка
    expect(stored).toEqual({ status: 'DISCARDED', problem: 'CORRUPTED' });
  });

  it('запись старой версии переводится в текущий формат и перезаписывается', () => {
    // подготовка: в «версии 0» сложность называлась level
    const { difficulty, ...current } = performAll(expedition(), [{ type: 'DEAL_DAMAGE', amount: 3 }]);
    const storage = memoryStorage();
    storage.setItem(BATTLE_STORAGE_KEY, JSON.stringify({ ...current, schemaVersion: 0, level: difficulty }));
    const migrations = {
      0: ({ level, ...record }: Record<string, unknown>) => ({ ...record, schemaVersion: 1, difficulty: level }),
    };

    // вызов
    const stored = createBattleStore(() => storage, migrations).load();

    // проверка
    expect(stored).toMatchObject({ status: 'LOADED', migratedFrom: 0 });
    expect(stored.status === 'LOADED' && stored.battle).toEqual({ ...current, difficulty });
    expect(JSON.parse(storage.getItem(BATTLE_STORAGE_KEY) ?? '{}')).toMatchObject({ schemaVersion: 1, difficulty: 0 });
  });

  it('старая версия без перевода отбрасывается', () => {
    // подготовка
    const record = JSON.stringify({ ...expedition(), schemaVersion: 0 });

    // вызов и проверка
    expect(parseStoredBattle(record, {})).toEqual({ status: 'DISCARDED', problem: 'UNSUPPORTED_VERSION' });
  });

  it('перевод, не повысивший версию, считается ошибкой', () => {
    // подготовка
    const record = JSON.stringify({ ...expedition(), schemaVersion: 0 });

    // вызов
    const stored = parseStoredBattle(record, { 0: (value) => value });

    // проверка
    expect(stored).toEqual({ status: 'DISCARDED', problem: 'CORRUPTED' });
  });

  it('запись более новой версии сайта отбрасывается', () => {
    // подготовка
    const record = JSON.stringify({ ...expedition(), schemaVersion: 2 });

    // вызов и проверка
    expect(parseStoredBattle(record)).toEqual({ status: 'DISCARDED', problem: 'UNSUPPORTED_VERSION' });
  });
});

describe('localStorage недоступен', () => {
  it('обращение к хранилищу бросает исключение — бой хранится в памяти вкладки', () => {
    // подготовка
    const store = createBattleStore(() => {
      throw new DOMException('Доступ запрещён', 'SecurityError');
    });
    const battle = expedition();

    // вызов
    store.save(battle);

    // проверка
    expect(store.persistent).toBe(false);
    expect(store.load()).toEqual({ status: 'LOADED', battle, migratedFrom: null });
  });

  it('методы хранилища бросают исключения — бой хранится в памяти вкладки', () => {
    // подготовка
    const throwing: KeyValueStorage = {
      getItem: () => {
        throw new Error('недоступно');
      },
      setItem: () => {
        throw new Error('недоступно');
      },
      removeItem: () => {
        throw new Error('недоступно');
      },
    };
    const store = createBattleStore(() => throwing);

    // вызов
    store.save(expedition());
    store.clear();

    // проверка
    expect(store.persistent).toBe(false);
    expect(store.load()).toEqual({ status: 'EMPTY' });
  });

  it('хранилище переполнилось посреди боя — дальше бой живёт в памяти', () => {
    // подготовка
    const store = createBattleStore(() => fullStorage());
    expect(store.persistent).toBe(true);
    const battle = expedition();

    // вызов
    store.save(battle);

    // проверка
    expect(store.persistent).toBe(false);
    expect(store.load()).toEqual({ status: 'LOADED', battle, migratedFrom: null });
  });
});
