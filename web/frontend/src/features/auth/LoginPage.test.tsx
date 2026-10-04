import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { MeResponse, SignInResponse, UserView } from '../../api/generated/primal.schemas';
import { guestMe, userMe } from '../../test/fixtures/auth';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { status, code, detail: 'Ошибка сервера', ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

const alice: UserView = {
  id: 7,
  login: 'alice',
  displayName: 'Алиса',
  passwordSet: true,
  admin: false,
  expansions: ['NIGHTMARE', 'FEATHER', 'POISON', 'ICE'],
};

function signIn(user: UserView = alice): SignInResponse {
  return { user, device: { id: userMe.device.id, userAgent: 'Chrome, Android' } };
}

/** Сервер принимает вход или регистрацию и запоминает тела запросов. */
function accepts(path: 'login' | 'register', bodies: unknown[] = [], user: UserView = alice) {
  return http.post(`*/api/v1/auth/${path}`, async ({ request }) => {
    bodies.push(await request.json());
    return HttpResponse.json(signIn(user), { status: path === 'register' ? 201 : 200 });
  });
}

async function openLogin(path = '/login') {
  const view = renderRoutes(routes, path);
  await screen.findByTestId('page-login');
  return { ...view, user: userEvent.setup() };
}

async function toRegister(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByText('Регистрация'));
  await screen.findByTestId('register-username');
}

async function fillRegistration(user: ReturnType<typeof userEvent.setup>, values: Partial<Record<string, string>> = {}) {
  const filled = { username: 'alice', password: 'correct horse', repeat: 'correct horse', name: 'Алиса', ...values };
  if (filled.username !== '') await user.type(screen.getByTestId('register-username'), filled.username);
  if (filled.password !== '') await user.type(screen.getByTestId('register-password'), filled.password);
  if (filled.repeat !== '') await user.type(screen.getByTestId('register-password-repeat'), filled.repeat);
  if (filled.name !== '') await user.type(screen.getByTestId('register-name'), filled.name);
  await user.click(screen.getByTestId('register-submit'));
}

describe('Вход по логину и паролю', () => {
  it('логин без пробелов по краям, пароль как есть; вход — туда, откуда пришли', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(accepts('login', bodies), signedIn());
    const { user, router } = await openLogin('/login?next=%2Fcampaigns');

    // вызов
    await user.type(screen.getByTestId('login-username'), '  Alice  ');
    await user.type(screen.getByTestId('login-password'), ' secret pass ');
    await user.click(screen.getByTestId('login-submit'));

    // проверка
    expect(await screen.findByTestId('page-campaigns')).toBeInTheDocument();
    expect(bodies).toEqual([{ login: 'Alice', password: ' secret pass ' }]);
    expect(router.state.location.pathname).toBe('/campaigns');
  });

  it('пустые поля и недопустимый логин — подсказки у полей, запрос не отправляется', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(accepts('login', bodies));
    const { user } = await openLogin();

    // вызов: пусто
    await user.click(screen.getByTestId('login-submit'));

    // проверка
    expect(await screen.findAllByText('Заполните поле.')).toHaveLength(2);

    // вызов: логин с пробелом
    await user.type(screen.getByTestId('login-username'), 'a b');
    await user.type(screen.getByTestId('login-password'), 'secret pass');
    await user.click(screen.getByTestId('login-submit'));

    // проверка
    expect(await screen.findByText('Логин — от 3 до 32 символов: латиница, цифры, «.», «_», «-».')).toBeInTheDocument();
    expect(bodies).toEqual([]);
  });

  it.each([
    [problem(400, 'INVALID_CREDENTIALS', { detail: 'Неверный логин или пароль.' }), 'Неверный логин или пароль.'],
    [problem(429, 'RATE_LIMITED', { retryAfter: 42 }), 'Слишком много попыток. Повторите через 42 с.'],
  ])('ошибка сервера показывается понятным текстом (%#)', async (response, message) => {
    // подготовка
    server.use(http.post('*/api/v1/auth/login', () => response));
    const { user } = await openLogin();

    // вызов
    await user.type(screen.getByTestId('login-username'), 'alice');
    await user.type(screen.getByTestId('login-password'), 'wrong-pass');
    await user.click(screen.getByTestId('login-submit'));

    // проверка
    expect(await screen.findByTestId('login-error')).toHaveTextContent(message);
    expect(screen.getByTestId('page-login')).toBeInTheDocument();
  });
});

describe('Регистрация', () => {
  it('?mode=register открывает регистрацию; логин, пароль и имя уходят на сервер, дальше — главное меню', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(accepts('register', bodies), signedIn());
    const { user, router } = await openLogin('/login?mode=register');

    // вызов
    await fillRegistration(user, { name: ' Алиса ' });

    // проверка
    expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
    expect(bodies).toEqual([{ login: 'alice', password: 'correct horse', displayName: 'Алиса' }]);
    expect(router.state.location.pathname).toBe('/');
  });

  it('переключатель «Регистрация» на экране входа', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(accepts('register', bodies));
    const { user } = await openLogin();

    // вызов
    await toRegister(user);
    await fillRegistration(user);

    // проверка
    await waitFor(() => expect(bodies).toHaveLength(1));
  });

  it('до отправки проверяются логин, длина пароля, совпадение паролей и имя', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(accepts('register', bodies));
    const { user } = await openLogin('/login?mode=register');

    // вызов
    await fillRegistration(user, { username: 'a b', password: 'short', repeat: 'other', name: '' });

    // проверка
    expect(await screen.findByText('Логин — от 3 до 32 символов: латиница, цифры, «.», «_», «-».')).toBeInTheDocument();
    expect(screen.getByText('Пароль — от 8 до 64 символов.')).toBeInTheDocument();
    expect(screen.getByText('Пароли не совпадают.')).toBeInTheDocument();
    expect(screen.getByText('Заполните поле.')).toBeInTheDocument();
    expect(bodies).toEqual([]);
  });

  it('логин уже зарегистрирован — сообщение у поля логина', async () => {
    // подготовка
    server.use(
      http.post('*/api/v1/auth/register', () =>
        problem(409, 'LOGIN_TAKEN', { detail: 'Логин alice уже зарегистрирован. Войдите по нему.' }),
      ),
    );
    const { user } = await openLogin('/login?mode=register');

    // вызов
    await fillRegistration(user);

    // проверка
    expect(await screen.findByText('Логин alice уже зарегистрирован. Войдите по нему.')).toBeInTheDocument();
    expect(screen.getByTestId('register-username')).toHaveAttribute('aria-invalid', 'true');
  });

  it('ошибка сервера по полю — у этого поля', async () => {
    // подготовка
    server.use(
      http.post('*/api/v1/auth/register', () =>
        problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'password', message: 'Пароль слишком длинный: сократите его' }] }),
      ),
    );
    const { user } = await openLogin('/login?mode=register');

    // вызов
    await fillRegistration(user);

    // проверка
    expect(await screen.findByText('Пароль слишком длинный: сократите его')).toBeInTheDocument();
    expect(screen.getByTestId('login-error')).toHaveTextContent('Проверьте заполнение полей.');
  });
});

describe('Логин и пароль в настройках', () => {
  function settings(me: MeResponse = userMe) {
    server.use(signedIn(me), http.get('*/api/v1/auth/devices', () => HttpResponse.json([])));
    renderRoutes(routes, '/settings');
    return userEvent.setup();
  }

  it('логин в заголовке; смена логина — в нижнем регистре', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/auth/me/login', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...userMe, user: { ...alice, login: 'alice_new' } });
      }),
    );
    const user = settings();
    expect(await screen.findByTestId('settings-account')).toHaveTextContent('Вы вошли по логину alice');
    expect(screen.getByTestId('settings-username')).toHaveValue('alice');

    // вызов
    await user.clear(screen.getByTestId('settings-username'));
    await user.type(screen.getByTestId('settings-username'), 'Alice_New');
    await user.click(screen.getByTestId('settings-username-save'));

    // проверка
    expect(await screen.findByTestId('settings-notice')).toHaveTextContent('Логин сохранён. Входите по нему.');
    expect(screen.getByTestId('settings-username')).toHaveValue('alice_new');
    expect(bodies).toEqual([{ login: 'Alice_New' }]);
  });

  it('логин занят другим аккаунтом — сообщение у поля; пустой логин не отправляется', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/auth/me/login', async ({ request }) => {
        bodies.push(await request.json());
        return problem(409, 'LOGIN_TAKEN', { detail: 'Логин bob уже зарегистрирован. Войдите по нему.' });
      }),
    );
    const user = settings();
    const field = await screen.findByTestId('settings-username');

    // вызов: пусто
    await user.clear(field);
    await user.click(screen.getByTestId('settings-username-save'));

    // проверка
    expect(await screen.findByText('Заполните поле.')).toBeInTheDocument();
    expect(bodies).toEqual([]);

    // вызов: занятый логин
    await user.type(field, 'bob');
    await user.click(screen.getByTestId('settings-username-save'));

    // проверка
    expect(await screen.findByText('Логин bob уже зарегистрирован. Войдите по нему.')).toBeInTheDocument();
  });

  it('смена пароля: текущий и новый дважды; неверный текущий — сообщение', async () => {
    // подготовка
    const bodies: unknown[] = [];
    let wrong = true;
    server.use(
      http.put('*/api/v1/auth/me/password', async ({ request }) => {
        bodies.push(await request.json());
        if (wrong) return problem(400, 'INVALID_CREDENTIALS', { detail: 'Текущий пароль указан неверно.' });
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = settings();
    await user.type(await screen.findByTestId('settings-password-current'), 'old-pass');
    await user.type(screen.getByTestId('settings-password-new'), 'brand new pass');
    await user.type(screen.getByTestId('settings-password-repeat'), 'brand new pass');

    // вызов: неверный текущий
    await user.click(screen.getByTestId('settings-password-save'));

    // проверка
    expect(await screen.findByTestId('settings-password-error')).toHaveTextContent('Текущий пароль указан неверно.');

    // вызов: верный
    wrong = false;
    await user.click(screen.getByTestId('settings-password-save'));

    // проверка
    expect(await screen.findByTestId('settings-notice')).toHaveTextContent('Пароль изменён.');
    expect(screen.getByTestId('settings-password-new')).toHaveValue('');
    expect(bodies[1]).toEqual({ currentPassword: 'old-pass', newPassword: 'brand new pass' });
  });

  it('новые пароли не совпадают — запрос не отправляется', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/auth/me/password', async ({ request }) => {
        bodies.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = settings();
    await user.type(await screen.findByTestId('settings-password-current'), 'old-pass');
    await user.type(screen.getByTestId('settings-password-new'), 'brand new pass');
    await user.type(screen.getByTestId('settings-password-repeat'), 'brand new past');

    // вызов
    await user.click(screen.getByTestId('settings-password-save'));

    // проверка
    expect(await screen.findByText('Пароли не совпадают.')).toBeInTheDocument();
    expect(bodies).toEqual([]);
  });

  it('аккаунт по почте: логин user<id>, «Задать пароль» без текущего', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/auth/me/password', async ({ request }) => {
        bodies.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = settings({ ...userMe, user: { ...alice, login: 'user-old', passwordSet: false } });
    expect(await screen.findByTestId('settings-account')).toHaveTextContent('Вы вошли по логину user-old');
    expect(screen.getByTestId('settings-password-not-set')).toBeInTheDocument();
    expect(screen.queryByTestId('settings-password-current')).not.toBeInTheDocument();

    // вызов
    await user.type(screen.getByTestId('settings-password-new'), 'first password');
    await user.type(screen.getByTestId('settings-password-repeat'), 'first password');
    await user.click(screen.getByTestId('settings-password-save'));

    // проверка
    expect(await screen.findByTestId('settings-notice')).toHaveTextContent('Пароль изменён.');
    expect(bodies).toEqual([{ currentPassword: null, newPassword: 'first password' }]);
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

  it('гость: имя устройства, без списка устройств, логина и пароля; предложение войти', async () => {
    // подготовка
    server.use(signedIn(guestMe));

    // вызов
    renderRoutes(routes, '/settings');

    // проверка
    expect(await screen.findByTestId('settings-name')).toHaveValue('Вадим');
    expect(screen.getByTestId('settings-login')).toHaveTextContent('Войти или зарегистрироваться');
    expect(screen.queryByTestId('settings-devices')).not.toBeInTheDocument();
    expect(screen.queryByTestId('settings-username')).not.toBeInTheDocument();
    expect(screen.queryByTestId('settings-password')).not.toBeInTheDocument();
    expect(screen.queryByTestId('settings-expansions')).not.toBeInTheDocument();
  });
});

describe('Дополнения в настройках', () => {
  it('по умолчанию отмечены все; снять «Перо» — уходят остальные три, отметка снята', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      signedIn(),
      http.get('*/api/v1/auth/devices', () => HttpResponse.json([])),
      http.put('*/api/v1/auth/me/expansions', async ({ request }) => {
        const body = (await request.json()) as { expansions: string[] };
        bodies.push(body);
        return HttpResponse.json({ ...userMe, user: { ...alice, expansions: body.expansions } });
      }),
    );
    renderRoutes(routes, '/settings');
    const user = userEvent.setup();
    const checkbox = async (code: string) =>
      (await screen.findAllByTestId('settings-expansion')).find((node) => node.dataset.expansion === code)!;

    // проверка
    expect((await screen.findAllByTestId('settings-expansion')).map((node) => (node as HTMLInputElement).checked)).toEqual([
      true,
      true,
      true,
      true,
    ]);

    // вызов
    await user.click(await checkbox('FEATHER'));

    // проверка
    await waitFor(() => expect(bodies).toEqual([{ expansions: ['NIGHTMARE', 'POISON', 'ICE'] }]));
    await waitFor(async () => expect(await checkbox('FEATHER')).not.toBeChecked());
    expect(await screen.findByText('Дополнения сохранены.')).toBeInTheDocument();
  });
});
