import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import type { CampaignSheet } from '../../api/generated/primal.schemas';
import { hunterFixture, sheetFixture } from '../../test/fixtures/campaign';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const problem = (status: number, body: Record<string, unknown>) =>
  HttpResponse.json({ status, ...body }, { status, headers: { 'Content-Type': 'application/problem+json' } });

/** Лист с открытыми кузнями огня и коралла; у Дареона хватает на «Язык пламени». */
function forgeSheet(overrides: Partial<CampaignSheet> = {}): CampaignSheet {
  return sheetFixture({
    openForges: ['FIRE', 'CORAL'],
    hunters: [
      hunterFixture({ resources: { FIRE: 2, BONES: 1, BLOOD: 1 } }),
      hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2, resources: {} }),
    ],
    ...overrides,
  });
}

function serveSheet(sheet: CampaignSheet) {
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheet)),
  );
}

async function openForge(sheet: CampaignSheet = forgeSheet()) {
  serveSheet(sheet);
  const view = renderRoutes(routes, '/campaigns/12/forge');
  await screen.findByTestId('forge-level');
  return { ...view, user: userEvent.setup() };
}

const section = (element: string) => {
  const found = screen.getAllByTestId('forge-section').find((node) => node.dataset.element === element);
  if (found === undefined) throw new Error(`Нет кузни ${element}`);
  return within(found);
};

const item = (name: string) => {
  const found = screen.getAllByTestId('forge-item').find((node) => node.dataset.name === name);
  if (found === undefined) throw new Error(`Нет предмета «${name}»`);
  return within(found);
};

const names = (element: string) =>
  section(element)
    .getAllByTestId('forge-item')
    .map((node) => node.dataset.name);

describe('Кузница', () => {
  const scrollIntoView = vi.fn();

  beforeEach(() => {
    Element.prototype.scrollIntoView = scrollIntoView;
  });

  afterEach(() => {
    scrollIntoView.mockReset();
  });

  it('кнопка «Кузница» на листе кампании открывает кузницу', async () => {
    // подготовка
    serveSheet(forgeSheet());
    const { router } = renderRoutes(routes, '/campaigns/12');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('sheet-open-forge'));

    // проверка
    expect(await screen.findByTestId('forge-level')).toHaveTextContent('Кузня: 1');
    expect(router.state.location.pathname).toBe('/campaigns/12/forge');
  });

  it('кузни не открыты — пояснение, без планшетов', async () => {
    // вызов
    await openForge(forgeSheet({ openForges: [] }));

    // проверка
    expect(screen.getByTestId('forge-none')).toHaveTextContent('кузня стихии открывается победой над боссом');
    expect(screen.queryByTestId('forge-section')).not.toBeInTheDocument();
  });

  it('только открытые кузни: кнопки перехода и планшеты огня и коралла по порядку', async () => {
    // вызов
    const { user } = await openForge();

    // проверка
    const jumps = screen.getAllByTestId('forge-jump');
    expect(jumps.map((node) => node.dataset.element)).toEqual(['FIRE', 'CORAL']);
    expect(jumps.map((node) => node.textContent)).toEqual(['Огонь', 'Коралл']);
    expect(screen.getAllByTestId('forge-section').map((node) => node.dataset.element)).toEqual(['FIRE', 'CORAL']);
    expect(section('FIRE').getByText('Кузня огня · 1-й уровень')).toBeInTheDocument();

    // вызов: переход к кораллу
    await user.click(jumps[1]!);

    // проверка
    expect(scrollIntoView).toHaveBeenCalledOnce();
    expect(scrollIntoView.mock.contexts[0]).toBe(document.getElementById('forge-coral'));
  });

  it('оружие — только класса выбранного охотника; шлем, доспех и предметы — всем', async () => {
    // вызов
    const { user } = await openForge();

    // проверка: Дареон
    expect(names('FIRE')).toEqual([
      'Язык пламени',
      'Чешуйчатый шлем',
      'Чешуйчатый доспех',
      'Перчатка Волтьяра',
      'Лавовый щит',
    ]);
    expect(item('Язык пламени').getByTestId('forge-item-slot')).toHaveTextContent('Оружие · большой меч');

    // вызов: Мира
    await user.click(screen.getAllByTestId('forge-hunter-option')[1]!);

    // проверка
    expect(names('FIRE')).toEqual([
      'Лук-испепелитель',
      'Чешуйчатый шлем',
      'Чешуйчатый доспех',
      'Перчатка Волтьяра',
      'Лавовый щит',
    ]);
  });

  it('цена — 1 стихия и материи текущего уровня; чего не хватает — подсказка, «Создать» недоступна', async () => {
    // вызов
    await openForge();

    // проверка: «Язык пламени» 1-го уровня — огонь, кости, кровь; у Дареона всё есть
    const costs = item('Язык пламени').getAllByTestId('forge-cost');
    expect(costs.map((node) => [node.dataset.code, node.dataset.quantity])).toEqual([
      ['FIRE', '1'],
      ['BONES', '1'],
      ['BLOOD', '1'],
    ]);
    expect(item('Язык пламени').getByTestId('forge-item-create')).toBeEnabled();
    expect(item('Язык пламени').queryByTestId('forge-item-missing')).not.toBeInTheDocument();
    // «Лавовый щит» — 2 чешуи, чешуи нет
    expect(item('Лавовый щит').getByTestId('forge-item-missing')).toHaveTextContent('Не хватает: Чешуя 2');
    expect(item('Лавовый щит').getByTestId('forge-item-create')).toBeDisabled();
  });

  it('на 2-м уровне кузни — цены 2-го уровня', async () => {
    // вызов
    await openForge(forgeSheet({ forgeLevel: 2 }));

    // проверка: «Язык пламени» 2-го уровня — чешуя и кровь
    expect(section('FIRE').getByText('Кузня огня · 2-й уровень')).toBeInTheDocument();
    const costs = item('Язык пламени').getAllByTestId('forge-cost');
    expect(costs.map((node) => node.dataset.code)).toEqual(['FIRE', 'SCALES', 'BLOOD']);
  });

  it('«Создать» — подтверждение со списанием, запрос, новый запас и сообщение', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/forge', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(
          {
            item: 'FIRE_01',
            name: 'Язык пламени',
            level: 1,
            hunter: hunterFixture({
              resources: { FIRE: 1 },
              items: [{ id: 501, kind: 'EQUIPMENT', name: 'Язык пламени', level: 1, element: 'FIRE', source: 'FIRE_01' }],
            }),
          },
          { status: 201 },
        );
      }),
    );
    const { user } = await openForge();

    // вызов: отмена ничего не отправляет
    await user.click(item('Язык пламени').getByTestId('forge-item-create'));
    const dialog = within(await screen.findByTestId('forge-confirm'));
    expect(dialog.getByText('«Язык пламени» 1-го уровня для охотника Боец.')).toBeInTheDocument();
    expect(dialog.getByText('Будет списано: Огонь 1, Кости 1, Кровь 1.')).toBeInTheDocument();
    await user.click(dialog.getByTestId('forge-confirm-cancel'));
    expect(bodies).toEqual([]);

    // вызов: создание
    await user.click(item('Язык пламени').getByTestId('forge-item-create'));
    await user.click(within(await screen.findByTestId('forge-confirm')).getByTestId('forge-confirm-ok'));

    // проверка
    expect(
      await screen.findByText('«Язык пламени» создан — возьмите из коробки карту 1-го уровня.'),
    ).toBeInTheDocument();
    expect(bodies).toEqual([{ item: 'FIRE_01' }]);
    await waitFor(() =>
      expect(screen.getAllByTestId('forge-stock-item').map((node) => node.dataset.code)).toEqual(['FIRE']),
    );
    expect(item('Язык пламени').getByTestId('forge-item-missing')).toHaveTextContent('Не хватает: Кости 1, Кровь 1');
  });

  it('отказ сервера — его текст', async () => {
    // подготовка
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/forge', () =>
        problem(422, { code: 'FORGE_UNAVAILABLE', detail: 'Кузня стихии «Огонь» ещё не открыта.' }),
      ),
    );
    const { user } = await openForge();

    // вызов
    await user.click(item('Язык пламени').getByTestId('forge-item-create'));
    await user.click(within(await screen.findByTestId('forge-confirm')).getByTestId('forge-confirm-ok'));

    // проверка
    expect(await screen.findByText('Кузня стихии «Огонь» ещё не открыта.')).toBeInTheDocument();
  });
});
