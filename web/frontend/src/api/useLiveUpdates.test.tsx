import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { sheetFixture } from '../test/fixtures/campaign';
import { getGetCampaignQueryKey } from './generated/campaigns/campaigns';
import { useLiveUpdates, type LiveSource } from './useLiveUpdates';

/** Поддельный `EventSource`: тест сам отправляет события. */
class FakeSource implements LiveSource {
  readonly listeners = new Map<string, (event: MessageEvent<string>) => void>();
  closed = false;

  constructor(readonly url: string) {}

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data: unknown) {
    act(() => this.listeners.get(type)?.(new MessageEvent(type, { data: JSON.stringify(data) })));
  }
}

function setup() {
  const sources: FakeSource[] = [];
  const createSource = (url: string) => {
    const source = new FakeSource(url);
    sources.push(source);
    return source;
  };
  function Sheet() {
    useLiveUpdates(12, createSource);
    return <div data-testid="sheet" />;
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetCampaignQueryKey(12), sheetFixture({ version: 5 }));
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const router = createMemoryRouter(
    [
      { path: '/campaigns/12', element: <Sheet /> },
      { path: '/', element: <div data-testid="menu" /> },
    ],
    { initialEntries: ['/campaigns/12'] },
  );
  const view = render(
    <MantineProvider env="test">
      <Notifications />
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
  return { ...view, sources, queryClient, invalidate, router };
}

describe('useLiveUpdates', () => {
  it('подписывается на поток кампании и закрывает его при уходе', () => {
    // вызов
    const { sources, unmount } = setup();

    // проверка
    expect(sources.map((source) => source.url)).toEqual(['/api/v1/campaigns/12/events']);
    unmount();
    expect(sources[0]!.closed).toBe(true);
  });

  it('новая версия — запросы кампании перезапрашиваются; своя (не новее) — нет', () => {
    // подготовка
    const { sources, invalidate, queryClient } = setup();

    // вызов: своя правка уже подняла версию до 5
    sources[0]!.emit('campaign.updated', { campaignId: 12, version: 5, actor: { kind: 'USER', name: 'Алиса' } });

    // проверка
    expect(invalidate).not.toHaveBeenCalled();

    // вызов: отметка о начале боя — версия та же, но изменились бои
    sources[0]!.emit('campaign.updated', { campaignId: 12, version: 5, battles: true, actor: { kind: 'GUEST', name: 'Вадим' } });

    // проверка
    expect(invalidate).toHaveBeenCalled();
    invalidate.mockClear();

    // вызов: правка другого участника
    sources[0]!.emit('campaign.updated', { campaignId: 12, version: 6, actor: { kind: 'GUEST', name: 'Вадим' } });

    // проверка
    expect(invalidate).toHaveBeenCalled();
    const predicate = invalidate.mock.calls[0]![0]!.predicate!;
    const query = (key: unknown[]) => ({ queryKey: key }) as unknown as Parameters<typeof predicate>[0];
    expect(predicate(query(['/api/v1/campaigns/12']))).toBe(true);
    expect(predicate(query(['/api/v1/campaigns/12/battle-setup']))).toBe(true);
    expect(predicate(query(['/api/v1/campaigns/120']))).toBe(false);
    expect(queryClient.getQueryData(getGetCampaignQueryKey(12))).toBeDefined();
  });

  it('access.revoked — сообщение, данные кампании удаляются, возврат в меню', async () => {
    // подготовка
    const { sources, queryClient, router } = setup();

    // вызов
    sources[0]!.emit('access.revoked', {});

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(await screen.findByText('Доступ к кампании закрыт: ссылку отозвали.')).toBeInTheDocument();
    expect(sources[0]!.closed).toBe(true);
    expect(queryClient.getQueryData(getGetCampaignQueryKey(12))).toBeUndefined();
  });
});
