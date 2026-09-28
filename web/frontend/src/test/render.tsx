import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { createBattleStore, type KeyValueStorage } from '../domain/battle';
import { ActiveBattleContext, createActiveBattle, type ActiveBattle } from '../features/battle/useActiveBattle';

/** Web Storage в памяти — вместо `localStorage` в тестах. */
export function memoryStorage(): KeyValueStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: (key) => void data.delete(key),
  };
}

export function memoryActiveBattle(storage: KeyValueStorage = memoryStorage()): ActiveBattle {
  return createActiveBattle(createBattleStore(() => storage));
}

/**
 * Рендер маршрутов приложения с провайдерами, как в App, но с памятью вместо адресной строки
 * и текущим боем в памяти, а не в `localStorage`.
 */
export function renderRoutes(
  routes: RouteObject[],
  initialPath: string,
  { activeBattle = memoryActiveBattle() }: { activeBattle?: ActiveBattle } = {},
) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(
    <MantineProvider env="test">
      <Notifications />
      <QueryClientProvider client={queryClient}>
        <ActiveBattleContext.Provider value={activeBattle}>
          <RouterProvider router={router} />
        </ActiveBattleContext.Provider>
      </QueryClientProvider>
    </MantineProvider>,
  );
  return { ...view, router, activeBattle };
}
