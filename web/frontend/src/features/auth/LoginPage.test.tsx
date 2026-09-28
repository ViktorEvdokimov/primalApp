import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import type { MeResponse } from '../../api/generated/primal.schemas';
import { guestMe, userMe } from '../../test/fixtures/auth';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const CHALLENGE = 'c7e1a1a0-0000-4000-8000-000000000001';

function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { status, code, detail: 'Ошибка сервера', ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Сервер принимает запрос кода; повтор разрешён через `resendInSeconds`. */
function codeRequested(resendInSeconds = 60, requests: unknown[] = []) {
  return http.post('*/api/v1/auth/code', async ({ request }) => {
    requests.push(await request.json());
    const now = Date.now();
    return HttpResponse.json(
      {
        challengeId: CHALLENGE,
        expiresAt: new Date(now + 600_000).toISOString(),
        resendAfter: new Date(now + resendInSeconds * 1000).toISOString(),
      },
      { status: 202 },
    );
  });
}

function verified(isNewUser: boolean, bodies: unknown[] = []) {
  return http.post('*/api/v1/auth/code/verify', async ({ request }) => {
    bodies.push(await request.json());
    return HttpResponse.json({
      user: { id: 7, email: 'alice@example.com', displayName: isNewUser ? null : 'Алиса' },
      isNewUser,
      device: { id: userMe.device.id, userAgent: 'Chrome, Android' },
    });
  });
}

async function openLogin(path = '/login', user = userEvent.setup()) {
  const view = renderRoutes(routes, path);
  await screen.findByTestId('page-login');
  return { ...view, user };
}

async function requestCodeFor(user: ReturnType<typeof userEvent.setup>, email = 'alice@example.com') {
  await user.type(screen.getByTestId('login-email'), email);
  await user.click(screen.getByTestId('login-get-code'));
  await screen.findByTestId('login-code-sent');
}

describe('Вход по коду из письма', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  describe('Шаг 1: почта', () => {
    it('код запрашивается на введённую почту, затем открывается ввод кода', async () => {
      // подготовка
      const requests: unknown[] = [];
      server.use(codeRequested(60, requests));
      const { user } = await openLogin();

      // вызов
      await requestCodeFor(user, '  alice@example.com ');

      // проверка
      expect(requests).toEqual([{ email: 'alice@example.com' }]);
      expect(screen.getByTestId('login-code-sent')).toHaveTextContent('alice@example.com');
      expect(screen.getByTestId('login-code-0')).toHaveAttribute('autocomplete', 'one-time-code');
    });

    it.each([
      [problem(400, 'VALIDATION_FAILED'), 'Проверьте адрес почты.'],
      [problem(429, 'RATE_LIMITED', { retryAfter: 42 }), 'Слишком много попыток. Повторите через 42 с.'],
    ])('ошибка сервера показывается понятным текстом (%#)', async (response, message) => {
      // подготовка
      server.use(http.post('*/api/v1/auth/code', () => response));
      const { user } = await openLogin();

      // вызов
      await user.type(screen.getByTestId('login-email'), 'alice@example.com');
      await user.click(screen.getByTestId('login-get-code'));

      // проверка
      expect(await screen.findByTestId('login-error')).toHaveTextContent(message);
      expect(screen.queryByTestId('login-code-sent')).not.toBeInTheDocument();
    });
  });

  describe('Шаг 2: код', () => {
    it('вставка кода целиком — вход и возврат туда, откуда пришли', async () => {
      // подготовка
      const bodies: unknown[] = [];
      server.use(codeRequested(), verified(false, bodies), signedIn());
      const { user, router } = await openLogin('/login?next=%2Fcampaigns');
      await requestCodeFor(user);

      // вызов
      await user.click(screen.getByTestId('login-code-0'));
      await user.paste('482913');

      // проверка
      expect(await screen.findByTestId('page-campaigns')).toBeInTheDocument();
      expect(bodies).toEqual([{ challengeId: CHALLENGE, code: '482913' }]);
      expect(router.state.location.pathname).toBe('/campaigns');
    });

    it('новый пользователь указывает имя, затем — главное меню', async () => {
      // подготовка
      const renames: unknown[] = [];
      server.use(
        codeRequested(),
        verified(true),
        http.patch('*/api/v1/auth/me', async ({ request }) => {
          const body = (await request.json()) as { displayName: string };
          renames.push(body);
          const me: MeResponse = { ...userMe, user: { ...userMe.user!, displayName: body.displayName } };
          return HttpResponse.json(me);
        }),
      );
      const { user } = await openLogin();
      await requestCodeFor(user);
      await user.click(screen.getByTestId('login-code-0'));
      await user.paste('482913');

      // вызов
      await user.type(await screen.findByTestId('login-name'), 'Никита');
      await user.click(screen.getByTestId('login-name-save'));

      // проверка
      expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
      expect(renames).toEqual([{ displayName: 'Никита' }]);
      expect(screen.getByTestId('menu-user')).toHaveTextContent('Никита');
      expect(screen.getByTestId('menu-settings')).toBeInTheDocument();
    });

    it('неверный код — сообщение с числом оставшихся попыток', async () => {
      // подготовка
      server.use(codeRequested(), http.post('*/api/v1/auth/code/verify', () => problem(400, 'INVALID_CODE', { attemptsLeft: 3 })));
      const { user } = await openLogin();
      await requestCodeFor(user);

      // вызов
      await user.click(screen.getByTestId('login-code-0'));
      await user.paste('111111');

      // проверка
      expect(await screen.findByTestId('login-error')).toHaveTextContent('Неверный код. Осталось попыток: 3.');
      expect(screen.getByTestId('login-code-0')).toHaveValue('');
    });

    it('код устарел — сообщение, новый код можно запросить сразу', async () => {
      // подготовка
      server.use(codeRequested(), http.post('*/api/v1/auth/code/verify', () => problem(400, 'CODE_EXPIRED')));
      const { user } = await openLogin();
      await requestCodeFor(user);
      expect(screen.getByTestId('login-resend')).toBeDisabled();

      // вызов
      await user.click(screen.getByTestId('login-code-0'));
      await user.paste('111111');

      // проверка
      expect(await screen.findByTestId('login-error')).toHaveTextContent('Код устарел');
      expect(screen.getByTestId('login-resend')).toBeEnabled();
    });

    it('повторная отправка — после таймера из resendAfter', async () => {
      // подготовка
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const requests: unknown[] = [];
      server.use(codeRequested(60, requests));
      const { user } = await openLogin('/login', userEvent.setup({ advanceTimers: vi.advanceTimersByTime }));
      await requestCodeFor(user);
      expect(screen.getByTestId('login-resend')).toBeDisabled();
      expect(screen.getByTestId('login-resend-timer')).toHaveTextContent('через 60 с');

      // вызов: время идёт по секунде — после каждого тика React перерисовывает отсчёт
      const tick = (seconds: number) => {
        for (let i = 0; i < seconds; i++) {
          act(() => {
            vi.advanceTimersByTime(1000);
          });
        }
      };
      tick(30);
      expect(screen.getByTestId('login-resend-timer')).toHaveTextContent('через 30 с');
      tick(30);

      // проверка
      await waitFor(() => expect(screen.getByTestId('login-resend')).toBeEnabled());
      expect(screen.queryByTestId('login-resend-timer')).not.toBeInTheDocument();
      await user.click(screen.getByTestId('login-resend'));
      await waitFor(() => expect(requests).toHaveLength(2));
    });

    it('«Изменить почту» возвращает к первому шагу', async () => {
      // подготовка
      server.use(codeRequested());
      const { user } = await openLogin();
      await requestCodeFor(user);

      // вызов
      await user.click(screen.getByTestId('login-change-email'));

      // проверка
      expect(screen.getByTestId('login-email')).toHaveValue('alice@example.com');
    });
  });
});

describe('Главное меню и настройки', () => {
  it('без входа меню предлагает «Войти», «Кампании» ведут на вход', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('menu-campaigns'));

    // проверка
    expect(await screen.findByTestId('page-login')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?next=%2Fcampaigns');
  });

  it('меню после входа: имя и «Настройки»', async () => {
    // подготовка
    server.use(signedIn());

    // вызов
    renderRoutes(routes, '/');

    // проверка
    expect(await screen.findByTestId('menu-user')).toHaveTextContent('Алиса');
    expect(screen.queryByTestId('menu-login')).not.toBeInTheDocument();
  });

  it('устройства: текущее помечено; «Выйти на всех других устройствах»', async () => {
    // подготовка
    let revokedOthers = false;
    server.use(
      signedIn(),
      http.get('*/api/v1/auth/devices', () =>
        HttpResponse.json([
          { id: userMe.device.id, userAgent: 'Chrome, Android', createdAt: '2026-09-20T10:00:00Z', lastSeenAt: '2026-09-27T10:00:00Z', current: true },
          ...(revokedOthers
            ? []
            : [{ id: 'd2', userAgent: 'Firefox, Windows', createdAt: '2026-09-01T10:00:00Z', lastSeenAt: '2026-09-02T10:00:00Z', current: false }]),
        ]),
      ),
      http.post('*/api/v1/auth/devices/revoke-others', () => {
        revokedOthers = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoutes(routes, '/settings');
    const user = userEvent.setup();
    expect(await screen.findAllByTestId('settings-device')).toHaveLength(2);
    expect(screen.getByTestId('settings-device-current')).toBeInTheDocument();

    // вызов
    await user.click(screen.getByTestId('settings-revoke-others'));

    // проверка
    await waitFor(() => expect(screen.getAllByTestId('settings-device')).toHaveLength(1));
    expect(screen.getByTestId('settings-notice')).toHaveTextContent('Другие устройства отключены.');
  });

  it('отзыв текущего устройства открывает вход', async () => {
    // подготовка
    server.use(
      signedIn(),
      http.get('*/api/v1/auth/devices', () =>
        HttpResponse.json([
          { id: userMe.device.id, userAgent: 'Chrome, Android', createdAt: '2026-09-20T10:00:00Z', lastSeenAt: '2026-09-27T10:00:00Z', current: true },
        ]),
      ),
      http.delete('*/api/v1/auth/devices/:id', () => new HttpResponse(null, { status: 204 })),
    );
    const { router } = renderRoutes(routes, '/settings');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('settings-device-revoke'));

    // проверка
    expect(await screen.findByTestId('page-login')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('«Выйти» — главное меню снова предлагает «Войти»', async () => {
    // подготовка
    server.use(
      signedIn(),
      http.get('*/api/v1/auth/devices', () => HttpResponse.json([])),
      http.post('*/api/v1/auth/logout', () => {
        server.use(http.get('*/api/v1/auth/me', () => problem(401, 'UNAUTHENTICATED')));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoutes(routes, '/settings');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('settings-logout'));

    // проверка
    expect(await screen.findByTestId('menu-login')).toBeInTheDocument();
  });

  it('гость: имя устройства, без списка устройств, предложение войти по почте', async () => {
    // подготовка
    server.use(signedIn(guestMe));

    // вызов
    renderRoutes(routes, '/settings');

    // проверка
    expect(await screen.findByTestId('settings-name')).toHaveValue('Вадим');
    expect(screen.getByTestId('settings-login')).toBeInTheDocument();
    expect(screen.queryByTestId('settings-devices')).not.toBeInTheDocument();
  });
});
