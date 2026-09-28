/** Типы боя (doc/battle.md §2–3, §5). */

export const MAX_HEALTH = 10;
export const MAX_STANCE = 9;
export const MAX_ROUNDS = 10;
/** Выплеск ярости — когда ярость достигает 3 за охотника (R-8). */
export const RAGE_SURGE_PER_HUNTER = 3;

export type StanceChange =
  | { mode: 'HEALTH'; atHealth: number } // смена при здоровье ≤ atHealth
  | { mode: 'ON_DEMAND' } // кнопка «Сменить стойку»
  | { mode: 'FINAL' }; // последняя стойка

/** Стойка из каталога — снимок на момент начала боя. */
export interface StanceDef {
  stance: number; // 1–9
  toughnessPerHunter: number | null; // прочность за охотника; null — нет порога раны
  stanceChange: StanceChange;
}

export interface MonsterState {
  stance: number; // 1–9
  health: number; // 0–10
  accumulatedDamage: number; // жетоны урона на карте стойки
  toughness: number | null; // итоговая прочность = за охотника × охотники
  stanceChange: StanceChange;
  rage: number;
  hardened: boolean; // «Затвердевший»
  resilient: boolean; // «Устойчивость стойки»
}

/** Окно «Смена стойки!» ждёт подтверждения параметров новой стойки. */
export interface PendingStance {
  stance: number;
  carriedDamage: number; // будет нанесён с новой прочностью
  carriedDamageResets: boolean; // «Устойчивость стойки»: урон сбросится
  prefill: StanceDef | null; // null — стойки нет в снимке каталога, поля пустые
}

export type BattleStatus = 'IN_PROGRESS' | 'VICTORY' | 'DEFEAT';
export type DefeatReason = 'ROUNDS' | 'SURRENDER';

export interface BattleState {
  status: BattleStatus;
  defeatReason: DefeatReason | null;
  round: number; // 1–11: после 10-го раунда — поражение
  maxRounds: typeof MAX_ROUNDS;
  hunterCount: number; // > 0
  monster: MonsterState;
  pendingStance: PendingStance | null;
  rageSurgePending: boolean;
}

/** Параметры нового боя: поля подготовки, предзаполненные стойкой I (D-2). */
export interface NewBattleParams {
  hunterCount: number;
  toughnessPerHunter: number | null;
  stanceChange: StanceChange;
}

export type BattleCommand =
  | { type: 'DEAL_DAMAGE'; amount: number }
  | { type: 'HEAL_WOUND' }
  | { type: 'ADJUST_RAGE'; delta: number }
  | { type: 'SET_STATUS'; hardened?: boolean; resilient?: boolean }
  | { type: 'CHANGE_STANCE' }
  | { type: 'CONFIRM_STANCE'; toughnessPerHunter: number | null; stanceChange: StanceChange; health?: number }
  | { type: 'ACK_RAGE_SURGE' }
  | { type: 'END_ROUND'; damage?: number }
  | { type: 'SURRENDER' };

export type BattleEvent =
  | { type: 'DAMAGE_ACCUMULATED'; amount: number }
  | { type: 'DAMAGE_REDUCED'; amount: number }
  | { type: 'WOUNDS_INFLICTED'; count: number }
  | { type: 'DAMAGE_BURNED'; amount: number }
  | { type: 'STANCE_CHANGED'; stance: number }
  | { type: 'HEALED' }
  | { type: 'RAGE_CHANGED'; rage: number }
  | { type: 'RAGE_SURGE' }
  | { type: 'ROUND_STARTED'; round: number }
  | { type: 'STATUS_CHANGED'; hardened: boolean; resilient: boolean }
  | { type: 'VICTORY' }
  | { type: 'DEFEAT'; reason: DefeatReason }
  | { type: 'UNDONE'; description: string };

/** Почему команда отклонена; состояние при этом не меняется. */
export type RejectionReason =
  | 'BATTLE_OVER' // бой окончен
  | 'STANCE_PENDING' // ждёт подтверждения новой стойки
  | 'RAGE_SURGE_PENDING' // ждёт подтверждения выплеска ярости
  | 'NO_PENDING_STANCE' // подтверждать нечего
  | 'NO_RAGE_SURGE' // выплеска ярости нет
  | 'HEALTH_FULL' // здоровье уже максимальное
  | 'STANCE_NOT_ON_DEMAND' // стойка меняется не по запросу
  | 'LAST_STANCE' // стойка IX — последняя
  | 'INVALID_VALUE'; // значение команды вне допустимых

export type CommandResult =
  | { ok: true; state: BattleState; events: BattleEvent[]; message: string }
  | { ok: false; state: BattleState; reason: RejectionReason; message: string };

// --- Бой в браузере (doc/battle.md §6–7) ---

/** Снимок перед командой: отмена восстанавливает его целиком, включая раунд (D-4). */
export interface HistoryEntry {
  state: BattleState;
  description: string; // «урон +13», «завершение раунда 4»
}

export type BattleMode = 'EXPEDITION' | 'CAMPAIGN';
export type BattlePurpose = 'PROLOGUE' | 'QUEST' | 'FREE' | 'FINAL';
export type Difficulty = 0 | 1 | 2 | 3;

/** Кампания боя — снимок на момент начала боя. */
export interface CampaignBattleInfo {
  id: number;
  name: string;
  chapter: number;
  progressSeq: number;
  purpose: BattlePurpose;
  questNumber: number | null;
  startMarked: boolean; // отметка о начале дошла до сервера
}

export interface BattleBoss {
  code: string;
  name: string;
  element: string | null;
}

export interface BattleSubmission {
  status: 'NOT_SENT' | 'SENT';
  lastError: string | null;
}

export const LOCAL_BATTLE_SCHEMA_VERSION = 1;

/** Текущий бой браузера — запись `localStorage` `primal.battle`. */
export interface LocalBattle {
  schemaVersion: typeof LOCAL_BATTLE_SCHEMA_VERSION;
  id: string; // randomId() (UUID v4); в кампании — id боя на сервере
  mode: BattleMode;
  campaign: CampaignBattleInfo | null; // только для кампании
  boss: BattleBoss | null; // null — ручной ввод
  difficulty: Difficulty;
  stances: StanceDef[]; // стойки босса этой сложности — бой не зависит от сети
  state: BattleState;
  history: HistoryEntry[]; // ≤ 10, последняя команда — в конце
  submission: BattleSubmission | null; // только для кампании
  startedAt: string;
  updatedAt: string;
  finishedAt: string | null;
}
