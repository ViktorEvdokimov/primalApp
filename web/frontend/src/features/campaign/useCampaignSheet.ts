import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { ApiError } from '../../api/errors';
import { getGetCampaignQueryKey, useGetCampaign } from '../../api/generated/campaigns/campaigns';
import type { CampaignSheet, HunterSheet } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

type Patch = (current: CampaignSheet) => CampaignSheet;

/**
 * Лист кампании и общие действия после изменений. Любое изменение кампании увеличивает её версию на 1
 * (doc/api.md §5.3): версия в кэше растёт сразу, чтобы следующая правка (`PATCH` с `expectedVersion`)
 * не получила ложный конфликт.
 */
export function useCampaignSheet(campaignId: number) {
  const queryClient = useQueryClient();
  const sheet = useGetCampaign(campaignId, { query: { retry: false } });

  const actions = useMemo(() => {
    const key = getGetCampaignQueryKey(campaignId);
    const refetch = () => void queryClient.invalidateQueries({ queryKey: key });

    /** Изменение прошло, ответ описывает его полностью: применить к листу без перезапроса. */
    const applied = (patch: Patch = (current) => current) => {
      queryClient.setQueryData<CampaignSheet>(key, (current) =>
        current === undefined ? current : { ...patch(current), version: current.version + 1 },
      );
    };

    return {
      applied,

      /** Изменение прошло, но ответ описывает его не целиком: применить что известно и перезапросить лист. */
      changed: (patch?: Patch) => {
        applied(patch);
        refetch();
      },

      /** Лист целиком из ответа сервера (`PATCH /campaigns/{id}`). */
      replace: (next: CampaignSheet) => {
        queryClient.setQueryData(key, next);
      },

      /** Ошибка изменения: 409 — «Состояние обновилось», иначе текст сервера; лист перезапрашивается. */
      failed: (error: unknown) => {
        if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
          const current = error.problem?.current as CampaignSheet | undefined;
          if (current !== undefined) queryClient.setQueryData(key, current);
          notifications.show({ id: 'sheet-conflict', color: 'yellow', message: ru.sheet.conflict });
        } else {
          notifications.show({ color: 'red', message: errorMessage(error) });
        }
        refetch();
      },

      /** Версия для `expectedVersion` — из кэша на момент отправки, а не из последнего рендера. */
      currentVersion: () => queryClient.getQueryData<CampaignSheet>(key)?.version ?? 0,
    };
  }, [queryClient, campaignId]);

  return { sheet, ...actions };
}

export type SheetActions = Omit<ReturnType<typeof useCampaignSheet>, 'sheet'>;

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return ru.errors.unknown;
  if (error.code === 'NETWORK_ERROR') return ru.errors.network;
  return error.detail ?? ru.errors.unknown;
}

/** Лист с заменённым охотником. */
export function withHunter(sheet: CampaignSheet, hunterId: number, update: (hunter: HunterSheet) => HunterSheet): CampaignSheet {
  return { ...sheet, hunters: sheet.hunters.map((hunter) => (hunter.id === hunterId ? update(hunter) : hunter)) };
}
