import { applyCommand } from './engine';
import { battleMessages, describeCommand } from './messages';
import type { BattleCommand, BattleEvent, BattleState, CommandResult, LocalBattle } from './types';

/** Отмена (doc/battle.md §6): перед каждой принятой командой — снимок состояния, хранятся 10 последних. */
export const HISTORY_LIMIT = 10;

export interface PerformResult {
  battle: LocalBattle; // прежний объект, если команда отклонена
  result: CommandResult;
}

export interface UndoResult {
  battle: LocalBattle;
  events: BattleEvent[];
  message: string;
}

/** Выполняет команду боя: снимок в историю отмены, время изменения и окончания боя. */
export function perform(battle: LocalBattle, command: BattleCommand, now: string): PerformResult {
  const result = applyCommand(battle.state, command, battle.stances);
  if (!result.ok) return { battle, result };

  const snapshot = { state: battle.state, description: describeCommand(battle.state, command) };
  return {
    battle: {
      ...battle,
      state: result.state,
      history: [...battle.history, snapshot].slice(-HISTORY_LIMIT),
      updatedAt: now,
      finishedAt: finishedAt(battle, result.state, now),
    },
    result,
  };
}

/** Отменить можно любую команду, в том числе победу и поражение, пока результат не отправлен. */
export function canUndo(battle: LocalBattle): boolean {
  return battle.history.length > 0 && battle.submission?.status !== 'SENT';
}

/** Что отменит «Отменить действие»: описание последней команды. */
export function nextUndo(battle: LocalBattle): string | null {
  return canUndo(battle) ? (battle.history.at(-1)?.description ?? null) : null;
}

/** Восстанавливает снимок перед последней командой целиком, включая раунд (D-4). */
export function undo(battle: LocalBattle, now: string): UndoResult | null {
  const last = battle.history.at(-1);
  if (last === undefined || !canUndo(battle)) return null;
  return {
    battle: {
      ...battle,
      state: last.state,
      history: battle.history.slice(0, -1),
      updatedAt: now,
      finishedAt: last.state.status === 'IN_PROGRESS' ? null : battle.finishedAt,
    },
    events: [{ type: 'UNDONE', description: last.description }],
    message: battleMessages.undone(last.description),
  };
}

function finishedAt(battle: LocalBattle, state: BattleState, now: string): string | null {
  if (state.status === 'IN_PROGRESS') return null;
  return battle.state.status === 'IN_PROGRESS' ? now : battle.finishedAt;
}
