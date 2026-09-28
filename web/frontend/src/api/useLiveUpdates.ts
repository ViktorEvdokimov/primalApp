import { notifications } from '@mantine/notifications';
import { useQueryClient, type Query } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import { ru } from '../shared/i18n/ru';
import { getGetCampaignQueryKey, getListCampaignsQueryKey } from './generated/campaigns/campaigns';
import type { CampaignSheet } from './generated/primal.schemas';

/** То, что нужно от `EventSource`: подписка на именованные события и закрытие. */
export interface LiveSource {
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
  close(): void;
}

export type LiveSourceFactory = (url: string) => LiveSource;

/** В браузере — `EventSource` (переподключается сам); в jsdom его нет — обновлений нет. */
const browserSource: LiveSourceFactory | null =
  typeof EventSource === 'undefined' ? null : (url) => new EventSource(url, { withCredentials: true }) as LiveSource;

/** Запросы кампании: лист, подготовка боя, история боёв, переход главы — ключи orval начинаются с адреса. */
export function isCampaignQuery(query: Query, campaignId: number): boolean {
  const base = `/api/v1/campaigns/${campaignId}`;
  const [url] = query.queryKey;
  return typeof url === 'string' && (url === base || url.startsWith(`${base}/`) || url.startsWith(`${base}?`));
}

/**
 * Живые обновления кампании (`api.md` §9.3): `campaign.updated` с версией новее своей или с изменёнными боями
 * — запросы кампании перезапрашиваются (свои правки листа версию уже подняли — лишнего запроса нет);
 * `access.revoked` — сообщение и возврат в меню.
 */
export function useLiveUpdates(campaignId: number, createSource: LiveSourceFactory | null = browserSource) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  // Навигация меняется между рендерами, а подписка — только при смене кампании
  const navigateRef = useRef(navigate);
  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  useEffect(() => {
    if (createSource === null || !Number.isFinite(campaignId)) return undefined;
    const source = createSource(`/api/v1/campaigns/${campaignId}/events`);

    source.addEventListener('campaign.updated', (event) => {
      let update: { version?: number; battles?: boolean } = {};
      try {
        update = JSON.parse(event.data) as typeof update;
      } catch {
        update = {};
      }
      // Своя правка версию уже подняла; бои (отметки, история) версию не меняют — их перезапрашиваем всегда
      const current = queryClient.getQueryData<CampaignSheet>(getGetCampaignQueryKey(campaignId))?.version;
      const own = update.version !== undefined && current !== undefined && update.version <= current;
      if (own && update.battles !== true) return;
      void queryClient.invalidateQueries({ predicate: (query) => isCampaignQuery(query, campaignId) });
      void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
    });

    source.addEventListener('access.revoked', () => {
      source.close();
      queryClient.removeQueries({ predicate: (query) => isCampaignQuery(query, campaignId) });
      void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
      notifications.show({ id: `access-revoked-${campaignId}`, color: 'red', message: ru.sharing.accessRevoked });
      navigateRef.current('/', { replace: true });
    });

    return () => source.close();
  }, [campaignId, createSource, queryClient]);
}
