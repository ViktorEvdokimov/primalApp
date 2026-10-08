import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import { createLocalBattle } from '../../domain/battle';
import { sheetFixture } from '../../test/fixtures/campaign';
import { infoFixture, reactionEntry } from '../../test/fixtures/info';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const entry = (id: number) => {
  const found = screen.getAllByTestId('info-entry').find((node) => node.dataset.entry === String(id));
  if (found === undefined) throw new Error(`Нет статьи ${id}`);
  return found;
};

describe('Инфо', () => {
  beforeEach(() => {
    server.use(http.get('*/api/v1/info', () => HttpResponse.json(infoFixture())));
  });

  it('без входа: три раздела — у пустого «скоро»', async () => {
    // вызов
    renderRoutes(routes, '/info');

    // проверка
    const sections = await screen.findAllByTestId('info-section');
    expect(sections.map((node) => node.dataset.section)).toEqual(['KEYWORDS', 'REACTIONS', 'TOKENS']);
    expect(sections.map((node) => node.textContent)).toEqual([
      'Ключевые слова2',
      'Символы реакций монстровскоро',
      'Жетоны окружения1',
    ]);
  });

  it('поиск по всем разделам; результат открывает раздел с раскрытой статьёй', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/info');
    const user = userEvent.setup();

    // вызов
    await user.type(await screen.findByTestId('info-search'), 'препятствие');

    // проверка
    const results = screen.getAllByTestId('info-result');
    expect(results.map((node) => node.textContent)).toEqual(['Камень · Жетоны окружения']);

    // вызов
    await user.click(results[0]!);

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/info/tokens'));
    expect(router.state.location.hash).toBe('#entry-3');
    expect(await within(entry(3)).findByText(/Препятствие на поле/)).toBeVisible();
  });

  it('статья: картинка, абзацы, **жирный**; «См. также» — ссылка открывает нужную статью', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/info/keywords#entry-2');
    const user = userEvent.setup();

    // проверка
    const stamina = within(await waitFor(() => entry(2)));
    expect(await stamina.findByTestId('info-entry-image')).toHaveAttribute(
      'src',
      '/api/v1/info/images/0b8f0000-0000-4000-8000-000000000001',
    );
    const links = stamina.getAllByTestId('info-link');
    expect(links.map((node) => node.textContent)).toEqual(['«Защита»']);
    expect(stamina.getByText(/«Неизвестное слово»/)).toBeInTheDocument();

    // вызов
    await user.click(links[0]!);

    // проверка
    await waitFor(() => expect(router.state.location.hash).toBe('#entry-1'));
    expect(await within(entry(1)).findByText('Примечание.')).toBeVisible();
  });

  it('фильтр в разделе; пустой раздел — «Здесь пока ничего нет.»', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/info/keywords');
    const user = userEvent.setup();

    // вызов
    await user.type(await screen.findByTestId('info-filter'), 'складываются');

    // проверка
    expect(screen.getAllByTestId('info-entry').map((node) => node.dataset.entry)).toEqual(['1']);

    // вызов
    await router.navigate('/info/reactions');

    // проверка
    expect(await screen.findByTestId('info-empty')).toHaveTextContent('Здесь пока ничего нет.');
  });

  it('символы реакций: карточки — картинка и описание, без названия; ссылка в описании работает', async () => {
    // подготовка
    const view = infoFixture();
    view.sections[1]!.entries = [reactionEntry];
    server.use(http.get('*/api/v1/info', () => HttpResponse.json(view)));
    const { router } = renderRoutes(routes, '/info/reactions');
    const user = userEvent.setup();

    // проверка
    const card = within(await waitFor(() => entry(4)));
    expect(card.getByTestId('info-entry-image')).toHaveAttribute(
      'src',
      '/api/v1/info/images/0b8f0000-0000-4000-8000-000000000004',
    );
    expect(card.getByText('разворачивается')).toBeVisible();
    expect(card.queryByTestId('info-entry-title')).not.toBeInTheDocument();

    // вызов
    await user.click(card.getByTestId('info-link'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/info/keywords'));
  });

  it('жетоны окружения: карточки — картинка, название и описание видны без раскрытия', async () => {
    // подготовка
    const view = infoFixture();
    view.sections[2]!.entries = [{ ...view.sections[2]!.entries[0]!, imageUrl: '/api/v1/info/images/stone' }];
    server.use(http.get('*/api/v1/info', () => HttpResponse.json(view)));

    // вызов
    renderRoutes(routes, '/info/tokens');

    // проверка
    const card = within(await waitFor(() => entry(3)));
    expect(card.getByTestId('info-entry-image')).toHaveAttribute('src', '/api/v1/info/images/stone');
    expect(card.getByTestId('info-card-title')).toHaveTextContent('Камень');
    expect(card.getByText(/Препятствие на поле/)).toBeVisible();
    expect(card.queryByTestId('info-entry-title')).not.toBeInTheDocument();
  });

  it('поиск находит символ реакции по описанию — подпись из начала описания', async () => {
    // подготовка
    const view = infoFixture();
    view.sections[1]!.entries = [reactionEntry];
    server.use(http.get('*/api/v1/info', () => HttpResponse.json(view)));
    renderRoutes(routes, '/info');
    const user = userEvent.setup();

    // вызов
    await user.type(await screen.findByTestId('info-search'), 'разворачивается');

    // проверка
    expect(screen.getAllByTestId('info-result').map((node) => node.textContent)).toEqual([
      'Монстр разворачивается к охотнику. (См. также «Защита» .) · Символы реакций монстров',
    ]);
  });

  it('главное меню: «Инфо» без входа; «Назад» — в меню', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('menu-info'));

    // проверка
    expect(await screen.findAllByTestId('info-section')).toHaveLength(3);
    await user.click(screen.getByTestId('info-back'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('ссылка «Инфо» — в бою и на листе кампании; «Назад» возвращает', async () => {
    // подготовка: бой
    const activeBattle = memoryActiveBattle();
    activeBattle.start(
      createLocalBattle({
        id: '0b8f6c1e-8a51-4a3e-9d7c-2f0f6a3f9b12',
        mode: 'EXPEDITION',
        campaign: null,
        boss: null,
        difficulty: 0,
        stances: [],
        params: { hunterCount: 2, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        now: '2026-10-07T18:00:00.000Z',
      }),
    );
    const { router } = renderRoutes(routes, '/battle', { activeBattle });
    const user = userEvent.setup();

    // вызов и проверка
    await user.click(await screen.findByTestId('battle-info'));
    await screen.findAllByTestId('info-section');
    await user.click(screen.getByTestId('info-back'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/battle'));

    // подготовка: лист кампании
    server.use(
      signedIn(),
      http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture())),
    );
    await router.navigate('/campaigns/12');

    // вызов и проверка
    expect(await screen.findByTestId('sheet-open-info')).toHaveAttribute('href', '/info');
  });
});
