import { battleMessages as msg, invalidValueMessages as invalid, rejectionMessages } from './messages';
import {
  MAX_HEALTH,
  MAX_ROUNDS,
  MAX_STANCE,
  RAGE_SURGE_PER_HUNTER,
  type BattleCommand,
  type BattleEvent,
  type BattleState,
  type CommandResult,
  type DefeatReason,
  type NewBattleParams,
  type PendingStance,
  type RejectionReason,
  type StanceChange,
  type StanceDef,
} from './types';

/**
 * Движок боя: перенос MonsterExt.kt и игровой части BattleViewModel.kt (doc/battle.md §2–5).
 * Чистые функции: входное состояние не меняется, результат — новое состояние, события и сообщение.
 */

/** Описание ошибки в параметрах нового боя или `null`, если параметры допустимы. */
export function validateNewBattle(params: NewBattleParams): string | null {
  if (!isPositiveInteger(params.hunterCount)) return invalid.hunterCount;
  return validateStance(params.toughnessPerHunter, params.stanceChange);
}

/** Начальное состояние: здоровье 10, ярость = число охотников, раунд 1, стойка I. */
export function createBattle(params: NewBattleParams): BattleState {
  const problem = validateNewBattle(params);
  if (problem !== null) throw new RangeError(problem);
  return {
    status: 'IN_PROGRESS',
    defeatReason: null,
    round: 1,
    maxRounds: MAX_ROUNDS,
    hunterCount: params.hunterCount,
    monster: {
      stance: 1,
      health: MAX_HEALTH,
      accumulatedDamage: 0,
      toughness: totalToughness(params.toughnessPerHunter, params.hunterCount),
      stanceChange: params.stanceChange,
      rage: params.hunterCount,
      hardened: false,
      resilient: false,
    },
    pendingStance: null,
    rageSurgePending: false,
  };
}

/**
 * Выполняет команду. `stances` — снимок стоек босса выбранной сложности (пустой при ручном вводе):
 * из него предзаполняется окно новой стойки. Недопустимая команда отклоняется, состояние не меняется.
 */
export function applyCommand(state: BattleState, command: BattleCommand, stances: readonly StanceDef[]): CommandResult {
  const rejection = check(state, command);
  if (rejection !== null) return { ok: false, state, ...rejection };

  const next: BattleState = { ...state, monster: { ...state.monster } };
  const events: BattleEvent[] = [];
  const message = execute(next, command, stances, events);
  return { ok: true, state: next, events, message };
}

/** Допустима ли команда в этом состоянии — для доступности кнопок. */
export function canApply(state: BattleState, command: BattleCommand): boolean {
  return check(state, command) === null;
}

// --- Проверка команд (doc/battle.md §3) ---

interface Rejection {
  reason: RejectionReason;
  message: string;
}

function check(state: BattleState, command: BattleCommand): Rejection | null {
  if (state.status !== 'IN_PROGRESS') return reject('BATTLE_OVER');
  switch (command.type) {
    case 'SURRENDER':
      return null;
    case 'SET_STATUS':
      return command.hardened === undefined && command.resilient === undefined ? invalidValue(invalid.status) : null;
    case 'CONFIRM_STANCE': {
      if (state.pendingStance === null) return reject('NO_PENDING_STANCE');
      const problem =
        validateStance(command.toughnessPerHunter, command.stanceChange) ??
        (command.health === undefined || isIntegerIn(command.health, 1, MAX_HEALTH) ? null : invalid.health);
      return problem === null ? null : invalidValue(problem);
    }
    case 'ACK_RAGE_SURGE':
      return state.rageSurgePending ? null : reject('NO_RAGE_SURGE');
    case 'DEAL_DAMAGE':
      return waiting(state) ?? (isNonZeroInteger(command.amount) ? null : invalidValue(invalid.damage));
    case 'HEAL_WOUND':
      return waiting(state) ?? (state.monster.health < MAX_HEALTH ? null : reject('HEALTH_FULL'));
    case 'ADJUST_RAGE':
      return waiting(state) ?? (isNonZeroInteger(command.delta) ? null : invalidValue(invalid.rage));
    case 'CHANGE_STANCE':
      return (
        waiting(state) ??
        (state.monster.stanceChange.mode !== 'ON_DEMAND'
          ? reject('STANCE_NOT_ON_DEMAND')
          : state.monster.stance >= MAX_STANCE
            ? reject('LAST_STANCE')
            : null)
      );
    case 'END_ROUND':
      return (
        waiting(state) ??
        (command.damage === undefined || Number.isInteger(command.damage) ? null : invalidValue(invalid.damage))
      );
  }
}

/** Окна «Смена стойки!» и «Выплеск ярости» модальные: пока они открыты, бой ждёт. */
function waiting(state: BattleState): Rejection | null {
  if (state.pendingStance !== null) return reject('STANCE_PENDING');
  if (state.rageSurgePending) return reject('RAGE_SURGE_PENDING');
  return null;
}

function reject(reason: RejectionReason): Rejection {
  return { reason, message: rejectionMessages[reason] };
}

function invalidValue(message: string): Rejection {
  return { reason: 'INVALID_VALUE', message };
}

function validateStance(toughnessPerHunter: number | null, stanceChange: StanceChange): string | null {
  if (toughnessPerHunter !== null && !isPositiveInteger(toughnessPerHunter)) return invalid.toughness;
  if (stanceChange.mode === 'HEALTH' && !isIntegerIn(stanceChange.atHealth, 1, MAX_HEALTH - 1)) return invalid.atHealth;
  return null;
}

// --- Выполнение команд. `state` — копия, её можно менять; вложенные объекты заменяются целиком ---

function execute(state: BattleState, command: BattleCommand, stances: readonly StanceDef[], events: BattleEvent[]): string {
  const monster = state.monster;
  switch (command.type) {
    case 'DEAL_DAMAGE':
      return inflict(state, command.amount, stances, events).message;

    case 'HEAL_WOUND':
      monster.health += 1;
      events.push({ type: 'HEALED' });
      return msg.healed(monster.health);

    case 'ADJUST_RAGE': {
      const before = setRage(state, monster.rage + command.delta, events);
      checkRageSurge(state, before, events);
      return msg.rage(monster.rage);
    }

    case 'SET_STATUS': {
      const parts: string[] = [];
      if (command.hardened !== undefined) {
        monster.hardened = command.hardened;
        parts.push(msg.hardened(command.hardened));
      }
      if (command.resilient !== undefined) {
        monster.resilient = command.resilient;
        parts.push(msg.resilient(command.resilient));
      }
      if (state.pendingStance !== null) {
        state.pendingStance = { ...state.pendingStance, carriedDamageResets: monster.resilient };
      }
      events.push({ type: 'STATUS_CHANGED', hardened: monster.hardened, resilient: monster.resilient });
      return parts.join('. ');
    }

    case 'CHANGE_STANCE':
      monster.stance += 1;
      state.pendingStance = pendingStanceFor(state, stances);
      events.push({ type: 'STANCE_CHANGED', stance: monster.stance });
      return msg.stanceChanged(monster.stance);

    case 'CONFIRM_STANCE':
      return confirmStance(state, command, stances, events);

    case 'ACK_RAGE_SURGE':
      state.rageSurgePending = false;
      setRage(state, state.hunterCount, events);
      return msg.rageSurge(monster.rage);

    case 'END_ROUND':
      return endRound(state, command.damage ?? 0, stances, events);

    case 'SURRENDER':
      finish(state, 'DEFEAT', 'SURRENDER');
      events.push({ type: 'DEFEAT', reason: 'SURRENDER' });
      return msg.surrendered;
  }
}

interface DamageOutcome {
  wounds: number;
  message: string;
}

/**
 * Урон (§4.1, `Monster.takeDamage`): раны наносятся по одной; на пороге смены стойки цикл
 * останавливается, остаток переносится на новую стойку (R-2). Отрицательный урон уменьшает только
 * накопленный урон (D-3). «Затвердевший»: после ран остаток сгорает (R-3).
 */
function inflict(state: BattleState, amount: number, stances: readonly StanceDef[], events: BattleEvent[]): DamageOutcome {
  const monster = state.monster;
  if (amount < 0) {
    const reduced = Math.min(-amount, monster.accumulatedDamage);
    if (reduced === 0) return { wounds: 0, message: msg.noAccumulatedDamage };
    monster.accumulatedDamage -= reduced;
    events.push({ type: 'DAMAGE_REDUCED', amount: reduced });
    return { wounds: 0, message: msg.damageReduced(reduced) };
  }

  monster.accumulatedDamage += amount;
  if (amount > 0) events.push({ type: 'DAMAGE_ACCUMULATED', amount });
  const toughness = monster.toughness;
  if (toughness === null) return { wounds: 0, message: msg.damageWithoutThreshold };

  let wounds = 0;
  let stanceChanged = false;
  while (monster.accumulatedDamage >= toughness) {
    monster.accumulatedDamage -= toughness;
    monster.health -= 1;
    wounds += 1;
    if (monster.health <= 0) {
      monster.health = 0;
      events.push({ type: 'WOUNDS_INFLICTED', count: wounds }, { type: 'VICTORY' });
      finish(state, 'VICTORY', null);
      return { wounds, message: msg.victory(wounds) };
    }
    const change = monster.stanceChange;
    if (change.mode === 'HEALTH' && monster.health <= change.atHealth && monster.stance < MAX_STANCE) {
      monster.stance += 1;
      stanceChanged = true;
      break; // следующие раны — с прочностью новой стойки
    }
  }

  if (wounds > 0) events.push({ type: 'WOUNDS_INFLICTED', count: wounds });
  if (monster.hardened && wounds > 0 && monster.accumulatedDamage > 0) {
    events.push({ type: 'DAMAGE_BURNED', amount: monster.accumulatedDamage });
    monster.accumulatedDamage = 0;
  }
  if (stanceChanged) {
    state.pendingStance = pendingStanceFor(state, stances);
    events.push({ type: 'STANCE_CHANGED', stance: monster.stance });
  }
  return { wounds, message: msg.damage(wounds, stanceChanged ? monster.stance : null) };
}

/** Подтверждение стойки (§4.2): перенесённый урон сразу наносится с новой прочностью. */
function confirmStance(
  state: BattleState,
  command: Extract<BattleCommand, { type: 'CONFIRM_STANCE' }>,
  stances: readonly StanceDef[],
  events: BattleEvent[],
): string {
  const monster = state.monster;
  const confirmed = monster.stance;
  monster.toughness = totalToughness(command.toughnessPerHunter, state.hunterCount);
  monster.stanceChange = command.stanceChange;
  state.pendingStance = null;
  if (monster.resilient) monster.accumulatedDamage = 0;
  if (command.health !== undefined) monster.health = command.health;

  const summary = msg.stanceConfirmed(confirmed, monster.toughness, monster.stanceChange);
  if (monster.toughness === null || monster.accumulatedDamage === 0) return summary;

  const outcome = inflict(state, 0, stances, events);
  if (state.status === 'VICTORY') return outcome.message;
  return outcome.wounds > 0 ? `${summary}. ${outcome.message}` : summary;
}

/** Конец раунда (§4.3). */
function endRound(state: BattleState, damage: number, stances: readonly StanceDef[], events: BattleEvent[]): string {
  const parts: string[] = [];
  if (damage !== 0) {
    const outcome = inflict(state, damage, stances, events);
    if (state.status === 'VICTORY') return outcome.message; // победа — раунд не завершается
    parts.push(outcome.message);
  }

  const before = setRage(state, state.monster.rage + state.hunterCount, events);
  state.round += 1;
  if (state.round > state.maxRounds) {
    finish(state, 'DEFEAT', 'ROUNDS');
    events.push({ type: 'DEFEAT', reason: 'ROUNDS' });
    parts.push(msg.defeatByRounds(state.maxRounds));
  } else {
    events.push({ type: 'ROUND_STARTED', round: state.round });
    checkRageSurge(state, before, events);
    parts.push(msg.roundStarted(state.round, state.monster.rage));
  }
  return parts.join(' ');
}

/** Ярость не опускается ниже 0. Возвращает прежнее значение. */
function setRage(state: BattleState, rage: number, events: BattleEvent[]): number {
  const before = state.monster.rage;
  state.monster.rage = Math.max(0, rage);
  if (state.monster.rage !== before) events.push({ type: 'RAGE_CHANGED', rage: state.monster.rage });
  return before;
}

/** Выплеск ярости — только при росте ярости до 3 за охотника и выше (R-8). */
function checkRageSurge(state: BattleState, rageBefore: number, events: BattleEvent[]): void {
  const rage = state.monster.rage;
  if (rage > rageBefore && rage >= RAGE_SURGE_PER_HUNTER * state.hunterCount) {
    state.rageSurgePending = true;
    events.push({ type: 'RAGE_SURGE' });
  }
}

function pendingStanceFor(state: BattleState, stances: readonly StanceDef[]): PendingStance {
  const monster = state.monster;
  return {
    stance: monster.stance,
    carriedDamage: monster.accumulatedDamage,
    carriedDamageResets: monster.resilient,
    prefill: stances.find((def) => def.stance === monster.stance) ?? null,
  };
}

function finish(state: BattleState, status: 'VICTORY' | 'DEFEAT', reason: DefeatReason | null): void {
  state.status = status;
  state.defeatReason = reason;
  state.pendingStance = null;
  state.rageSurgePending = false;
}

function totalToughness(perHunter: number | null, hunterCount: number): number | null {
  return perHunter === null ? null : perHunter * hunterCount;
}

function isPositiveInteger(value: number): boolean {
  return Number.isInteger(value) && value > 0;
}

function isNonZeroInteger(value: number): boolean {
  return Number.isInteger(value) && value !== 0;
}

function isIntegerIn(value: number, min: number, max: number): boolean {
  return Number.isInteger(value) && value >= min && value <= max;
}
