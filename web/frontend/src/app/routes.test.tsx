import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isProtectedPath } from '../shared/routing';
import { renderRoutes } from '../test/render';
import { server, signedIn } from '../test/server';
import { routes, screens } from './routes';

describe('Маршруты приложения', () => {
  it('главное меню предлагает экспедицию и кампании', async () => {
    // подготовка и вызов
    renderRoutes(routes, '/');

    // проверка
    expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
    expect(screen.getByTestId('menu-expedition')).toHaveAttribute('href', '/expedition/new');
    expect(screen.getByTestId('menu-campaigns')).toHaveAttribute('href', '/campaigns');
  });

  it.each(screens.map((s) => [s.path.replace(':id', '12').replace(':token', 'abc'), s.testId]))(
    'экран %s открывается (после входа)',
    async (path, testId) => {
      // подготовка
      server.use(signedIn());

      // вызов
      renderRoutes(routes, path);

      // проверка
      expect(await screen.findByTestId(testId)).toBeInTheDocument();
    },
  );

  it.each(screens.filter((s) => isProtectedPath(s.path)).map((s) => s.path.replace(':id', '12')))(
    'без входа экран %s ведёт на вход с возвратом',
    async (path) => {
      // вызов
      const { router } = renderRoutes(routes, path);

      // проверка
      expect(await screen.findByTestId('page-login')).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/login');
      expect(new URLSearchParams(router.state.location.search).get('next')).toBe(path);
    },
  );

  it('неизвестный адрес показывает «Страница не найдена»', async () => {
    // подготовка и вызов
    renderRoutes(routes, '/nope');

    // проверка
    expect(await screen.findByTestId('page-not-found')).toBeInTheDocument();
  });
});
