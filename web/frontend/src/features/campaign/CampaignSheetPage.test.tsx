import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { CampaignSheet } from '../../api/generated/primal.schemas';
import { achievementsFixture, hunterFixture, questItem, questsFixture, sheetFixture } from '../../test/fixtures/campaign';
import { finishedCampaignBattle } from '../../test/fixtures/progression';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const problem = (status: number, body: Record<string, unknown>) =>
  HttpResponse.json({ status, ...body }, { status, headers: { 'Content-Type': 'application/problem+json' } });

/** Лист кампании 12 на «сервере»: GET отдаёт текущее значение, тест меняет его между запросами. */
function serveSheet(initial: CampaignSheet) {
  const state = { sheet: initial, gets: 0 };
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => {
      state.gets += 1;
      return HttpResponse.json(state.sheet);
    }),
    http.get('*/api/v1/catalog/quests', () => HttpResponse.json(questsFixture)),
    http.get('*/api/v1/catalog/achievements', () => HttpResponse.json(achievementsFixture)),
  );
  return state;
}

async function openSheet(sheet: CampaignSheet = sheetFixture(), path = '/campaigns/12') {
  const state = serveSheet(sheet);
  const view = renderRoutes(routes, path);
  await screen.findByTestId('sheet-name');
  return { ...view, state, user: userEvent.setup() };
}

const skill = (code: string) => screen.getByTestId(`skill-${code}`);
const resource = (name: string) => {
  const row = screen.getAllByTestId('resource').find((element) => element.dataset.name === name);
  if (row === undefined) throw new Error(`Нет ресурса «${name}»`);
  return within(row);
};

describe('Лист кампании: шапка', () => {
  it('название, пролог, кузня и лаборатория 1 уровня, охотники отряда', async () => {
    // вызов
    await openSheet();

    // проверка
    expect(screen.getByTestId('sheet-name')).toHaveTextContent('SheetTest');
    expect(screen.getByTestId('sheet-chapter')).toHaveTextContent('Пролог');
    expect(screen.getByTestId('sheet-forge')).toHaveTextContent('Кузня: 1');
    expect(screen.getByTestId('sheet-lab')).toHaveTextContent('Лаборатория: 1');
    const hunters = await screen.findAllByTestId('hunter-option');
    expect(hunters.map((element) => [element.dataset.player, element.dataset.class])).toEqual([
      ['Боец', 'Дареон'],
      ['Мира', 'Мира'],
    ]);
  });

  it('«Удалить кампанию» есть только у владельца', async () => {
    // вызов
    await openSheet(sheetFixture({ access: 'LINK', ownerName: 'Алиса' }));

    // проверка
    expect(screen.queryByTestId('sheet-delete')).not.toBeInTheDocument();
    expect(screen.getByText('Владелец: Алиса')).toBeInTheDocument();
  });

  it('ожидающий переход главы: баннер, ручная правка главы недоступна', async () => {
    // вызов
    await openSheet(sheetFixture({ chapter: 3, pendingTransition: true, status: 'CHAPTER_TRANSITION' }));

    // проверка
    expect(screen.getByTestId('sheet-pending-transition')).toBeInTheDocument();
    expect(screen.getByTestId('chapter-edit')).toBeDisabled();
  });

  it('бой кампании: «Начать бой», идущие бои, неотправленный результат этого браузера', async () => {
    // подготовка
    const activeBattle = memoryActiveBattle();
    activeBattle.start(finishedCampaignBattle());
    serveSheet(
      sheetFixture({
        activeBattles: [
          {
            id: '5d1a0000-0000-4000-8000-000000000001',
            startedBy: { kind: 'GUEST', name: 'Вадим' },
            startedAt: '2026-09-27T18:30:00Z',
            quest: questItem(1),
            boss: questItem(1).boss,
          },
        ],
      }),
    );

    // вызов
    renderRoutes(routes, '/campaigns/12', { activeBattle });

    // проверка
    expect(await screen.findByTestId('sheet-active-battle')).toHaveTextContent('Идёт бой: Вадим, задание 1 «Вой в долине», с');
    expect(screen.getByTestId('sheet-start-battle')).toHaveAttribute('href', '/campaigns/12/battle/new');
    expect(screen.getByTestId('pending-result-send')).toHaveAttribute('href', '/campaigns/12/outcome');
  });

  it('ожидающий переход — «Переход главы» вместо «Начать бой»; пройденная кампания — баннер', async () => {
    // вызов
    await openSheet(sheetFixture({ chapter: 3, pendingTransition: true, status: 'CHAPTER_TRANSITION' }));

    // проверка
    expect(screen.getByTestId('sheet-transition')).toHaveAttribute('href', '/campaigns/12/transition');
    expect(screen.queryByTestId('sheet-start-battle')).not.toBeInTheDocument();
  });

  it('несуществующая кампания → «не найдена»', async () => {
    // подготовка
    server.use(signedIn(), http.get('*/api/v1/campaigns/99', () => problem(404, { code: 'NOT_FOUND' })));

    // вызов
    renderRoutes(routes, '/campaigns/99');

    // проверка
    expect(await screen.findByTestId('sheet-error')).toHaveTextContent('Кампания не найдена');
  });
});

describe('Лист кампании: навыки', () => {
  it('в новой кампании навыки закрыты, ступень 2 недоступна без ступени 1', async () => {
    // вызов
    await openSheet();

    // проверка
    const a1 = await screen.findByTestId('skill-A1');
    expect(a1).toHaveTextContent('А1');
    expect(a1).toHaveAttribute('aria-pressed', 'false');
    expect(a1).toHaveAccessibleName('А1: Закрыт');
    expect(a1).toBeEnabled();
    expect(skill('A2')).toHaveAttribute('aria-pressed', 'false');
    expect(skill('A2')).toBeDisabled();
  });

  it('открытие ступени 1 делает доступной ступень 2', async () => {
    // подготовка
    const requests: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/skills', async ({ request }) => {
        requests.push(await request.json());
        return HttpResponse.json(
          hunterFixture({
            skills: [{ branch: 'A', tier: 1 }],
            unlockableSkills: [{ branch: 'A', tier: 2 }, ...(['B', 'V', 'G', 'D'] as const).map((branch) => ({ branch, tier: 1 }))],
          }),
          { status: 201 },
        );
      }),
    );
    const { user } = await openSheet();

    // вызов
    await user.click(await screen.findByTestId('skill-A1'));

    // проверка
    await waitFor(() => expect(skill('A1')).toHaveAttribute('aria-pressed', 'true'));
    expect(skill('A1')).toHaveAccessibleName('А1: Открыт');
    expect(skill('A2')).toBeEnabled();
    expect(requests).toEqual([{ branch: 'A', tier: 1 }]);
  });

  it('открытая ступень 1 при открытой ступени 2 не снимается, ступень 2 — снимается', async () => {
    // подготовка
    let locked: string | null = null;
    server.use(
      http.delete('*/api/v1/campaigns/12/hunters/31/skills/:branch/:tier', ({ params }) => {
        locked = `${String(params.branch)}${String(params.tier)}`;
        return HttpResponse.json(hunterFixture({ skills: [{ branch: 'A', tier: 1 }] }));
      }),
    );
    const hunter = hunterFixture({ skills: [{ branch: 'A', tier: 1 }, { branch: 'A', tier: 2 }], unlockableSkills: [] });
    const { user } = await openSheet(sheetFixture({ hunters: [hunter] }));

    // проверка
    const a1 = await screen.findByTestId('skill-A1');
    expect(a1).toHaveAttribute('aria-pressed', 'true');
    expect(a1).toHaveAttribute('aria-disabled', 'true');
    await user.click(a1);
    expect(locked).toBeNull();
    expect(skill('A2')).toBeEnabled();
    await user.click(skill('A2'));
    await waitFor(() => expect(skill('A2')).toHaveAttribute('aria-pressed', 'false'));
    expect(locked).toBe('A2');
  });
});

describe('Лист кампании: ресурсы', () => {
  it('все ресурсы 0, «−» недоступен при 0', async () => {
    // вызов
    await openSheet();

    // проверка
    const rows = await screen.findAllByTestId('resource');
    expect(rows).toHaveLength(21);
    for (const row of rows) {
      expect(within(row).getByTestId('resource-value')).toHaveTextContent('0');
      expect(within(row).getByTestId('resource-decrement')).toBeDisabled();
    }
  });

  it('+/− сразу меняют значение, на сервер уходит одна пачка изменений', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/resources/adjust', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ resources: { BONES: 1, FIRE: 1 } });
      }),
    );
    const { user } = await openSheet();
    await screen.findAllByTestId('resource');

    // вызов
    await user.click(resource('Кости').getByTestId('resource-increment'));
    await user.click(resource('Кости').getByTestId('resource-increment'));
    await user.click(resource('Кости').getByTestId('resource-decrement'));
    await user.click(resource('Огонь').getByTestId('resource-increment'));

    // проверка
    expect(resource('Кости').getByTestId('resource-value')).toHaveTextContent('1');
    expect(resource('Кости').getByTestId('resource-decrement')).toBeEnabled();
    await waitFor(() => expect(bodies).toEqual([{ changes: { BONES: 1, FIRE: 1 } }]));
    await waitFor(() => expect(resource('Огонь').getByTestId('resource-value')).toHaveTextContent('1'));
    expect(resource('Кости').getByTestId('resource-value')).toHaveTextContent('1');
  });

  it('отказ сервера возвращает значения с сервера и показывает причину', async () => {
    // подготовка
    const state = serveSheet(sheetFixture());
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/resources/adjust', () =>
        problem(422, { code: 'NOT_ENOUGH_RESOURCES', detail: 'Не хватает ресурсов.' }),
      ),
    );
    renderRoutes(routes, '/campaigns/12');
    const user = userEvent.setup();
    await screen.findAllByTestId('resource');
    const before = state.gets;

    // вызов
    await user.click(resource('Кости').getByTestId('resource-increment'));

    // проверка
    expect(await screen.findByText('Не хватает ресурсов.')).toBeInTheDocument();
    await waitFor(() => expect(resource('Кости').getByTestId('resource-value')).toHaveTextContent('0'));
    await waitFor(() => expect(state.gets).toBeGreaterThan(before));
  });
});

describe('Лист кампании: задания', () => {
  it('нет заданий — «Нет открытых заданий.»', async () => {
    // подготовка
    const { user } = await openSheet();

    // вызов
    await user.click(screen.getByTestId('sheet-tab-quests'));

    // проверка
    expect(screen.getByTestId('quests-empty')).toHaveTextContent('Нет открытых заданий.');
  });

  it('редактор: выполненные и истёкшие неактивны, сохраняется набор открытых', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.put('*/api/v1/campaigns/12/quests/open', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ open: [questItem(1), questItem(4)], completed: [questItem(3, 0)], expired: [questItem(5, 0)] });
      }),
    );
    const quests = { open: [questItem(1), questItem(2)], completed: [questItem(3, 0)], expired: [questItem(5, 0)] };
    const { user } = await openSheet(sheetFixture({ quests }), '/campaigns/12?tab=quests');

    // вызов
    await user.click(screen.getByTestId('quests-edit'));
    const editor = within(await screen.findByTestId('quest-editor'));
    await editor.findByLabelText('4. Пепел');

    // проверка
    expect(editor.getByLabelText('1. Вой в долине')).toBeChecked();
    expect(editor.getByLabelText('3. Старые кости')).toBeDisabled();
    expect(editor.getByLabelText('5. Тихая вода')).toBeDisabled();
    expect(editor.getByLabelText('40. Перья бури')).toBeEnabled();

    // вызов
    await user.click(editor.getByLabelText('2. Каменный сон'));
    await user.click(editor.getByLabelText('4. Пепел'));
    await user.click(editor.getByTestId('quest-editor-save'));

    // проверка
    await waitFor(() => expect(screen.queryByTestId('quest-editor')).not.toBeInTheDocument());
    expect(bodies).toEqual([{ numbers: [1, 4] }]);
    expect(screen.getAllByTestId('quest-open').map((element) => element.dataset.number)).toEqual(['1', '4']);
  });

  it('«Отмена» в редакторе ничего не отправляет', async () => {
    // подготовка
    let sent = false;
    server.use(
      http.put('*/api/v1/campaigns/12/quests/open', () => {
        sent = true;
        return HttpResponse.json({ open: [], completed: [], expired: [] });
      }),
    );
    const { user } = await openSheet(sheetFixture(), '/campaigns/12?tab=quests');
    await user.click(screen.getByTestId('quests-edit'));
    const editor = within(await screen.findByTestId('quest-editor'));
    await user.click(await editor.findByLabelText('5. Тихая вода'));

    // вызов
    await user.click(editor.getByTestId('quest-editor-cancel'));

    // проверка
    await waitFor(() => expect(screen.queryByTestId('quest-editor')).not.toBeInTheDocument());
    expect(sent).toBe(false);
    expect(screen.getByTestId('quests-empty')).toBeInTheDocument();
  });

  it('«Выполнено» переносит задание в выполненные и сообщает об открытых; «Отмена» возвращает', async () => {
    // подготовка
    const state = serveSheet(sheetFixture({ chapter: 1, quests: { open: [questItem(1)], completed: [], expired: [] } }));
    server.use(
      http.post('*/api/v1/campaigns/12/quests/1/complete', () => {
        state.sheet = sheetFixture({ chapter: 1, version: 4, quests: { open: [questItem(4)], completed: [questItem(1, 1)], expired: [] } });
        return HttpResponse.json({ quest: { number: 1, status: 'COMPLETED' }, opened: [4], rules: [] });
      }),
      http.post('*/api/v1/campaigns/12/quests/1/reopen', () =>
        HttpResponse.json({ open: [questItem(1), questItem(4)], completed: [], expired: [] }),
      ),
    );
    renderRoutes(routes, '/campaigns/12?tab=quests');
    const user = userEvent.setup();
    await screen.findByTestId('quest-open');

    // вызов
    await user.click(screen.getByTestId('quest-complete'));

    // проверка
    expect(await screen.findByText('Задание 1 выполнено. Добавлены задания: 4.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByTestId('quest-open').map((element) => element.dataset.number)).toEqual(['4']));
    expect(screen.getAllByTestId('quest-completed').map((element) => element.dataset.number)).toEqual(['1']);

    // вызов
    await user.click(screen.getByTestId('quest-reopen'));

    // проверка
    await waitFor(() => expect(screen.queryByTestId('quest-completed')).not.toBeInTheDocument());
    expect(screen.getAllByTestId('quest-open').map((element) => element.dataset.number)).toEqual(['1', '4']);
  });
});

describe('Лист кампании: история боёв', () => {
  it('кто начал, кто отправил итог и чем кончился бой', async () => {
    // подготовка
    server.use(
      http.get('*/api/v1/campaigns/12/battles', () =>
        HttpResponse.json([
          {
            id: 'b1',
            status: 'APPLIED',
            purpose: 'QUEST',
            questNumber: 1,
            boss: { code: 'ZHAR_PTITSA', name: 'Жар-птица', element: 'FIRE' },
            difficulty: 1,
            chapter: 2,
            result: 'VICTORY',
            roundsPlayed: 6,
            startedBy: { kind: 'GUEST', name: 'Вадим' },
            startedAt: '2026-09-27T18:35:00Z',
            submittedBy: { kind: 'USER', name: 'Алиса' },
            submittedAt: '2026-09-27T19:05:00Z',
            stale: false,
          },
          {
            id: 'b2',
            status: 'ABANDONED',
            purpose: 'FREE',
            questNumber: null,
            boss: null,
            difficulty: 1,
            chapter: 2,
            result: null,
            roundsPlayed: null,
            startedBy: { kind: 'USER', name: 'Алиса' },
            startedAt: '2026-09-27T18:00:00Z',
            submittedBy: null,
            submittedAt: null,
            stale: false,
          },
        ]),
      ),
    );

    // вызов
    await openSheet(sheetFixture(), '/campaigns/12?tab=history');

    // проверка
    const items = await screen.findAllByTestId('history-item');
    expect(items[0]).toHaveTextContent('Победа · Задание 1 · Глава 2');
    expect(items[0]).toHaveTextContent('принят');
    expect(items[0]).toHaveTextContent('начало: Вадим');
    expect(items[0]).toHaveTextContent('итог: Алиса');
    expect(items[1]).toHaveAttribute('data-status', 'ABANDONED');
    expect(items[1]).toHaveTextContent('брошен');
  });

  it('идущий бой — «Снять отметку» помечает его брошенным', async () => {
    // подготовка
    let status = 'IN_PROGRESS';
    const removed: string[] = [];
    server.use(
      http.get('*/api/v1/campaigns/12/battles', () =>
        HttpResponse.json([
          {
            id: 'b3',
            status,
            purpose: 'FREE',
            questNumber: null,
            boss: null,
            difficulty: 1,
            chapter: 2,
            result: null,
            roundsPlayed: null,
            startedBy: { kind: 'GUEST', name: 'Вадим' },
            startedAt: '2026-09-26T18:00:00Z',
            submittedBy: null,
            submittedAt: null,
            stale: true,
          },
        ]),
      ),
      http.delete('*/api/v1/campaigns/12/battles/:id', ({ params }) => {
        removed.push(String(params.id));
        status = 'ABANDONED';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = await openSheet(sheetFixture(), '/campaigns/12?tab=history');

    // вызов
    await user.click(await screen.findByTestId('history-abandon'));

    // проверка
    await waitFor(() => expect(screen.getByTestId('history-item')).toHaveAttribute('data-status', 'ABANDONED'));
    expect(removed).toEqual(['b3']);
    expect(screen.queryByTestId('history-abandon')).not.toBeInTheDocument();
  });
});

describe('Лист кампании: достижения и трофеи', () => {
  it('добавление с подсказкой из каталога и удаление', async () => {
    // подготовка
    const added: unknown[] = [];
    let removed: string | null = null;
    const achievement = { id: 7, code: 'ZATISHE', name: 'Затишье', source: 'MANUAL', grantedInChapter: 0 } as const;
    const state = serveSheet(sheetFixture());
    server.use(
      http.post('*/api/v1/campaigns/12/achievements', async ({ request }) => {
        added.push(await request.json());
        state.sheet = sheetFixture({ version: 4, achievements: [achievement] });
        return HttpResponse.json({ ...achievement, matchedCatalog: true }, { status: 201 });
      }),
      http.delete('*/api/v1/campaigns/12/achievements/:id', ({ params }) => {
        removed = String(params.id);
        state.sheet = sheetFixture({ version: 5 });
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoutes(routes, '/campaigns/12?tab=achievements');
    const user = userEvent.setup();
    expect(await screen.findByTestId('achievements-empty')).toHaveTextContent('Нет достижений.');

    // вызов
    await user.type(screen.getByTestId('achievement-input'), 'зати');
    await user.click(await screen.findByRole('option', { name: 'Затишье' }));
    await user.click(screen.getByTestId('achievement-add'));

    // проверка
    await waitFor(() => expect(screen.getAllByTestId('achievement').map((element) => element.dataset.name)).toEqual(['Затишье']));
    expect(added).toEqual([{ name: 'Затишье' }]);
    expect(screen.getByTestId('achievement-input')).toHaveValue('');

    // вызов
    await user.click(screen.getByRole('button', { name: 'Удалить: Затишье' }));

    // проверка
    await waitFor(() => expect(screen.queryByTestId('achievement')).not.toBeInTheDocument());
    expect(removed).toBe('7');
  });

  it('трофеи: босс и главы побед', async () => {
    // вызов
    await openSheet(
      sheetFixture({ trophies: [{ boss: { code: 'GROMOVOLK', name: 'Громоволк', element: 'LIGHTNING' }, chapters: [1, 3] }] }),
      '/campaigns/12?tab=trophies',
    );

    // проверка
    expect(screen.getByTestId('trophy')).toHaveTextContent('Громоволк');
    expect(screen.getByTestId('trophy')).toHaveTextContent('Побеждён: Глава 1, Глава 3');
  });
});

describe('Лист кампании: правки с версией', () => {
  it('409 → уведомление «Состояние обновилось» и актуальный лист', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.patch('*/api/v1/campaigns/12', async ({ request }) => {
        bodies.push(await request.json());
        return problem(409, { code: 'VERSION_CONFLICT', detail: 'Кампания уже изменена', current: sheetFixture({ version: 9, chapter: 5 }) });
      }),
    );
    const { user } = await openSheet();

    // вызов
    await user.click(screen.getByTestId('chapter-edit'));
    const input = await screen.findByTestId('chapter-input');
    await user.clear(input);
    await user.type(input, '3');
    await user.click(screen.getByTestId('chapter-save'));

    // проверка
    expect(await screen.findByText('Состояние обновилось: кампанию изменили на другом устройстве.')).toBeInTheDocument();
    expect(bodies).toEqual([{ expectedVersion: 3, chapter: 3 }]);
  });

  it('правка главы после изменения ресурсов отправляет выросшую версию', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/resources/adjust', () => HttpResponse.json({ resources: { BONES: 1 } })),
      http.patch('*/api/v1/campaigns/12', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(sheetFixture({ version: 5, chapter: 2 }));
      }),
    );
    const { user } = await openSheet();
    await screen.findAllByTestId('resource');
    await user.click(resource('Кости').getByTestId('resource-increment'));
    await waitFor(() => expect(resource('Кости').getByTestId('resource-decrement')).toBeEnabled());
    await new Promise((resolve) => setTimeout(resolve, 600));

    // вызов
    await user.click(screen.getByTestId('chapter-edit'));
    const input = await screen.findByTestId('chapter-input');
    await user.clear(input);
    await user.type(input, '2');
    await user.click(screen.getByTestId('chapter-save'));

    // проверка
    await waitFor(() => expect(screen.getByTestId('sheet-chapter')).toHaveTextContent('Глава 2'));
    expect(bodies).toEqual([{ expectedVersion: 4, chapter: 2 }]);
  });

  it('заметки сохраняются сами; конфликт из-за других правок повторяется с новой версией', async () => {
    // подготовка
    const bodies: { expectedVersion: number; notes: string }[] = [];
    server.use(
      http.patch('*/api/v1/campaigns/12', async ({ request }) => {
        const body = (await request.json()) as { expectedVersion: number; notes: string };
        bodies.push(body);
        if (body.expectedVersion === 3) {
          return problem(409, { code: 'VERSION_CONFLICT', current: sheetFixture({ version: 6 }) });
        }
        return HttpResponse.json(sheetFixture({ version: 7, notes: body.notes }));
      }),
    );
    const { user } = await openSheet(sheetFixture(), '/campaigns/12?tab=notes');

    // вызов
    await user.type(screen.getByTestId('notes-input'), 'Мои заметки');

    // проверка
    await waitFor(() => expect(screen.getByTestId('notes-status')).toHaveTextContent('Сохранено'), { timeout: 3000 });
    expect(bodies).toEqual([
      { expectedVersion: 3, notes: 'Мои заметки' },
      { expectedVersion: 6, notes: 'Мои заметки' },
    ]);
    expect(screen.queryByText(/Состояние обновилось/)).not.toBeInTheDocument();
  });
});

describe('Лист кампании: инвентарь', () => {
  const kit = [
    { id: 401, kind: 'EQUIPMENT' as const, name: 'Большой меч', level: 1, element: null, source: null },
    { id: 404, kind: 'POTION' as const, name: 'Алемор', level: 1, element: null, source: 'LAB_01' },
    { id: 405, kind: 'REWARD' as const, name: 'Карта награды №7', level: null, element: null, source: '7' },
  ];
  const withKit = (items = kit) =>
    sheetFixture({
      hunters: [hunterFixture({ items }), hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2 })],
    });
  const inventoryItem = (name: string) => {
    const found = screen.getAllByTestId('inventory-item').find((node) => node.dataset.name === name);
    if (found === undefined) throw new Error(`Нет предмета «${name}»`);
    return within(found);
  };

  it('предметы по видам: снаряжение, зелья, карты наград; у карты награды уровня нет', async () => {
    // вызов
    await openSheet(withKit());

    // проверка
    const groups = await screen.findAllByTestId('inventory-group');
    expect(groups.map((group) => group.dataset.kind)).toEqual(['EQUIPMENT', 'POTION', 'REWARD']);
    expect(inventoryItem('Большой меч').getByTestId('inventory-item-level')).toHaveTextContent('1 ур.');
    expect(inventoryItem('Карта награды №7').queryByTestId('inventory-item-level')).not.toBeInTheDocument();
  });

  it('кампания без предметов (создана до инвентаря) — «Предметов нет»', async () => {
    // вызов
    await openSheet(withKit([]));

    // проверка
    expect(await screen.findByTestId('inventory-empty')).toHaveTextContent('Предметов нет.');
  });

  it('добавить без оплаты: вид, название, уровень; ответ — охотник целиком', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/items', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(
          hunterFixture({ items: [...kit, { id: 406, kind: 'EQUIPMENT', name: 'Язык пламени', level: 2, element: null, source: null }] }),
          { status: 201 },
        );
      }),
    );
    const { user } = await openSheet(withKit());

    // вызов
    await user.click(await screen.findByTestId('inventory-add'));
    const form = within(await screen.findByTestId('inventory-form'));
    expect(form.getByTestId('inventory-form-save')).toBeDisabled();
    await user.type(form.getByTestId('inventory-form-name'), 'Язык пламени');
    await user.selectOptions(form.getByTestId('inventory-form-level'), '2');
    await user.click(form.getByTestId('inventory-form-save'));

    // проверка
    expect(await screen.findByText('Язык пламени')).toBeInTheDocument();
    expect(bodies).toEqual([{ kind: 'EQUIPMENT', name: 'Язык пламени', level: 2 }]);
  });

  it('карта награды добавляется без уровня', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/items', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(hunterFixture({ items: kit }), { status: 201 });
      }),
    );
    const { user } = await openSheet(withKit([]));

    // вызов
    await user.click(await screen.findByTestId('inventory-add'));
    const form = within(await screen.findByTestId('inventory-form'));
    await user.selectOptions(form.getByTestId('inventory-form-kind'), 'REWARD');
    expect(form.queryByTestId('inventory-form-level')).not.toBeInTheDocument();
    await user.type(form.getByTestId('inventory-form-name'), 'Карта награды №3');
    await user.click(form.getByTestId('inventory-form-save'));

    // проверка
    await waitFor(() => expect(bodies).toEqual([{ kind: 'REWARD', name: 'Карта награды №3', level: null }]));
  });

  it('правка уровня и удаление', async () => {
    // подготовка
    const edits: unknown[] = [];
    let removed = 0;
    server.use(
      http.patch('*/api/v1/campaigns/12/hunters/31/items/401', async ({ request }) => {
        edits.push(await request.json());
        return HttpResponse.json(hunterFixture({ items: [{ ...kit[0]!, level: 3 }, ...kit.slice(1)] }));
      }),
      http.delete('*/api/v1/campaigns/12/hunters/31/items/404', () => {
        removed += 1;
        return HttpResponse.json(hunterFixture({ items: [kit[0]!, kit[2]!] }));
      }),
    );
    const { user } = await openSheet(withKit());

    // вызов: правка
    await user.click(await screen.findByLabelText('Изменить: Большой меч'));
    const form = within(await screen.findByTestId('inventory-form'));
    expect(form.getByTestId('inventory-form-name')).toHaveValue('Большой меч');
    await user.selectOptions(form.getByTestId('inventory-form-level'), '3');
    await user.click(form.getByTestId('inventory-form-save'));

    // проверка
    await waitFor(() => expect(inventoryItem('Большой меч').getByTestId('inventory-item-level')).toHaveTextContent('3 ур.'));
    expect(edits).toEqual([{ name: 'Большой меч', level: 3 }]);

    // вызов: удаление
    await user.click(screen.getByLabelText('Убрать: Алемор'));

    // проверка
    await waitFor(() => expect(screen.queryByText('Алемор')).not.toBeInTheDocument());
    expect(removed).toBe(1);
  });
});
