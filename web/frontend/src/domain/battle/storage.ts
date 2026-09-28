import {
  LOCAL_BATTLE_SCHEMA_VERSION,
  MAX_ROUNDS,
  type BattleState,
  type LocalBattle,
  type MonsterState,
  type PendingStance,
  type StanceChange,
  type StanceDef,
} from './types';

/**
 * Хранение текущего боя в браузере (doc/battle.md §7). Хранилище передаётся параметром:
 * `src/domain` не обращается к `localStorage` сам.
 */

export const BATTLE_STORAGE_KEY = 'primal.battle';

/** Подмножество Web Storage API, нужное бою. */
export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/** Почему сохранённый бой не восстановлен: запись испорчена или её формат больше не поддерживается. */
export type RestoreProblem = 'CORRUPTED' | 'UNSUPPORTED_VERSION';

export type StoredBattle =
  | { status: 'EMPTY' }
  | { status: 'LOADED'; battle: LocalBattle; migratedFrom: number | null }
  | { status: 'DISCARDED'; problem: RestoreProblem };

export interface BattleStore {
  /** `false` — `localStorage` недоступен: бой живёт в памяти вкладки и не переживёт её закрытие. */
  readonly persistent: boolean;
  load(): StoredBattle;
  save(battle: LocalBattle): void;
  clear(): void;
}

/** Перевод записи версии N в версию N + 1. */
export type Migration = (record: Record<string, unknown>) => Record<string, unknown>;

/** Переводы старых записей: ключ — версия, из которой переводит функция. Пока формат один. */
export const MIGRATIONS: Readonly<Record<number, Migration>> = {};

/**
 * `storage` — функция: в некоторых приватных режимах браузера исключение бросает уже обращение к
 * `window.localStorage`. Если хранилище недоступно или перестало принимать записи, бой хранится в памяти.
 */
export function createBattleStore(
  storage: () => KeyValueStorage,
  migrations: Readonly<Record<number, Migration>> = MIGRATIONS,
): BattleStore {
  let backend = probe(storage);
  let memory: string | null = null;

  const read = (): string | null => {
    if (backend === null) return memory;
    try {
      return backend.getItem(BATTLE_STORAGE_KEY);
    } catch {
      backend = null;
      return memory;
    }
  };

  const write = (value: string | null): void => {
    memory = value;
    if (backend === null) return;
    try {
      if (value === null) backend.removeItem(BATTLE_STORAGE_KEY);
      else backend.setItem(BATTLE_STORAGE_KEY, value);
    } catch {
      backend = null; // переполнено или запрещено — дальше только память
    }
  };

  return {
    get persistent() {
      return backend !== null;
    },
    load() {
      const raw = read();
      if (raw === null) return { status: 'EMPTY' };
      const stored = parseStoredBattle(raw, migrations);
      if (stored.status === 'DISCARDED') write(null);
      else if (stored.status === 'LOADED' && stored.migratedFrom !== null) write(JSON.stringify(stored.battle));
      return stored;
    },
    save(battle) {
      write(JSON.stringify(battle));
    },
    clear() {
      write(null);
    },
  };
}

function probe(storage: () => KeyValueStorage): KeyValueStorage | null {
  try {
    const backend = storage();
    const key = `${BATTLE_STORAGE_KEY}.probe`;
    backend.setItem(key, '1');
    backend.removeItem(key);
    return backend;
  } catch {
    return null;
  }
}

/** Разбирает запись: переводит старый формат в текущий и проверяет её целиком. */
export function parseStoredBattle(
  raw: string,
  migrations: Readonly<Record<number, Migration>> = MIGRATIONS,
): StoredBattle {
  let record: unknown;
  try {
    record = JSON.parse(raw);
  } catch {
    return discarded('CORRUPTED');
  }
  if (!isRecord(record) || !isInteger(record.schemaVersion)) return discarded('CORRUPTED');

  const original = record.schemaVersion;
  if (original > LOCAL_BATTLE_SCHEMA_VERSION) return discarded('UNSUPPORTED_VERSION');
  let current: Record<string, unknown> = record;
  let version = original;
  while (version < LOCAL_BATTLE_SCHEMA_VERSION) {
    const migrate = migrations[version];
    if (migrate === undefined) return discarded('UNSUPPORTED_VERSION');
    try {
      current = migrate(current);
    } catch {
      return discarded('CORRUPTED');
    }
    if (!isInteger(current.schemaVersion) || current.schemaVersion <= version) return discarded('CORRUPTED');
    version = current.schemaVersion;
  }

  if (!isLocalBattle(current)) return discarded('CORRUPTED');
  return { status: 'LOADED', battle: current, migratedFrom: original === version ? null : original };
}

function discarded(problem: RestoreProblem): StoredBattle {
  return { status: 'DISCARDED', problem };
}

// --- Проверка формата: испорченная запись не должна ломать движок ---

type Guarded = Record<string, unknown>;

function isRecord(value: unknown): value is Guarded {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown): value is number {
  return Number.isInteger(value);
}

function isIntegerIn(value: unknown, min: number, max: number): value is number {
  return isInteger(value) && value >= min && value <= max;
}

function isNullable<T>(value: unknown, guard: (value: unknown) => value is T): value is T | null {
  return value === null || guard(value);
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isOneOf<T extends string>(value: unknown, options: readonly T[]): value is T {
  return options.includes(value as T);
}

function isPositiveInteger(value: unknown): value is number {
  return isInteger(value) && value > 0;
}

function isStanceChange(value: unknown): value is StanceChange {
  if (!isRecord(value)) return false;
  if (value.mode === 'HEALTH') return isIntegerIn(value.atHealth, 1, 9);
  return value.mode === 'ON_DEMAND' || value.mode === 'FINAL';
}

function isStanceDef(value: unknown): value is StanceDef {
  return (
    isRecord(value) &&
    isIntegerIn(value.stance, 1, 9) &&
    isNullable(value.toughnessPerHunter, isPositiveInteger) &&
    isStanceChange(value.stanceChange)
  );
}

function isMonster(value: unknown): value is MonsterState {
  return (
    isRecord(value) &&
    isIntegerIn(value.stance, 1, 9) &&
    isIntegerIn(value.health, 0, 10) &&
    isIntegerIn(value.accumulatedDamage, 0, Number.MAX_SAFE_INTEGER) &&
    isNullable(value.toughness, isPositiveInteger) &&
    isStanceChange(value.stanceChange) &&
    isIntegerIn(value.rage, 0, Number.MAX_SAFE_INTEGER) &&
    typeof value.hardened === 'boolean' &&
    typeof value.resilient === 'boolean'
  );
}

function isPendingStance(value: unknown): value is PendingStance {
  return (
    isRecord(value) &&
    isIntegerIn(value.stance, 1, 9) &&
    isIntegerIn(value.carriedDamage, 0, Number.MAX_SAFE_INTEGER) &&
    typeof value.carriedDamageResets === 'boolean' &&
    isNullable(value.prefill, isStanceDef)
  );
}

function isBattleState(value: unknown): value is BattleState {
  return (
    isRecord(value) &&
    isOneOf(value.status, ['IN_PROGRESS', 'VICTORY', 'DEFEAT'] as const) &&
    (value.defeatReason === null || isOneOf(value.defeatReason, ['ROUNDS', 'SURRENDER'] as const)) &&
    isIntegerIn(value.round, 1, MAX_ROUNDS + 1) &&
    value.maxRounds === MAX_ROUNDS &&
    isPositiveInteger(value.hunterCount) &&
    isMonster(value.monster) &&
    isNullable(value.pendingStance, isPendingStance) &&
    typeof value.rageSurgePending === 'boolean'
  );
}

function isHistoryEntry(value: unknown): boolean {
  return isRecord(value) && isBattleState(value.state) && isString(value.description);
}

function isCampaignInfo(value: unknown): boolean {
  return (
    isRecord(value) &&
    isPositiveInteger(value.id) &&
    isString(value.name) &&
    isIntegerIn(value.chapter, 0, 11) &&
    isInteger(value.progressSeq) &&
    isOneOf(value.purpose, ['PROLOGUE', 'QUEST', 'FREE', 'FINAL'] as const) &&
    isNullable(value.questNumber, isPositiveInteger) &&
    typeof value.startMarked === 'boolean'
  );
}

function isBoss(value: unknown): boolean {
  return isRecord(value) && isString(value.code) && isString(value.name) && isNullable(value.element, isString);
}

function isSubmission(value: unknown): boolean {
  return (
    isRecord(value) && isOneOf(value.status, ['NOT_SENT', 'SENT'] as const) && isNullable(value.lastError, isString)
  );
}

function isLocalBattle(value: Guarded): value is Guarded & LocalBattle {
  return (
    value.schemaVersion === LOCAL_BATTLE_SCHEMA_VERSION &&
    isString(value.id) &&
    value.id.length > 0 &&
    isOneOf(value.mode, ['EXPEDITION', 'CAMPAIGN'] as const) &&
    (value.campaign === null || isCampaignInfo(value.campaign)) &&
    (value.mode === 'CAMPAIGN') === (value.campaign !== null) &&
    (value.boss === null || isBoss(value.boss)) &&
    isIntegerIn(value.difficulty, 0, 3) &&
    Array.isArray(value.stances) &&
    value.stances.every(isStanceDef) &&
    isBattleState(value.state) &&
    Array.isArray(value.history) &&
    value.history.every(isHistoryEntry) &&
    (value.submission === null || isSubmission(value.submission)) &&
    isString(value.startedAt) &&
    isString(value.updatedAt) &&
    isNullable(value.finishedAt, isString)
  );
}
