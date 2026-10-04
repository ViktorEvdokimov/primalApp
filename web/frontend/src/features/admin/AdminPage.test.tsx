import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { AdminCatalog, AdminQuest, MeResponse } from '../../api/generated/primal.schemas';
import { achievementsFixture } from '../../test/fixtures/campaign';
import { userMe } from '../../test/fixtures/auth';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const adminMe: MeResponse = { ...userMe, user: { ...userMe.user!, admin: true } };

const questOne: AdminQuest = {
  number: 1,
  name: 'Память пустыни',
  bossCode: 'TORAMAT',
  bossName: 'Торамат',
  expansion: null,
  victory: [
    { resources: { BONES: 2 } },
    { if: { chapterIn: [1, 2] }, then: [{ openQuest: 4 }], else: [{ openQuest: 6 }] },
  ],
  expired: [{ openQuest: 6 }],
  victoryText: ['Каждый охотник получает Кости 2', 'Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6'],
  expiredText: ['Добавить задание 6'],
  edited: false,
};

const catalog: AdminCatalog = {
  quests: [
    questOne,
    { ...questOne, number: 33, name: 'Последняя страница', expansion: 'NIGHTMARE', victory: [], expired: [], victoryText: [], expiredText: [] },
  ],
  chapters: [{ chapter: 4, effects: [{ forgeLevelUp: true }], text: ['Повышение уровня кузни'], edited: false }],
  forge: [
    {
      code: 'FIRE_01',
      element: 'FIRE',
      name: 'Язык пламени',
      slot: 'GREATSWORD',
      hunterClass: 'DAREON',
      costs: [
        { level: 1, materials: { BONES: 1, BLOOD: 1 } },
        { level: 2, materials: { SCALES: 1, BLOOD: 1 } },
        { level: 3, materials: { BONES: 1, BLOOD: 1 } },
      ],
      edited: false,
    },
  ],
  lab: [
    {
      code: 'LAB_05',
      name: 'Эвок',
      units: [
        { options: ['TARMARET'], any: false },
        { options: ['ANTHEMON', 'MELLIS'], any: false },
      ],
      edited: false,
    },
  ],
  bosses: [
    {
      code: 'KOROVON',
      name: 'Коровон',
      element: 'CORAL',
      expansion: null,
      difficulties: {
        '0': [
          { stance: 1, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
          { stance: 2, toughnessPerHunter: null, stanceChange: { mode: 'ON_DEMAND', atHealth: null } },
          { stance: 3, toughnessPerHunter: 4, stanceChange: { mode: 'FINAL', atHealth: null } },
        ],
      },
      edited: false,
    },
  ],
};

const stats = {
  accounts: { total: 12, last30Days: 5, last7Days: 2 },
  campaigns: { created: { total: 7, last30Days: 3, last7Days: 1 }, active: 6, completed: 1 },
  battles: { played: { total: 40, last30Days: 18, last7Days: 4 }, victories: 25, defeats: 15, inProgress: 2 },
  generatedAt: '2026-10-04T12:00:00Z',
};

function serveAdmin() {
  let requests = 0;
  server.use(
    signedIn(adminMe),
    http.get('*/api/v1/catalog/achievements', () => HttpResponse.json(achievementsFixture)),
    http.get('*/api/v1/admin/stats', () => HttpResponse.json(stats)),
    http.get('*/api/v1/admin/catalog', () => {
      requests += 1;
      return HttpResponse.json(catalog);
    }),
  );
  return { requests: () => requests };
}

async function openAdmin() {
  serveAdmin();
  renderRoutes(routes, '/admin');
  const user = userEvent.setup();
  await user.click(await screen.findByText('Задания'));
  await screen.findByTestId('admin-quest-form');
  return user;
}

describe('Администрирование: доступ', () => {
  it('не администратору /admin — «Страница не найдена», каталог админки не запрашивается, в меню пункта нет', async () => {
    // подготовка
    let requests = 0;
    server.use(
      signedIn(),
      http.get('*/api/v1/admin/catalog', () => {
        requests += 1;
        return HttpResponse.json(catalog);
      }),
    );

    // вызов
    const { router } = renderRoutes(routes, '/admin');

    // проверка
    expect(await screen.findByTestId('page-not-found')).toBeInTheDocument();
    expect(requests).toBe(0);
    await router.navigate('/');
    await screen.findByTestId('menu-user');
    expect(screen.queryByTestId('menu-admin')).not.toBeInTheDocument();
  });

  it('администратору — пункт «Администрирование» в главном меню', async () => {
    // подготовка
    serveAdmin();

    // вызов
    renderRoutes(routes, '/');

    // проверка
    expect(await screen.findByTestId('menu-admin')).toHaveAttribute('href', '/admin');
  });
});

describe('Администрирование: награды задания', () => {
  it('форма из наград задания; текст «как прочтут игроки»; дополнение в списке заданий', async () => {
    // вызов
    await openAdmin();

    // проверка
    const victory = within(screen.getByTestId('admin-victory'));
    // вложенные эффекты «то» и «иначе» — тоже карточки
    expect(victory.getAllByTestId('effect').map((node) => node.dataset.kind)).toEqual(['resources', 'if', 'openQuest', 'openQuest']);
    expect(screen.getAllByTestId('admin-preview-line')[1]).toHaveTextContent('Если текущая глава 1 или 2');
    expect(screen.getByTestId('admin-quest')).toHaveValue('1. Память пустыни');
    expect(screen.getByTestId('admin-save')).toBeDisabled();
  });

  it('правка: количество, новое достижение — «Сохранить» отправляет язык каталога', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/quests/1', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...questOne, edited: true });
      }),
    );
    const user = await openAdmin();
    const victory = within(screen.getByTestId('admin-victory'));

    // вызов: кости 2 → 5
    const quantity = victory.getByTestId('effect-resource-quantity');
    await user.clear(quantity);
    await user.type(quantity, '5');
    // вызов: + достижение «Затишье»
    await user.click(victory.getAllByTestId('effect-add').at(-1)!);
    await user.click(await screen.findByTestId('effect-add-grantAchievement'));
    expect(screen.getByTestId('admin-missing')).toHaveTextContent('Заполните поля: 1.');
    expect(screen.getByTestId('admin-save')).toBeDisabled();
    await user.click(victory.getByTestId('effect-achievement'));
    await user.click(await screen.findByRole('option', { name: 'Затишье' }));
    await user.click(screen.getByTestId('admin-save'));

    // проверка
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toEqual({
      victory: [
        { resources: { BONES: 5 } },
        { if: { chapterIn: [1, 2] }, then: [{ openQuest: 4 }], else: [{ openQuest: 6 }] },
        { grantAchievement: 'ZATISHE' },
      ],
      expired: [{ openQuest: 6 }],
    });
    expect(await screen.findByText('Награды сохранены — действуют для всех кампаний.')).toBeInTheDocument();
    expect(await screen.findByTestId('admin-edited')).toBeInTheDocument();
  });

  it('условие: «если» с текущей главой и эффектом «то» — вложенная форма', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/quests/1', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(questOne);
      }),
    );
    const user = await openAdmin();
    const expired = within(screen.getByTestId('admin-expired'));

    // вызов: убрать «добавить задание 6», добавить «если есть достижение «Гербарий» — улучшение набора»
    await user.click(expired.getByTestId('effect-remove'));
    await user.click(expired.getByTestId('effect-add'));
    await user.click(await screen.findByTestId('effect-add-if'));
    await user.click(expired.getByTestId('condition-achievement'));
    await user.click(await screen.findByRole('option', { name: 'Гербарий' }));
    const then = within(expired.getByTestId('effect-then'));
    await user.click(then.getByTestId('effect-add'));
    await user.click(await screen.findByTestId('effect-add-hunterKitUpgrade'));
    await user.click(screen.getByTestId('admin-save'));

    // проверка
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({
      expired: [{ if: { achievement: 'GERBARIY' }, then: [{ hunterKitUpgrade: true }] }],
    });
  });

  it('ошибка сервера — текст под формой; «Отменить изменения» возвращает форму', async () => {
    // подготовка
    server.use(
      http.put('*/api/v1/admin/quests/1', () =>
        HttpResponse.json(
          { status: 400, code: 'VALIDATION_FAILED', detail: 'Неверные ссылки: задание 1, победа: нет задания 99.' },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const user = await openAdmin();
    const victory = within(screen.getByTestId('admin-victory'));

    // вызов
    await user.click(victory.getAllByTestId('effect-remove')[0]!);
    await user.click(screen.getByTestId('admin-save'));

    // проверка
    expect(await screen.findByTestId('admin-error')).toHaveTextContent('Неверные ссылки');
    await user.click(screen.getByTestId('admin-discard'));
    expect(victory.getAllByTestId('effect')).toHaveLength(4);
  });

  it('«Вернуть исходные» — с подтверждением, DELETE', async () => {
    // подготовка
    let deleted = 0;
    serveAdmin();
    server.use(
      http.get('*/api/v1/admin/catalog', () => HttpResponse.json({ ...catalog, quests: [{ ...questOne, edited: true }] })),
      http.delete('*/api/v1/admin/quests/1', () => {
        deleted += 1;
        return HttpResponse.json(questOne);
      }),
    );
    renderRoutes(routes, '/admin');
    const user = userEvent.setup();
    await user.click(await screen.findByText('Задания'));

    // вызов
    await user.click(await screen.findByTestId('admin-reset'));
    await user.click(await screen.findByTestId('admin-reset-confirm'));

    // проверка
    expect(await screen.findByText('Возвращены исходные награды.')).toBeInTheDocument();
    expect(deleted).toBe(1);
    await waitFor(() => expect(screen.queryByTestId('admin-edited')).not.toBeInTheDocument());
  });
});

describe('Администрирование: главы', () => {
  it('эффекты главы 4: правка и сохранение', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/chapters/4', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...catalog.chapters[0], edited: true });
      }),
    );
    const user = await openAdmin();

    // вызов
    await user.click(screen.getByText('Главы'));
    const effects = within(await screen.findByTestId('admin-chapter-effects'));
    await user.click(effects.getByTestId('effect-add'));
    await user.click(await screen.findByTestId('effect-add-labLevelUp'));
    await user.click(screen.getByTestId('admin-save'));

    // проверка
    await waitFor(() => expect(bodies).toEqual([{ effects: [{ forgeLevelUp: true }, { labLevelUp: true }] }]));
  });
});

describe('Администрирование: условие «есть дополнение»', () => {
  it('в условии выбирается дополнение — уходит { expansion: FEATHER }', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/quests/1', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(questOne);
      }),
    );
    const user = await openAdmin();
    const expired = within(screen.getByTestId('admin-expired'));

    // вызов
    await user.click(expired.getByTestId('effect-add'));
    await user.click(await screen.findByTestId('effect-add-if'));
    await user.selectOptions(expired.getByTestId('condition-kind'), 'expansion');
    await user.click(expired.getByTestId('condition-expansion'));
    // «Перо» есть и в пометке дополнения у карточек (обычный select) — нужен пункт выпадающего списка
    const feather = (await screen.findAllByRole('option', { name: 'Перо' })).find((node) => node.hasAttribute('data-combobox-option'));
    await user.click(feather!);
    const then = within(expired.getByTestId('effect-then'));
    await user.click(then.getByTestId('effect-add'));
    await user.click(await screen.findByTestId('effect-add-forgeLevelUp'));
    await user.click(screen.getByTestId('admin-save'));

    // проверка
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({
      expired: [{ openQuest: 6 }, { if: { expansion: 'FEATHER' }, then: [{ forgeLevelUp: true }] }],
    });
  });
});

describe('Администрирование: цены и монстры', () => {
  it('кузница: материи по уровням — «Сохранить» отправляет материя → количество', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/forge/FIRE_01', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...catalog.forge[0], edited: true });
      }),
    );
    const user = await openAdmin();

    // вызов
    await user.click(screen.getByText('Кузница'));
    const item = within(await screen.findByTestId('admin-forge-item'));
    const quantity = item.getAllByTestId('admin-forge-quantity')[0]!;
    await user.clear(quantity);
    await user.type(quantity, '2');
    await user.click(item.getByTestId('admin-save'));

    // проверка
    await waitFor(() =>
      expect(bodies).toEqual([{ costs: [{ BONES: 2, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }, { BONES: 1, BLOOD: 1 }] }]),
    );
    expect(await screen.findByText('Награды сохранены — действуют для всех кампаний.')).toBeInTheDocument();
  });

  it('лаборатория: «любое растение» вместо тармарета', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/lab/LAB_05', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...catalog.lab[0], edited: true });
      }),
    );
    const user = await openAdmin();

    // вызов
    await user.click(screen.getByText('Лаборатория'));
    const potion = within(await screen.findByTestId('admin-lab-potion'));
    await user.click(potion.getAllByTestId('admin-lab-any')[0]!);
    await user.click(potion.getByTestId('admin-save'));

    // проверка
    await waitFor(() =>
      expect(bodies).toEqual([
        {
          units: [
            { any: true, options: [] },
            { options: ['ANTHEMON', 'MELLIS'], any: false },
          ],
        },
      ]),
    );
  });

  it('монстры: прочность второй стойки — 5; без порога здоровья сохранить нельзя', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/admin/bosses/KOROVON', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ ...catalog.bosses[0], edited: true });
      }),
    );
    const user = await openAdmin();

    // вызов: порог первой стойки стёрт — подсказка
    await user.click(screen.getByText('Монстры'));
    const form = within(await screen.findByTestId('admin-boss-form'));
    await user.clear(form.getAllByTestId('admin-stance-health')[0]!);
    expect(form.getByTestId('admin-missing')).toHaveTextContent('Заполните поля: 1.');
    await user.type(form.getAllByTestId('admin-stance-health')[0]!, '6');
    const toughness = form.getAllByTestId('admin-stance-toughness')[1]!;
    await user.type(toughness, '5');
    await user.click(form.getByTestId('admin-save'));

    // проверка
    await waitFor(() =>
      expect(bodies).toEqual([
        {
          difficulties: {
            '0': [
              { toughnessPerHunter: 2, mode: 'HEALTH', atHealth: 6 },
              { toughnessPerHunter: 5, mode: 'ON_DEMAND', atHealth: null },
              { toughnessPerHunter: 4, mode: 'FINAL', atHealth: null },
            ],
          },
        },
      ]),
    );
  });
});

describe('Администрирование: статистика', () => {
  it('открывается первой: учётные записи, кампании, сыгранные бои — всего, за 30 и 7 дней', async () => {
    // подготовка
    serveAdmin();

    // вызов
    renderRoutes(routes, '/admin');

    // проверка
    expect(await screen.findByTestId('admin-stats-accounts')).toHaveAttribute('data-value', '12');
    expect(screen.getByTestId('admin-stats-battles')).toHaveAttribute('data-value', '40');
    const row = within(screen.getByTestId('admin-stats-row-campaigns'));
    expect(row.getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['Новые кампании', '7', '3', '1']);
    expect(screen.getByTestId('admin-stats-campaign-status')).toHaveTextContent('Кампании: идут — 6, пройдены — 1.');
    expect(screen.getByTestId('admin-stats-battle-results')).toHaveTextContent(
      'Бои: побед — 25, поражений — 15; идут сейчас — 2.',
    );
  });
});
