import { createBattle } from './engine';
import {
  LOCAL_BATTLE_SCHEMA_VERSION,
  type BattleBoss,
  type BattleMode,
  type CampaignBattleInfo,
  type Difficulty,
  type LocalBattle,
  type NewBattleParams,
  type StanceDef,
} from './types';

export interface NewLocalBattle {
  id: string;
  mode: BattleMode;
  campaign: CampaignBattleInfo | null;
  boss: BattleBoss | null;
  difficulty: Difficulty;
  stances: StanceDef[];
  params: NewBattleParams; // поля подготовки
  now: string;
}

/** Новый бой браузера (doc/battle.md §7). Параметры проверяет `createBattle`. */
export function createLocalBattle(input: NewLocalBattle): LocalBattle {
  return {
    schemaVersion: LOCAL_BATTLE_SCHEMA_VERSION,
    id: input.id,
    mode: input.mode,
    campaign: input.campaign,
    boss: input.boss,
    difficulty: input.difficulty,
    stances: input.stances,
    state: createBattle(input.params),
    history: [],
    submission: input.mode === 'CAMPAIGN' ? { status: 'NOT_SENT', lastError: null } : null,
    startedAt: input.now,
    updatedAt: input.now,
    finishedAt: null,
  };
}

/**
 * Бой не закончен: идёт или ждёт отправки результата кампании. Главное меню показывает
 * «Вернуться к бою», а новый бой начинается только после подтверждения.
 */
export function isUnfinished(battle: LocalBattle): boolean {
  return battle.state.status === 'IN_PROGRESS' || battle.submission?.status === 'NOT_SENT';
}
