import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { ActiveBattle as ActiveBattleView, BattleSetup } from '../../api/generated/primal.schemas';
import { sheetFixture } from '../../test/fixtures/campaign';
import { prologueSetupFixture, setupFixture } from '../../test/fixtures/progression';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const otherBattle: ActiveBattleView = {
  id: '5d1a0000-0000-4000-8000-000000000001',
  startedBy: { kind: 'GUEST', name: 'Вадим' },
  startedAt: '2026-09-27T18:30:00Z',
  quest: null,
  boss: { code: 'KOROVON', name: 'Коровон', element: 'CORAL' },
};

/** Подготовка боя кампании 12: `setup` — ответ battle-setup, `marks` — тела отметок о начале. */
function openSetup(setup: (questNumber: string | null) => BattleSetup, path = '/campaigns/12/battle/new', markStatus = 201) {
  const marks: unknown[] = [];
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture())),
    http.get('*/api/v1/campaigns/12/battle-setup', ({ request }) =>
      HttpResponse.json(setup(new URL(request.url).searchParams.get('questNumber'))),
    ),
    http.post('*/api/v1/campaigns/12/battles', async ({ request }) => {
      marks.push(await request.json());
      return markStatus === 201
        ? HttpResponse.json({ id: 'x', status: 'IN_PROGRESS', otherActiveBattles: [] }, { status: 201 })
        : HttpResponse.json({ status: markStatus, code: 'INTERNAL_ERROR' }, { status: markStatus });
    }),
  );
  const activeBattle = memoryActiveBattle();
  const view = renderRoutes(routes, path, { activeBattle });
  return { ...view, activeBattle, marks, user: userEvent.setup() };
}

describe('Подготовка боя кампании', () => {
  it('выбор задания: открытые задания и «Продолжить без задания»; задание открывает подготовку с его боссом', async () => {
    // подготовка
    const { user, router } = openSetup((quest) =>
      quest === '1'
        ? setupFixture({ purpose: 'QUEST', boss: { code: 'KOROVON', name: 'Коровон', element: 'CORAL' } })
        : setupFixture(),
    );
    const items = await screen.findAllByTestId('quest-select-item');
    expect(items.map((item) => item.dataset.number)).toEqual(['1', '2']);
    expect(screen.getByTestId('quest-select-free')).toBeInTheDocument();

    // вызов
    await user.click(items[0]!);

    // проверка
    expect(await screen.findByTestId('campaign-battle-purpose')).toHaveTextContent('Задание 1 «Вой в долине»');
    expect(router.state.location.search).toBe('?quest=1');
    expect(screen.getByTestId('campaign-battle-boss')).toHaveValue('KOROVON');
    expect(screen.getByTestId('campaign-battle-hunters')).toHaveValue('2');
  });

  it('пролог: выбора задания нет, Вираксен без выбора, сложность 0', async () => {
    // вызов
    openSetup(() => prologueSetupFixture());

    // проверка
    expect(await screen.findByTestId('campaign-battle-purpose')).toHaveTextContent('Пролог');
    expect(screen.queryByTestId('quest-select')).not.toBeInTheDocument();
    expect(screen.getByTestId('campaign-battle-boss')).toBeDisabled();
    expect(screen.getByTestId('campaign-battle-boss')).toHaveValue('VIRAXEN');
    expect(screen.getByTestId('campaign-battle-forced-boss')).toHaveTextContent('Вираксен');
  });

  it('старт: бой кампании со снимком главы и progressSeq, отметка о начале дошла', async () => {
    // подготовка
    const { user, router, activeBattle, marks } = openSetup(() => prologueSetupFixture());
    await screen.findByTestId('campaign-battle-purpose');

    // вызов
    await user.click(screen.getByTestId('campaign-battle-start'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/battle'));
    const battle = activeBattle.getSnapshot().battle;
    expect(battle).toMatchObject({
      mode: 'CAMPAIGN',
      boss: { code: 'VIRAXEN' },
      difficulty: 0,
      campaign: { id: 12, name: 'SheetTest', chapter: 0, progressSeq: 0, purpose: 'PROLOGUE', questNumber: null },
    });
    await waitFor(() => expect(activeBattle.getSnapshot().battle?.campaign?.startMarked).toBe(true));
    expect(marks).toEqual([expect.objectContaining({ id: battle?.id, chapter: 0, progressSeq: 0, bossCode: 'VIRAXEN' })]);
  });

  it('идущий бой — предупреждение, начать бой всё равно можно', async () => {
    // подготовка
    const { user, router } = openSetup(() => setupFixture({ activeBattles: [otherBattle] }), '/campaigns/12/battle/new?quest=free');
    await screen.findByTestId('campaign-battle-purpose');

    // вызов
    await user.click(screen.getByTestId('campaign-battle-start'));

    // проверка
    const warning = within(await screen.findByTestId('active-battle-warning'));
    expect(warning.getByTestId('active-battle-warning-item')).toHaveTextContent('Вадим, бой с боссом «Коровон», с');
    await user.click(warning.getByTestId('active-battle-warning-start'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/battle'));
  });

  it('ошибка отметки о начале не мешает бою', async () => {
    // подготовка
    const { user, router, activeBattle, marks } = openSetup(() => prologueSetupFixture(), '/campaigns/12/battle/new', 500);
    await screen.findByTestId('campaign-battle-purpose');

    // вызов
    await user.click(screen.getByTestId('campaign-battle-start'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/battle'));
    await waitFor(() => expect(marks).toHaveLength(1));
    expect(activeBattle.getSnapshot().battle?.campaign?.startMarked).toBe(false);
    expect(await screen.findByTestId('battle-campaign')).toHaveTextContent('SheetTest · Пролог');
  });

  it('ожидающий переход главы — причина и кнопка «Переход главы»', async () => {
    // подготовка
    server.use(
      signedIn(),
      http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture())),
      http.get('*/api/v1/campaigns/12/battle-setup', () =>
        HttpResponse.json(
          { status: 409, code: 'CHAPTER_TRANSITION_PENDING', detail: 'Глава 1 завершена: сначала завершите переход главы.' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    // вызов
    renderRoutes(routes, '/campaigns/12/battle/new');

    // проверка
    expect(await screen.findByTestId('campaign-battle-error')).toHaveTextContent('Глава 1 завершена');
    expect(screen.getByTestId('campaign-battle-to-transition')).toHaveAttribute('href', '/campaigns/12/transition');
  });
});
