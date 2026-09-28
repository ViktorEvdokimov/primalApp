import type {
  ActiveBattle,
  BattleResultRequest,
  OverridesRequest,
  StartBattleRequest,
} from '../../api/generated/primal.schemas';
import type { CampaignBattleInfo, LocalBattle } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';

/** Бой кампании в браузере: снимок кампании есть всегда. */
export type CampaignLocalBattle = LocalBattle & { campaign: CampaignBattleInfo };

const MAX_ROUNDS = 10;

export function isCampaignBattle(battle: LocalBattle | null): battle is CampaignLocalBattle {
  return battle !== null && battle.mode === 'CAMPAIGN' && battle.campaign !== null;
}

/** Бой кампании закончен, а результат не отправлен — баннер «Результат боя не отправлен» (D-13). */
export function hasPendingResult(battle: LocalBattle | null): battle is CampaignLocalBattle {
  return isCampaignBattle(battle) && battle.state.status !== 'IN_PROGRESS' && battle.submission?.status === 'NOT_SENT';
}

/** Отметка о начале боя (`api.md` §6.2) — поля снимка подготовки. */
export function startRequest(battle: CampaignLocalBattle): StartBattleRequest {
  return {
    id: battle.id,
    questNumber: battle.campaign.questNumber,
    bossCode: battle.boss?.code ?? null,
    difficulty: battle.difficulty,
    chapter: battle.campaign.chapter,
    progressSeq: battle.campaign.progressSeq,
    startedAt: battle.startedAt,
  };
}

/**
 * Результат боя (`api.md` §7.1): поля старта повторяются на случай, если отметка о начале не дошла.
 * Тело одинаково для превью и итога; для итога — ещё `action` и `overrides`.
 */
export function resultRequest(
  battle: CampaignLocalBattle,
  action: BattleResultRequest['action'] = null,
  overrides: OverridesRequest | null = null,
): BattleResultRequest {
  const { state } = battle;
  return {
    questNumber: battle.campaign.questNumber,
    bossCode: battle.boss?.code ?? null,
    difficulty: battle.difficulty,
    chapter: battle.campaign.chapter,
    progressSeq: battle.campaign.progressSeq,
    result: state.status === 'VICTORY' ? 'VICTORY' : 'DEFEAT',
    defeatReason: state.status === 'DEFEAT' ? (state.defeatReason ?? 'ROUNDS') : null,
    roundsPlayed: Math.min(Math.max(state.round, 1), MAX_ROUNDS),
    startedAt: battle.startedAt,
    finishedAt: battle.finishedAt ?? battle.updatedAt,
    action,
    overrides,
  };
}

/** Время по часам браузера: «18:30». */
export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

/** «Вадим, задание 1 «Память пустыни», с 18:30» — идущий бой для предупреждений и баннера листа. */
export function describeActiveBattle(battle: ActiveBattle): string {
  const what =
    battle.quest !== null
      ? ru.progression.questInline(battle.quest.number, battle.quest.name)
      : battle.boss !== null
        ? ru.progression.battleWithBoss(battle.boss.name)
        : ru.progression.battleWithoutBoss;
  return ru.progression.warningBattle(battle.startedBy.name, what, clockTime(battle.startedAt));
}
