import { describe, expect, it } from 'vitest';
import { applyCommand, canApply, createBattle, validateNewBattle } from './engine';
import type {
  BattleCommand,
  BattleState,
  CommandResult,
  MonsterState,
  NewBattleParams,
  StanceChange,
  StanceDef,
} from './types';

// Сценарии перенесены из app: model/MonsterTest.kt и игровые сценарии viewmodel/BattleViewModelTest.kt.

const health = (atHealth: number): StanceChange => ({ mode: 'HEALTH', atHealth });
const ON_DEMAND: StanceChange = { mode: 'ON_DEMAND' };
const FINAL: StanceChange = { mode: 'FINAL' };

/** Снимок стоек из каталога: Вираксен, 1 охотник. */
const VIRAXEN: StanceDef[] = [
  { stance: 1, toughnessPerHunter: 4, stanceChange: health(7) },
  { stance: 2, toughnessPerHunter: 6, stanceChange: health(4) },
];

/** Коровон: стойка II без порога раны и со сменой по запросу. */
const KOROVON: StanceDef[] = [
  { stance: 1, toughnessPerHunter: 2, stanceChange: health(6) },
  { stance: 2, toughnessPerHunter: null, stanceChange: ON_DEMAND },
  { stance: 3, toughnessPerHunter: 4, stanceChange: FINAL },
];

/** Бой как в BattleViewModelTest.createViewModel: 4 охотника, прочность 1 за охотника, смена при 7. */
function battle(params: Partial<NewBattleParams> = {}, monster: Partial<MonsterState> = {}): BattleState {
  const state = createBattle({ hunterCount: 4, toughnessPerHunter: 1, stanceChange: health(7), ...params });
  return deepFreeze({ ...state, monster: { ...state.monster, ...monster } });
}

/** Входное состояние заморожено: попытка движка изменить его — ошибка. */
function apply(state: BattleState, command: BattleCommand, stances: readonly StanceDef[] = []): CommandResult {
  return applyCommand(deepFreeze(state), command, deepFreeze(stances));
}

function accepted(result: CommandResult): Extract<CommandResult, { ok: true }> {
  if (!result.ok) throw new Error(`Команда отклонена: ${result.reason} — ${result.message}`);
  return result;
}

function run(state: BattleState, commands: BattleCommand[], stances: readonly StanceDef[] = []): BattleState {
  return commands.reduce((current, command) => accepted(apply(current, command, stances)).state, state);
}

/** Завершает раунды, подтверждая выплески ярости. */
function endRounds(state: BattleState, count: number): CommandResult {
  let current = state;
  let result: CommandResult | null = null;
  for (let i = 0; i < count; i++) {
    if (current.rageSurgePending) current = accepted(apply(current, { type: 'ACK_RAGE_SURGE' })).state;
    result = apply(current, { type: 'END_ROUND' });
    current = accepted(result).state;
  }
  if (result === null) throw new Error('Нужен хотя бы один раунд');
  return result;
}

function deepFreeze<T>(value: T): T {
  if (value !== null && typeof value === 'object') {
    Object.freeze(value);
    Object.values(value).forEach(deepFreeze);
  }
  return value;
}

describe('Начало боя', () => {
  it('здоровье 10, ярость = число охотников, раунд 1, стойка I, прочность = за охотника × охотники', () => {
    // вызов
    const state = createBattle({ hunterCount: 4, toughnessPerHunter: 2, stanceChange: health(7) });

    // проверка
    expect(state).toEqual({
      status: 'IN_PROGRESS',
      defeatReason: null,
      round: 1,
      maxRounds: 10,
      hunterCount: 4,
      monster: {
        stance: 1,
        health: 10,
        accumulatedDamage: 0,
        toughness: 8,
        stanceChange: health(7),
        rage: 4,
        hardened: false,
        resilient: false,
      },
      pendingStance: null,
      rageSurgePending: false,
    });
  });

  it('пустая прочность — стойка без порога раны', () => {
    // вызов
    const state = createBattle({ hunterCount: 2, toughnessPerHunter: null, stanceChange: health(7) });

    // проверка
    expect(state.monster.toughness).toBeNull();
    expect(state.hunterCount).toBe(2);
  });

  it('число охотников не ограничено сверху (D-12): 12 охотников', () => {
    // вызов
    const state = createBattle({ hunterCount: 12, toughnessPerHunter: 1, stanceChange: ON_DEMAND });

    // проверка
    expect(state.monster.toughness).toBe(12);
    expect(state.monster.rage).toBe(12);
  });

  it.each<[string, NewBattleParams]>([
    ['0 охотников', { hunterCount: 0, toughnessPerHunter: 1, stanceChange: ON_DEMAND }],
    ['дробное число охотников', { hunterCount: 2.5, toughnessPerHunter: 1, stanceChange: ON_DEMAND }],
    ['прочность 0', { hunterCount: 2, toughnessPerHunter: 0, stanceChange: ON_DEMAND }],
    ['отрицательная прочность', { hunterCount: 2, toughnessPerHunter: -1, stanceChange: ON_DEMAND }],
    ['порог смены стойки 0', { hunterCount: 2, toughnessPerHunter: 1, stanceChange: health(0) }],
    ['порог смены стойки 10', { hunterCount: 2, toughnessPerHunter: 1, stanceChange: health(10) }],
  ])('недопустимые параметры: %s', (_, params) => {
    // вызов и проверка
    expect(validateNewBattle(params)).not.toBeNull();
    expect(() => createBattle(params)).toThrow(RangeError);
  });
});

describe('Урон и раны', () => {
  it('на каждые «прочность» урона — одна рана', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 8 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 8, accumulatedDamage: 0, stance: 1 });
    expect(result.message).toBe('Нанесено ран: 2.');
    expect(result.events).toEqual([
      { type: 'DAMAGE_ACCUMULATED', amount: 8 },
      { type: 'WOUNDS_INFLICTED', count: 2 },
    ]);
  });

  it('остаток меньше прочности остаётся накопленным', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 5 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 9, accumulatedDamage: 1 });
  });

  it('урона меньше прочности — рана не нанесена', () => {
    // подготовка
    const state = battle();

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 3 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 10, accumulatedDamage: 3 });
    expect(result.message).toBe('Урон накоплен, но рана не нанесена.');
    expect(result.events).toEqual([{ type: 'DAMAGE_ACCUMULATED', amount: 3 }]);
  });

  it('здоровье 0 — победа сразу, не дожидаясь конца раунда', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 }, { health: 1 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 4 }));

    // проверка
    expect(result.state.status).toBe('VICTORY');
    expect(result.state.monster.health).toBe(0);
    expect(result.state.round).toBe(1);
    expect(result.message).toBe('Монстр побеждён! Нанесено ран: 1');
    expect(result.events).toContainEqual({ type: 'VICTORY' });
  });

  it('фатальный урон без смены стойки по здоровью: все 10 ран, победа', () => {
    // подготовка
    const state = battle({ stanceChange: ON_DEMAND });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 100 }));

    // проверка
    expect(result.state.status).toBe('VICTORY');
    expect(result.state.monster.health).toBe(0);
    expect(result.message).toBe('Монстр побеждён! Нанесено ран: 10');
  });

  it('здоровье дошло до порога — стойка меняется, окно ждёт подтверждения', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(8) });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 8 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 8, stance: 2, toughness: 4 });
    expect(result.state.pendingStance).toEqual({ stance: 2, carriedDamage: 0, carriedDamageResets: false, prefill: null });
    expect(result.message).toBe('Нанесено ран: 2. Монстр перешёл на стойку 2!');
    expect(result.events).toContainEqual({ type: 'STANCE_CHANGED', stance: 2 });
  });

  it('цикл ран останавливается на смене стойки, остаток переносится (R-2)', () => {
    // подготовка: 2 охотника × 2 = прочность 4, смена при 7
    const state = battle({ hunterCount: 2, toughnessPerHunter: 2, stanceChange: health(7) });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 15 }));

    // проверка: 3 раны до порога, 3 урона ждут новой прочности
    expect(result.state.monster).toMatchObject({ health: 7, stance: 2, accumulatedDamage: 3 });
    expect(result.state.pendingStance?.carriedDamage).toBe(3);
    expect(result.events).toContainEqual({ type: 'WOUNDS_INFLICTED', count: 3 });
  });

  it('на стойке IX стойка больше не меняется', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(2) }, { stance: 9, health: 3 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 4 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 2, stance: 9 });
    expect(result.state.pendingStance).toBeNull();
    expect(result.message).toBe('Нанесено ран: 1.');
  });

  it.each([
    ['по запросу', ON_DEMAND],
    ['последняя стойка', FINAL],
  ])('стойка со сменой «%s» от ран не меняется', (_, stanceChange) => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 16 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 6, stance: 1 });
    expect(result.state.pendingStance).toBeNull();
  });
});

describe('Пример из книги: Вираксен', () => {
  // прочность 4 (2 охотника × 2), смена стойки при 7, здоровье 9
  const snapshot: StanceDef[] = [
    { stance: 1, toughnessPerHunter: 2, stanceChange: health(7) },
    { stance: 2, toughnessPerHunter: 3, stanceChange: health(4) },
  ];
  const start = battle({ hunterCount: 2, toughnessPerHunter: 2, stanceChange: health(7) }, { health: 9 });

  it('урон 13 → 2 раны, стойка II ждёт подтверждения, перенесено 5', () => {
    // вызов
    const result = accepted(apply(start, { type: 'DEAL_DAMAGE', amount: 13 }, snapshot));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 7, stance: 2, accumulatedDamage: 5 });
    expect(result.state.pendingStance).toEqual({
      stance: 2,
      carriedDamage: 5,
      carriedDamageResets: false,
      prefill: snapshot[1],
    });
    expect(result.message).toBe('Нанесено ран: 2. Монстр перешёл на стойку 2!');
  });

  it('после подтверждения прочности 6 третьей раны нет', () => {
    // подготовка
    const afterDamage = run(start, [{ type: 'DEAL_DAMAGE', amount: 13 }], snapshot);

    // вызов
    const result = accepted(
      apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 3, stanceChange: health(4) }, snapshot),
    );

    // проверка
    expect(result.state.monster).toMatchObject({ toughness: 6, health: 7, accumulatedDamage: 5, stance: 2 });
    expect(result.state.monster.stanceChange).toEqual(health(4));
    expect(result.state.pendingStance).toBeNull();
    expect(result.events).toEqual([]);
    expect(result.message).toBe('Стойка 2. Урон для раны: 6, смена: 4 HP');
  });
});

describe('Подтверждение стойки', () => {
  const oneHunter = { hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(7) };

  it('перенесённый урон сразу наносится с новой прочностью (R-2)', () => {
    // подготовка: 20 урона = 3 раны до порога, 8 урона переносится
    const afterDamage = run(battle(oneHunter), [{ type: 'DEAL_DAMAGE', amount: 20 }]);
    expect(afterDamage.monster).toMatchObject({ health: 7, accumulatedDamage: 8, stance: 2 });

    // вызов
    const result = accepted(
      apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 6, stanceChange: health(3) }),
    );

    // проверка
    expect(result.state.monster).toMatchObject({ health: 6, accumulatedDamage: 2, toughness: 6 });
    expect(result.state.pendingStance).toBeNull();
    expect(result.message).toBe('Стойка 2. Урон для раны: 6, смена: 3 HP. Нанесено ран: 1.');
    expect(result.events).toEqual([{ type: 'WOUNDS_INFLICTED', count: 1 }]);
  });

  it('окно предзаполнено стойкой из снимка каталога', () => {
    // вызов
    const result = accepted(apply(battle(oneHunter), { type: 'DEAL_DAMAGE', amount: 20 }, VIRAXEN));

    // проверка
    expect(result.state.pendingStance?.prefill).toEqual({ stance: 2, toughnessPerHunter: 6, stanceChange: health(4) });
  });

  it('значения окна можно исправить перед подтверждением', () => {
    // подготовка
    const afterDamage = run(battle(oneHunter), [{ type: 'DEAL_DAMAGE', amount: 20 }], VIRAXEN);

    // вызов: игрок заменил прочность 6 на 8
    const result = accepted(
      apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 8, stanceChange: health(4) }, VIRAXEN),
    );

    // проверка
    expect(result.state.monster).toMatchObject({ toughness: 8, health: 6, accumulatedDamage: 0 });
  });

  it('стойки нет в снимке каталога — окно пустое', () => {
    // вызов
    const result = accepted(apply(battle(oneHunter), { type: 'DEAL_DAMAGE', amount: 12 }, VIRAXEN.slice(0, 1)));

    // проверка
    expect(result.state.pendingStance).toMatchObject({ stance: 2, prefill: null });
  });

  it('бой с ручным вводом (снимок пуст) — окно пустое', () => {
    // вызов
    const result = accepted(apply(battle(oneHunter), { type: 'DEAL_DAMAGE', amount: 12 }, []));

    // проверка
    expect(result.state.pendingStance?.prefill).toBeNull();
  });

  it('прочность новой стойки — за охотника × охотники', () => {
    // подготовка
    const afterDamage = run(battle({ hunterCount: 3, toughnessPerHunter: 1 }), [{ type: 'DEAL_DAMAGE', amount: 9 }]);

    // вызов
    const result = accepted(apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 2, stanceChange: FINAL }));

    // проверка
    expect(result.state.monster.toughness).toBe(6);
    expect(result.message).toBe('Стойка 2. Урон для раны: 6, смена: нет (последняя стойка)');
  });

  it('перенесённый урон может снова довести до смены стойки', () => {
    // подготовка: прочность 2, смена при 9: 10 урона → 1 рана, стойка II, перенесено 8
    const afterDamage = run(battle({ hunterCount: 1, toughnessPerHunter: 2, stanceChange: health(9) }), [
      { type: 'DEAL_DAMAGE', amount: 10 },
    ]);
    expect(afterDamage.pendingStance?.carriedDamage).toBe(8);

    // вызов: стойка II — прочность 2, смена при 7
    const result = accepted(apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 2, stanceChange: health(7) }));

    // проверка: 2 раны, стойка III ждёт подтверждения с 4 перенесёнными
    expect(result.state.monster).toMatchObject({ health: 7, stance: 3, accumulatedDamage: 4 });
    expect(result.state.pendingStance).toMatchObject({ stance: 3, carriedDamage: 4 });
    expect(result.message).toBe('Стойка 2. Урон для раны: 2, смена: 7 HP. Нанесено ран: 2. Монстр перешёл на стойку 3!');
  });

  it('перенесённый урон может победить монстра', () => {
    // подготовка: здоровье 2 → 1 рана, стойка II, перенесено 4
    const afterDamage = run(battle(oneHunter, { health: 2 }), [{ type: 'DEAL_DAMAGE', amount: 8 }]);

    // вызов
    const result = accepted(apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: FINAL }));

    // проверка
    expect(result.state.status).toBe('VICTORY');
    expect(result.state.monster.health).toBe(0);
    expect(result.message).toBe('Монстр побеждён! Нанесено ран: 1');
  });

  it('переданное здоровье устанавливается', () => {
    // подготовка
    const afterDamage = run(battle(oneHunter), [{ type: 'DEAL_DAMAGE', amount: 12 }]);

    // вызов
    const result = accepted(
      apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 5, stanceChange: health(3), health: 9 }),
    );

    // проверка
    expect(result.state.monster.health).toBe(9);
  });

  it.each<[string, BattleCommand]>([
    ['прочность 0', { type: 'CONFIRM_STANCE', toughnessPerHunter: 0, stanceChange: FINAL }],
    ['дробная прочность', { type: 'CONFIRM_STANCE', toughnessPerHunter: 1.5, stanceChange: FINAL }],
    ['порог смены 0', { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: health(0) }],
    ['порог смены 10', { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: health(10) }],
    ['здоровье 0', { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: FINAL, health: 0 }],
    ['здоровье 11', { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: FINAL, health: 11 }],
  ])('недопустимое значение отклоняется: %s', (_, command) => {
    // подготовка
    const afterDamage = run(battle(oneHunter), [{ type: 'DEAL_DAMAGE', amount: 12 }]);

    // вызов
    const result = apply(afterDamage, command);

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'INVALID_VALUE' });
    expect(result.state).toBe(afterDamage);
  });
});

describe('«Затвердевший» и «Устойчивость стойки» (R-3)', () => {
  it('«Затвердевший»: после ран остаток урона сгорает', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 }, { hardened: true });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 9 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 8, accumulatedDamage: 0 });
    expect(result.events).toContainEqual({ type: 'DAMAGE_BURNED', amount: 1 });
  });

  it('«Затвердевший» без ран: урон копится', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 }, { hardened: true });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 3 }));

    // проверка
    expect(result.state.monster.accumulatedDamage).toBe(3);
    expect(result.events.map((event) => event.type)).not.toContain('DAMAGE_BURNED');
  });

  it('без «Затвердевшего» остаток после ран сохраняется', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 9 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 8, accumulatedDamage: 1 });
  });

  it('«Затвердевший» на смене стойки: остаток сгорает и не переносится', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(9) }, { hardened: true });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 6 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 9, stance: 2, accumulatedDamage: 0 });
    expect(result.state.pendingStance?.carriedDamage).toBe(0);
    expect(result.events).toContainEqual({ type: 'DAMAGE_BURNED', amount: 2 });
  });

  it('«Устойчивость стойки»: накопленный урон не переносится на новую стойку', () => {
    // подготовка: 6 урона → 1 рана и смена стойки, 2 урона остаются
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(9) }, { resilient: true });
    const afterDamage = run(state, [{ type: 'DEAL_DAMAGE', amount: 6 }]);
    expect(afterDamage.pendingStance).toMatchObject({ carriedDamage: 2, carriedDamageResets: true });

    // вызов
    const result = accepted(apply(afterDamage, { type: 'CONFIRM_STANCE', toughnessPerHunter: 1, stanceChange: health(5) }));

    // проверка: при прочности 1 перенесённые 2 урона дали бы 2 раны
    expect(result.state.monster).toMatchObject({ accumulatedDamage: 0, health: 9, resilient: true, hardened: false });
  });

  it('«Затвердевший» без «Устойчивости» переносит накопленный урон на новую стойку', () => {
    // подготовка: окно стойки II открыто, на карте 5 урона
    const state = deepFreeze({
      ...battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: health(3) }, { stance: 2, health: 7, accumulatedDamage: 5, hardened: true }),
      pendingStance: { stance: 2, carriedDamage: 5, carriedDamageResets: false, prefill: null },
    });

    // вызов
    const result = accepted(apply(state, { type: 'CONFIRM_STANCE', toughnessPerHunter: 6, stanceChange: health(2) }));

    // проверка
    expect(result.state.monster).toMatchObject({ accumulatedDamage: 5, health: 7 });
  });

  it('статусы переключаются независимо', () => {
    // подготовка
    const state = battle();

    // вызов
    const hardened = accepted(apply(state, { type: 'SET_STATUS', hardened: true }));
    const resilient = accepted(apply(hardened.state, { type: 'SET_STATUS', resilient: true }));
    const result = accepted(apply(resilient.state, { type: 'SET_STATUS', hardened: false }));

    // проверка
    expect(result.state.monster).toMatchObject({ hardened: false, resilient: true });
    expect([hardened.message, resilient.message, result.message]).toEqual([
      'Монстр затвердевший',
      'Стойка с устойчивостью',
      'Монстр не затвердевший',
    ]);
    expect(result.events).toEqual([{ type: 'STATUS_CHANGED', hardened: false, resilient: true }]);
  });

  it('«Устойчивость», включённая при открытом окне смены стойки, отражается в окне', () => {
    // подготовка
    const afterDamage = run(battle({ hunterCount: 1, toughnessPerHunter: 4 }), [{ type: 'DEAL_DAMAGE', amount: 14 }]);
    expect(afterDamage.pendingStance?.carriedDamageResets).toBe(false);

    // вызов
    const result = accepted(apply(afterDamage, { type: 'SET_STATUS', resilient: true }));

    // проверка
    expect(result.state.pendingStance).toMatchObject({ carriedDamage: 2, carriedDamageResets: true });
  });
});

describe('Заживление раны (R-4)', () => {
  it('здоровье +1, накопленный урон не меняется', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 }, { health: 6, accumulatedDamage: 3 });

    // вызов
    const result = accepted(apply(state, { type: 'HEAL_WOUND' }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 7, accumulatedDamage: 3 });
    expect(result.message).toBe('Рана заживлена. Здоровье: 7');
    expect(result.events).toEqual([{ type: 'HEALED' }]);
  });

  it('здоровье не поднимается выше начального', () => {
    // подготовка
    const state = battle();

    // вызов
    const result = apply(state, { type: 'HEAL_WOUND' });

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'HEALTH_FULL', message: 'Здоровье уже максимальное' });
    expect(result.state).toBe(state);
  });
});

describe('Отрицательный урон (D-3)', () => {
  it('уменьшает только накопленный урон, раны не заживляет', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4 }, { health: 8, accumulatedDamage: 2 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: -7 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 8, accumulatedDamage: 0 });
    expect(result.message).toBe('Накопленный урон уменьшен на 2.');
    expect(result.events).toEqual([{ type: 'DAMAGE_REDUCED', amount: 2 }]);
  });

  it('на стойке без прочности уменьшает накопленный урон', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: null }, { accumulatedDamage: 8 });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: -3 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 10, accumulatedDamage: 5 });
  });

  it('накопленного урона нет — ничего не меняется', () => {
    // подготовка
    const state = battle();

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: -5 }));

    // проверка
    expect(result.state).toEqual(state);
    expect(result.message).toBe('Накопленного урона нет.');
    expect(result.events).toEqual([]);
  });
});

describe('Стойка без прочности (Коровон)', () => {
  it('урон только копится, раны не наносятся', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: null });

    // вызов
    const result = accepted(apply(state, { type: 'DEAL_DAMAGE', amount: 15 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 10, accumulatedDamage: 15, stance: 1 });
    expect(result.state.status).toBe('IN_PROGRESS');
    expect(result.message).toBe('Урон накоплен, но рана не нанесена (нет порога раны).');
  });

  it('после подтверждения стойки с прочностью накопленный урон сразу наносит раны', () => {
    // подготовка: 1 охотник; 8 урона = 4 раны (прочность 2), здоровье 6 → стойка II без порога раны
    const start = battle({ hunterCount: 1, toughnessPerHunter: 2, stanceChange: health(6) });
    const stanceII = accepted(apply(start, { type: 'DEAL_DAMAGE', amount: 8 }, KOROVON));
    expect(stanceII.state.pendingStance?.prefill).toEqual(KOROVON[1]);
    const accumulating = run(
      stanceII.state,
      [
        { type: 'CONFIRM_STANCE', toughnessPerHunter: null, stanceChange: ON_DEMAND },
        { type: 'DEAL_DAMAGE', amount: 20 },
      ],
      KOROVON,
    );
    expect(accumulating.monster).toMatchObject({ stance: 2, toughness: null, accumulatedDamage: 20, health: 6 });

    // вызов: смена стойки по запросу II → III, подтверждение предзаполненных значений
    const stanceIII = accepted(apply(accumulating, { type: 'CHANGE_STANCE' }, KOROVON));
    expect(stanceIII.state.pendingStance).toEqual({
      stance: 3,
      carriedDamage: 20,
      carriedDamageResets: false,
      prefill: KOROVON[2],
    });
    const result = accepted(
      apply(stanceIII.state, { type: 'CONFIRM_STANCE', toughnessPerHunter: 4, stanceChange: FINAL }, KOROVON),
    );

    // проверка: 20 урона = 5 ран, здоровье 6 → 1
    expect(result.state.monster).toMatchObject({ toughness: 4, accumulatedDamage: 0, health: 1, stance: 3 });
    expect(result.state.pendingStance).toBeNull();
    expect(result.events).toEqual([{ type: 'WOUNDS_INFLICTED', count: 5 }]);
  });
});

describe('Смена стойки по запросу', () => {
  it('«Сменить стойку» переводит на следующую стойку и открывает окно', () => {
    // подготовка
    const state = battle({ hunterCount: 1, toughnessPerHunter: 4, stanceChange: ON_DEMAND }, { accumulatedDamage: 3 });

    // вызов
    const result = accepted(apply(state, { type: 'CHANGE_STANCE' }));

    // проверка
    expect(result.state.monster.stance).toBe(2);
    expect(result.state.pendingStance).toEqual({ stance: 2, carriedDamage: 3, carriedDamageResets: false, prefill: null });
    expect(result.message).toBe('Монстр перешёл на стойку 2!');
    expect(result.events).toEqual([{ type: 'STANCE_CHANGED', stance: 2 }]);
  });

  it.each([
    ['по здоровью', health(7)],
    ['последняя стойка', FINAL],
  ])('стойка со сменой «%s» по запросу не меняется', (_, stanceChange) => {
    // подготовка
    const state = battle({ stanceChange });

    // вызов
    const result = apply(state, { type: 'CHANGE_STANCE' });

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'STANCE_NOT_ON_DEMAND' });
    expect(result.state).toBe(state);
  });

  it('после стойки IX стоек нет', () => {
    // подготовка
    const state = battle({ stanceChange: ON_DEMAND }, { stance: 9 });

    // вызов
    const result = apply(state, { type: 'CHANGE_STANCE' });

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'LAST_STANCE' });
  });
});

describe('Ярость и выплеск ярости (R-8)', () => {
  it('кнопки ярости: −1, +1, +охотники, +(охотники − 1)', () => {
    // подготовка: 4 охотника, ярость 4
    const state = battle();

    // вызов
    const results = [-1, 1, 4, 3].reduce<CommandResult[]>((acc, delta) => {
      const current = acc.at(-1)?.state ?? state;
      return [...acc, accepted(apply(current, { type: 'ADJUST_RAGE', delta }))];
    }, []);

    // проверка
    expect(results.map((result) => result.state.monster.rage)).toEqual([3, 4, 8, 11]);
    expect(results.map((result) => result.message)).toEqual(['Ярость: 3', 'Ярость: 4', 'Ярость: 8', 'Ярость: 11']);
    expect(results.some((result) => result.state.rageSurgePending)).toBe(false);
  });

  it('ярость не опускается ниже 0', () => {
    // подготовка
    const state = battle({}, { rage: 2 });

    // вызов
    const result = accepted(apply(state, { type: 'ADJUST_RAGE', delta: -5 }));

    // проверка
    expect(result.state.monster.rage).toBe(0);
  });

  it('ярость выросла до 3 за охотника — выплеск; подтверждение сбрасывает ярость до 1 за охотника', () => {
    // подготовка: 4 охотника, ярость 4 → +8 = 12
    const surge = accepted(apply(battle(), { type: 'ADJUST_RAGE', delta: 8 }));
    expect(surge.state.rageSurgePending).toBe(true);
    expect(surge.events).toEqual([{ type: 'RAGE_CHANGED', rage: 12 }, { type: 'RAGE_SURGE' }]);

    // вызов
    const result = accepted(apply(surge.state, { type: 'ACK_RAGE_SURGE' }));

    // проверка
    expect(result.state.monster.rage).toBe(4);
    expect(result.state.rageSurgePending).toBe(false);
    expect(result.message).toBe(
      'Выплеск ярости! Каждый охотник получил урон, равный силе монстра. Ярость сброшена до 4',
    );
  });

  it('снижение ярости выплеска не вызывает', () => {
    // подготовка
    const state = battle({}, { rage: 14 });

    // вызов
    const result = accepted(apply(state, { type: 'ADJUST_RAGE', delta: -1 }));

    // проверка
    expect(result.state.monster.rage).toBe(13);
    expect(result.state.rageSurgePending).toBe(false);
  });

  it('изменение ярости на 0 отклоняется', () => {
    // вызов
    const result = apply(battle(), { type: 'ADJUST_RAGE', delta: 0 });

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'INVALID_VALUE' });
  });
});

describe('Конец раунда', () => {
  it('без урона: следующий раунд, ярость + число охотников', () => {
    // вызов
    const result = accepted(apply(battle(), { type: 'END_ROUND' }));

    // проверка
    expect(result.state.round).toBe(2);
    expect(result.state.monster.rage).toBe(8);
    expect(result.message).toBe('Раунд 2. Ярость: 8');
    expect(result.events).toEqual([
      { type: 'RAGE_CHANGED', rage: 8 },
      { type: 'ROUND_STARTED', round: 2 },
    ]);
  });

  it('введённый урон наносится до конца раунда', () => {
    // вызов
    const result = accepted(apply(battle(), { type: 'END_ROUND', damage: 3 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 10, accumulatedDamage: 3, rage: 8 });
    expect(result.state.round).toBe(2);
    expect(result.message).toBe('Урон накоплен, но рана не нанесена. Раунд 2. Ярость: 8');
  });

  it.each([false, true])('смена стойки уроном: раунд завершается, окно стойки остаётся (затвердевший: %s)', (hardened) => {
    // подготовка: прочность 4, смена при 7; 12 урона = 3 раны
    const state = battle({}, { hardened });

    // вызов
    const result = accepted(apply(state, { type: 'END_ROUND', damage: 12 }));

    // проверка
    expect(result.state.monster).toMatchObject({ health: 7, stance: 2, rage: 8, accumulatedDamage: 0 });
    expect(result.state.round).toBe(2);
    expect(result.state.pendingStance).toMatchObject({ stance: 2 });
  });

  it('победа уроном: раунд не завершается, ярость не растёт', () => {
    // подготовка
    const state = battle({ stanceChange: ON_DEMAND });

    // вызов
    const result = accepted(apply(state, { type: 'END_ROUND', damage: 40 }));

    // проверка
    expect(result.state.status).toBe('VICTORY');
    expect(result.state.round).toBe(1);
    expect(result.state.monster.rage).toBe(4);
    expect(result.message).toBe('Монстр побеждён! Нанесено ран: 10');
  });

  it('победа в 10-м раунде важнее поражения по раундам', () => {
    // подготовка
    const state = battle({ stanceChange: ON_DEMAND });
    const roundTen = accepted(endRounds(state, 9)).state;
    expect(roundTen.round).toBe(10);

    // вызов
    const result = accepted(apply(roundTen, { type: 'END_ROUND', damage: 40 }));

    // проверка
    expect(result.state.status).toBe('VICTORY');
  });

  it('после 10-го раунда — поражение по раундам, а не сдача (D-14)', () => {
    // вызов
    const result = accepted(endRounds(battle(), 10));

    // проверка
    expect(result.state.status).toBe('DEFEAT');
    expect(result.state.defeatReason).toBe('ROUNDS');
    expect(result.state.round).toBe(11);
    expect(result.message).toBe('Поражение! Прошло 10 раундов.');
    expect(result.events).toContainEqual({ type: 'DEFEAT', reason: 'ROUNDS' });
  });

  it('поражение по раундам закрывает окно смены стойки и не вызывает выплеск', () => {
    // подготовка: 10-й раунд, ярость близко к выплеску
    const state = deepFreeze({ ...battle({}, { rage: 10 }), round: 10 });

    // вызов
    const result = accepted(apply(state, { type: 'END_ROUND', damage: 12 }));

    // проверка
    expect(result.state.status).toBe('DEFEAT');
    expect(result.state.pendingStance).toBeNull();
    expect(result.state.rageSurgePending).toBe(false);
    expect(result.events.map((event) => event.type)).not.toContain('RAGE_SURGE');
    expect(result.message).toBe('Нанесено ран: 3. Монстр перешёл на стойку 2! Поражение! Прошло 10 раундов.');
  });

  it('ярость конца раунда может вызвать выплеск', () => {
    // подготовка: 4 охотника, ярость 4 → 8 → 12
    const roundTwo = run(battle(), [{ type: 'END_ROUND' }]);
    expect(roundTwo.rageSurgePending).toBe(false);

    // вызов
    const result = accepted(apply(roundTwo, { type: 'END_ROUND' }));

    // проверка
    expect(result.state.rageSurgePending).toBe(true);
    expect(result.events).toContainEqual({ type: 'RAGE_SURGE' });
  });

  it('отрицательный введённый урон уменьшает накопленный урон', () => {
    // подготовка
    const state = battle({}, { accumulatedDamage: 3 });

    // вызов
    const result = accepted(apply(state, { type: 'END_ROUND', damage: -3 }));

    // проверка
    expect(result.state.monster.accumulatedDamage).toBe(0);
    expect(result.state.round).toBe(2);
  });
});

describe('Сдаться (D-14)', () => {
  it('поражение с причиной «Сдаться»', () => {
    // вызов
    const result = accepted(apply(battle(), { type: 'SURRENDER' }));

    // проверка
    expect(result.state.status).toBe('DEFEAT');
    expect(result.state.defeatReason).toBe('SURRENDER');
    expect(result.message).toBe('Поражение! Вы сдались.');
    expect(result.events).toEqual([{ type: 'DEFEAT', reason: 'SURRENDER' }]);
  });

  it('доступно при открытом окне смены стойки и закрывает его', () => {
    // подготовка
    const afterDamage = run(battle(), [{ type: 'DEAL_DAMAGE', amount: 12 }]);

    // вызов
    const result = accepted(apply(afterDamage, { type: 'SURRENDER' }));

    // проверка
    expect(result.state.pendingStance).toBeNull();
    expect(result.state.status).toBe('DEFEAT');
  });
});

describe('Недопустимые команды не меняют состояние', () => {
  const blockedWhileWaiting: BattleCommand[] = [
    { type: 'DEAL_DAMAGE', amount: 5 },
    { type: 'HEAL_WOUND' },
    { type: 'ADJUST_RAGE', delta: 1 },
    { type: 'CHANGE_STANCE' },
    { type: 'END_ROUND', damage: 3 },
  ];

  it.each(blockedWhileWaiting)('окно смены стойки открыто: $type', (command) => {
    // подготовка
    const state = run(battle({ stanceChange: health(9) }, { health: 9 }), [{ type: 'DEAL_DAMAGE', amount: 4 }]);
    expect(state.pendingStance).not.toBeNull();

    // вызов
    const result = apply(state, command);

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'STANCE_PENDING' });
    expect(result.state).toBe(state);
  });

  it.each(blockedWhileWaiting)('окно выплеска ярости открыто: $type', (command) => {
    // подготовка
    const state = run(battle({ stanceChange: ON_DEMAND }, { health: 9 }), [{ type: 'ADJUST_RAGE', delta: 8 }]);
    expect(state.rageSurgePending).toBe(true);

    // вызов
    const result = apply(state, command);

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'RAGE_SURGE_PENDING' });
    expect(result.state).toBe(state);
  });

  it.each<BattleCommand>([
    ...blockedWhileWaiting,
    { type: 'SET_STATUS', hardened: true },
    { type: 'CONFIRM_STANCE', toughnessPerHunter: 1, stanceChange: FINAL },
    { type: 'ACK_RAGE_SURGE' },
    { type: 'SURRENDER' },
  ])('бой окончен победой: $type', (command) => {
    // подготовка
    const state = run(battle({ stanceChange: ON_DEMAND }), [{ type: 'DEAL_DAMAGE', amount: 40 }]);
    expect(state.status).toBe('VICTORY');

    // вызов
    const result = apply(state, command);

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'BATTLE_OVER' });
    expect(result.state).toBe(state);
  });

  it('после сдачи команды отклоняются', () => {
    // подготовка
    const state = run(battle(), [{ type: 'SURRENDER' }]);

    // вызов
    const result = apply(state, { type: 'DEAL_DAMAGE', amount: 1 });

    // проверка
    expect(result).toMatchObject({ ok: false, reason: 'BATTLE_OVER' });
  });

  it.each<[string, BattleCommand, string]>([
    ['подтверждение стойки без окна', { type: 'CONFIRM_STANCE', toughnessPerHunter: 1, stanceChange: FINAL }, 'NO_PENDING_STANCE'],
    ['подтверждение выплеска без выплеска', { type: 'ACK_RAGE_SURGE' }, 'NO_RAGE_SURGE'],
    ['урон 0', { type: 'DEAL_DAMAGE', amount: 0 }, 'INVALID_VALUE'],
    ['дробный урон', { type: 'DEAL_DAMAGE', amount: 2.5 }, 'INVALID_VALUE'],
    ['урон не число', { type: 'DEAL_DAMAGE', amount: Number.NaN }, 'INVALID_VALUE'],
    ['дробный урон в конце раунда', { type: 'END_ROUND', damage: 1.5 }, 'INVALID_VALUE'],
    ['статус не указан', { type: 'SET_STATUS' }, 'INVALID_VALUE'],
  ])('%s', (_, command, reason) => {
    // подготовка
    const state = battle();

    // вызов
    const result = apply(state, command);

    // проверка
    expect(result).toMatchObject({ ok: false, reason });
    expect(result.message).not.toBe('');
    expect(result.state).toBe(state);
  });

  it('canApply совпадает с решением движка', () => {
    // подготовка
    const waiting = run(battle(), [{ type: 'DEAL_DAMAGE', amount: 12 }]);

    // вызов и проверка
    expect(canApply(waiting, { type: 'DEAL_DAMAGE', amount: 1 })).toBe(false);
    expect(canApply(waiting, { type: 'CONFIRM_STANCE', toughnessPerHunter: 1, stanceChange: FINAL })).toBe(true);
    expect(canApply(battle(), { type: 'HEAL_WOUND' })).toBe(false);
    expect(canApply(battle({}, { health: 9 }), { type: 'HEAL_WOUND' })).toBe(true);
  });
});
