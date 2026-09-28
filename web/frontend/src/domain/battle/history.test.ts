import { describe, expect, it } from 'vitest';
import { canUndo, HISTORY_LIMIT, nextUndo, perform, undo } from './history';
import { createLocalBattle, isUnfinished } from './localBattle';
import { describeCommand } from './messages';
import type { BattleCommand, BattleState, LocalBattle, NewBattleParams } from './types';

const STARTED = '2026-09-27T18:00:00.000Z';
const LATER = '2026-09-27T18:05:00.000Z';

/** Экспедиция: 4 охотника, прочность 4, смена стойки по запросу. */
function expedition(params: Partial<NewBattleParams> = {}): LocalBattle {
  return createLocalBattle({
    id: 'battle-1',
    mode: 'EXPEDITION',
    campaign: null,
    boss: null,
    difficulty: 0,
    stances: [],
    params: { hunterCount: 4, toughnessPerHunter: 1, stanceChange: { mode: 'ON_DEMAND' }, ...params },
    now: STARTED,
  });
}

function campaignBattle(): LocalBattle {
  return createLocalBattle({
    id: '5f0c6f1e-0000-4000-8000-000000000001',
    mode: 'CAMPAIGN',
    campaign: { id: 12, name: 'Первая', chapter: 1, progressSeq: 3, purpose: 'QUEST', questNumber: 1, startMarked: true },
    boss: { code: 'TORAMAT', name: 'Торамат', element: 'HORN' },
    difficulty: 1,
    stances: [],
    params: { hunterCount: 3, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
    now: STARTED,
  });
}

function performAll(battle: LocalBattle, commands: BattleCommand[]): LocalBattle {
  return commands.reduce((current, command) => {
    const { result, battle: next } = perform(current, command, LATER);
    if (!result.ok) throw new Error(`Команда отклонена: ${result.message}`);
    return next;
  }, battle);
}

function undoOrFail(battle: LocalBattle): LocalBattle {
  const result = undo(battle, LATER);
  if (result === null) throw new Error('Отмена недоступна');
  return result.battle;
}

describe('История отмены', () => {
  it('перед каждой принятой командой сохраняется снимок с описанием', () => {
    // подготовка
    const battle = expedition();

    // вызов
    const { battle: next, result } = perform(battle, { type: 'DEAL_DAMAGE', amount: 3 }, LATER);

    // проверка
    expect(result.ok).toBe(true);
    expect(next.history).toEqual([{ state: battle.state, description: 'урон +3' }]);
    expect(next.state.monster.accumulatedDamage).toBe(3);
    expect(next.updatedAt).toBe(LATER);
    expect(nextUndo(next)).toBe('урон +3');
  });

  it('отклонённая команда в историю не попадает', () => {
    // подготовка
    const battle = expedition();

    // вызов
    const { battle: next, result } = perform(battle, { type: 'HEAL_WOUND' }, LATER);

    // проверка
    expect(result.ok).toBe(false);
    expect(next).toBe(battle);
  });

  it('хранится 10 последних снимков: после 11 команд доступно 10 отмен', () => {
    // подготовка
    const commands = Array.from({ length: 11 }, (): BattleCommand => ({ type: 'DEAL_DAMAGE', amount: 1 }));
    const battle = performAll(expedition(), commands);
    const afterFirst = performAll(expedition(), commands.slice(0, 1));
    expect(battle.history).toHaveLength(HISTORY_LIMIT);

    // вызов
    let current = battle;
    for (let i = 0; i < HISTORY_LIMIT; i++) current = undoOrFail(current);

    // проверка: самый старый снимок (до первой команды) потерян
    expect(current.state).toEqual(afterFirst.state);
    expect(canUndo(current)).toBe(false);
    expect(undo(current, LATER)).toBeNull();
  });

  it('отмена восстанавливает раунд и ярость (D-4)', () => {
    // подготовка
    const battle = performAll(expedition(), [{ type: 'END_ROUND' }, { type: 'END_ROUND', damage: 2 }]);
    expect(battle.state.round).toBe(3);

    // вызов
    const result = undo(battle, LATER);

    // проверка
    expect(result?.battle.state.round).toBe(2);
    expect(result?.battle.state.monster).toMatchObject({ rage: 8, accumulatedDamage: 0 });
    expect(result?.message).toBe('Отменено: завершение раунда 2');
    expect(result?.events).toEqual([{ type: 'UNDONE', description: 'завершение раунда 2' }]);
  });

  it('победу можно отменить: бой продолжается', () => {
    // подготовка
    const battle = performAll(expedition(), [{ type: 'DEAL_DAMAGE', amount: 40 }]);
    expect(battle.state.status).toBe('VICTORY');
    expect(battle.finishedAt).toBe(LATER);

    // вызов
    const result = undo(battle, LATER);

    // проверка
    expect(result?.battle.state.status).toBe('IN_PROGRESS');
    expect(result?.battle.state.monster.health).toBe(10);
    expect(result?.battle.finishedAt).toBeNull();
    expect(result?.message).toBe('Отменено: урон +40');
  });

  it('«Сдаться» можно отменить', () => {
    // подготовка
    const battle = performAll(expedition(), [{ type: 'SURRENDER' }]);

    // вызов
    const restored = undoOrFail(battle);

    // проверка
    expect(restored.state.status).toBe('IN_PROGRESS');
    expect(restored.state.defeatReason).toBeNull();
  });

  it('«Отмена» в окне смены стойки откатывает урон и стойку', () => {
    // подготовка
    const battle = performAll(expedition({ stanceChange: { mode: 'HEALTH', atHealth: 7 } }), [
      { type: 'DEAL_DAMAGE', amount: 14 },
    ]);
    expect(battle.state.pendingStance).not.toBeNull();

    // вызов
    const restored = undoOrFail(battle);

    // проверка
    expect(restored.state.pendingStance).toBeNull();
    expect(restored.state.monster).toMatchObject({ stance: 1, health: 10, accumulatedDamage: 0 });
  });

  it('после отправки результата кампании отмена недоступна', () => {
    // подготовка
    const finished = performAll(campaignBattle(), [{ type: 'SURRENDER' }]);
    const sent: LocalBattle = { ...finished, submission: { status: 'SENT', lastError: null } };

    // вызов и проверка
    expect(canUndo(finished)).toBe(true);
    expect(canUndo(sent)).toBe(false);
    expect(nextUndo(sent)).toBeNull();
    expect(undo(sent, LATER)).toBeNull();
  });
});

describe('Описания команд для отмены', () => {
  const state = expedition().state;
  const roundFour: BattleState = { ...state, round: 4 };
  const pending: BattleState = {
    ...state,
    monster: { ...state.monster, stance: 2 },
    pendingStance: { stance: 2, carriedDamage: 0, carriedDamageResets: false, prefill: null },
  };

  it.each<[BattleCommand, BattleState, string]>([
    [{ type: 'DEAL_DAMAGE', amount: 13 }, state, 'урон +13'],
    [{ type: 'DEAL_DAMAGE', amount: -3 }, state, 'снижение накопленного урона на 3'],
    [{ type: 'HEAL_WOUND' }, state, 'заживление раны'],
    [{ type: 'ADJUST_RAGE', delta: 4 }, state, 'ярость +4'],
    [{ type: 'ADJUST_RAGE', delta: -1 }, state, 'ярость -1'],
    [{ type: 'SET_STATUS', hardened: true }, state, 'включение статуса «Затвердевший»'],
    [{ type: 'SET_STATUS', resilient: false }, state, 'снятие «Устойчивости стойки»'],
    [{ type: 'CHANGE_STANCE' }, state, 'смена на стойку 2'],
    [{ type: 'CONFIRM_STANCE', toughnessPerHunter: 2, stanceChange: { mode: 'FINAL' } }, pending, 'параметры стойки 2'],
    [{ type: 'ACK_RAGE_SURGE' }, state, 'выплеск ярости'],
    [{ type: 'END_ROUND' }, roundFour, 'завершение раунда 4'],
    [{ type: 'SURRENDER' }, state, 'сдача'],
  ])('%o → «%s»', (command, before, description) => {
    // вызов и проверка
    expect(describeCommand(before, command)).toBe(description);
  });
});

describe('Новый бой браузера', () => {
  it('экспедиция: начальное состояние, пустая история, без отправки результата', () => {
    // вызов
    const battle = expedition({ hunterCount: 2, toughnessPerHunter: 3 });

    // проверка
    expect(battle).toMatchObject({
      schemaVersion: 1,
      mode: 'EXPEDITION',
      campaign: null,
      history: [],
      submission: null,
      startedAt: STARTED,
      updatedAt: STARTED,
      finishedAt: null,
    });
    expect(battle.state.monster).toMatchObject({ toughness: 6, rage: 2, health: 10 });
  });

  it('бой кампании ждёт отправки результата', () => {
    // вызов
    const battle = campaignBattle();

    // проверка
    expect(battle.submission).toEqual({ status: 'NOT_SENT', lastError: null });
  });

  it('незаконченный бой: идёт или ждёт отправки результата кампании', () => {
    // подготовка
    const expeditionWon = performAll(expedition(), [{ type: 'DEAL_DAMAGE', amount: 40 }]);
    const campaignLost = performAll(campaignBattle(), [{ type: 'SURRENDER' }]);
    const campaignSent: LocalBattle = { ...campaignLost, submission: { status: 'SENT', lastError: null } };

    // вызов и проверка
    expect(isUnfinished(expedition())).toBe(true);
    expect(isUnfinished(expeditionWon)).toBe(false);
    expect(isUnfinished(campaignLost)).toBe(true);
    expect(isUnfinished(campaignSent)).toBe(false);
  });
});
