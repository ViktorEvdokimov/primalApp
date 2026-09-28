import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { ApiError } from '../../api/errors';
import { submitBattleResult } from '../../api/generated/battles/battles';
import { getGetCampaignQueryKey, getListCampaignsQueryKey } from '../../api/generated/campaigns/campaigns';
import type { OverridesRequest, ResultAppliedNext } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useActiveBattle } from '../battle/useActiveBattle';
import { resultRequest, type CampaignLocalBattle } from './campaignBattle';

export type ResultAction = 'ACCEPT' | 'DISMISS';

/** Причины `409 CAMPAIGN_CHANGED` и время победы, закрывшей главу. */
export interface CampaignChanged {
  reasons: string[];
  closedAt: string | null;
}

export type SubmissionState =
  | { kind: 'IDLE' }
  | { kind: 'SENDING'; action: ResultAction }
  /** Нет сети: результат остался в браузере, «Отправить снова». */
  | { kind: 'OFFLINE'; action: ResultAction; overrides: OverridesRequest | null }
  /** Кампания изменилась, пока шёл бой: принять нельзя, только «Отклонить результат». */
  | { kind: 'CHANGED'; changed: CampaignChanged }
  | { kind: 'ERROR'; message: string }
  | { kind: 'DONE'; next: ResultAppliedNext };

/** Причины отказа из problem+json `409 CAMPAIGN_CHANGED`; другой ответ — `null`. */
export function campaignChanged(error: unknown): CampaignChanged | null {
  if (!(error instanceof ApiError) || error.code !== 'CAMPAIGN_CHANGED') return null;
  const reasons = Array.isArray(error.problem?.reasons) ? (error.problem.reasons as string[]) : [];
  const closedBy = error.problem?.closedBy as { submittedAt?: string } | undefined;
  return {
    reasons: reasons.length > 0 ? reasons : [error.detail ?? ru.errors.unknown],
    closedAt: closedBy?.submittedAt ?? null,
  };
}

/**
 * Отправка итога боя кампании (`battle.md` §9): успех — `submission = SENT`, бой удаляется из браузера, лист
 * кампании обновляется; нет сети — результат остаётся (`NOT_SENT` с текстом ошибки) и отправляется снова;
 * `409 CAMPAIGN_CHANGED` — причины и только «Отклонить результат». Повтор безопасен: сервер вернёт уже
 * сохранённый итог.
 */
export function useResultSubmission(battle: CampaignLocalBattle) {
  const active = useActiveBattle();
  const queryClient = useQueryClient();
  const [state, setState] = useState<SubmissionState>({ kind: 'IDLE' });
  const campaignId = battle.campaign.id;
  const { update, clear, getBattle } = active;

  const submit = useCallback(
    async (action: ResultAction, overrides: OverridesRequest | null = null): Promise<ResultAppliedNext | null> => {
      setState({ kind: 'SENDING', action });
      try {
        const applied = await submitBattleResult(campaignId, battle.id, resultRequest(battle, action, overrides));
        update(battle.id, (current) => ({ ...current, submission: { status: 'SENT', lastError: null } }));
        if (getBattle()?.id === battle.id) clear();
        queryClient.setQueryData(getGetCampaignQueryKey(campaignId), applied.campaign);
        void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
        setState({ kind: 'DONE', next: applied.next });
        return applied.next;
      } catch (error) {
        const changed = campaignChanged(error);
        if (changed !== null) {
          setState({ kind: 'CHANGED', changed });
        } else if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
          update(battle.id, (current) => ({ ...current, submission: { status: 'NOT_SENT', lastError: ru.errors.network } }));
          setState({ kind: 'OFFLINE', action, overrides });
        } else {
          setState({
            kind: 'ERROR',
            message: error instanceof ApiError ? (error.detail ?? ru.errors.unknown) : ru.errors.unknown,
          });
        }
        return null;
      }
    },
    [battle, campaignId, update, clear, getBattle, queryClient],
  );

  const retry = useCallback(
    () => (state.kind === 'OFFLINE' ? submit(state.action, state.overrides) : Promise.resolve(null)),
    [state, submit],
  );

  return { state, submit, retry };
}
