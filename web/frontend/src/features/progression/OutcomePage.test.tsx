import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { LocalBattle } from '../../domain/battle';
import { sheetFixture } from '../../test/fixtures/campaign';
import { finishedCampaignBattle, resultPreviewFixture, transitionFixture } from '../../test/fixtures/progression';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const RESULT_URL = '*/api/v1/campaigns/12/battles/:battleId/result';
const PREVIEW_URL = '*/api/v1/campaigns/12/battles/:battleId/result/preview';

const problem = (status: number, body: Record<string, unknown>) =>
  HttpResponse.json({ status, ...body }, { status, headers: { 'Content-Type': 'application/problem+json' } });

/** Экран итога с законченным боем кампании в браузере; тела запросов итога — в `bodies`. */
function openOutcome(battle: LocalBattle, preview = resultPreviewFixture()) {
  const bodies: { action: string | null; overrides: unknown }[] = [];
  let previews = 0;
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture())),
    http.post(PREVIEW_URL, () => {
      previews += 1;
      return HttpResponse.json(preview);
    }),
    http.post(RESULT_URL, async ({ request }) => {
      const body = (await request.json()) as { action: string | null; overrides: unknown };
      bodies.push(body);
      const next = body.action === 'DISMISS' ? 'CAMPAIGN_SHEET' : preview.next;
      return HttpResponse.json({ next, campaign: sheetFixture({ version: 5 }) });
    }),
    http.get('*/api/v1/campaigns/12/chapter-transition', () => HttpResponse.json(transitionFixture())),
  );
  const activeBattle = memoryActiveBattle();
  activeBattle.start(battle);
  const view = renderRoutes(routes, '/campaigns/12/outcome', { activeBattle });
  return { ...view, activeBattle, bodies, previews: () => previews, user: userEvent.setup() };
}

describe('Итог боя кампании', () => {
  it('превью наград; «Принять» — итог отправлен, бой удалён из браузера, дальше переход главы', async () => {
    // подготовка
    const { user, activeBattle, bodies, router } = openOutcome(finishedCampaignBattle());
    await screen.findByTestId('outcome-preview');
    expect(screen.getByText('Трофей: Коровон')).toBeInTheDocument();
    expect(screen.getByTestId('rewards-open-quests')).toHaveTextContent('Добавляются задания: 4');
    expect(screen.getAllByTestId('rewards-resource').map((badge) => badge.dataset.code)).toEqual(['CORAL', 'BONES']);
    expect(screen.getByTestId('rewards-rule')).toHaveTextContent('Добавлено задание 4.');

    // вызов
    await user.click(screen.getByTestId('outcome-accept'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12/transition'));
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toMatchObject({
      action: 'ACCEPT',
      overrides: null,
      questNumber: 1,
      bossCode: 'KOROVON',
      chapter: 2,
      progressSeq: 1,
      result: 'VICTORY',
      roundsPlayed: 4,
    });
    expect(activeBattle.getSnapshot().battle).toBeNull();
  });

  it('нет сети — результат остаётся в браузере (NOT_SENT), «Отправить снова» отправляет его', async () => {
    // подготовка
    const { user, activeBattle, router } = openOutcome(finishedCampaignBattle('DEFEAT'), resultPreviewFixture({ result: 'DEFEAT', next: 'CAMPAIGN_SHEET' }));
    let online = false;
    server.use(
      http.post(RESULT_URL, () =>
        online ? HttpResponse.json({ next: 'CAMPAIGN_SHEET', campaign: sheetFixture() }) : HttpResponse.error(),
      ),
    );
    await screen.findByTestId('outcome-preview');

    // вызов
    await user.click(screen.getByTestId('outcome-accept'));

    // проверка
    expect(await screen.findByTestId('result-not-sent')).toHaveTextContent('Результат боя не отправлен.');
    const kept = activeBattle.getSnapshot().battle;
    expect(kept?.submission).toEqual({ status: 'NOT_SENT', lastError: 'Нет связи с сервером. Проверьте подключение.' });

    // вызов: сеть вернулась
    online = true;
    await user.click(screen.getByTestId('result-resend'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(activeBattle.getSnapshot().battle).toBeNull();
  });

  it('итог, не ушедший из-за сети, отправляется сам, когда сеть возвращается', async () => {
    // подготовка
    const { user, router } = openOutcome(finishedCampaignBattle());
    let online = false;
    server.use(
      http.post(RESULT_URL, () =>
        online ? HttpResponse.json({ next: 'CHAPTER_TRANSITION', campaign: sheetFixture() }) : HttpResponse.error(),
      ),
    );
    await screen.findByTestId('outcome-preview');
    await user.click(screen.getByTestId('outcome-accept'));
    await screen.findByTestId('result-not-sent');

    // вызов
    online = true;
    window.dispatchEvent(new Event('online'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12/transition'));
  });

  it('409 CAMPAIGN_CHANGED — причины на экране, принять нельзя, только «Отклонить результат»', async () => {
    // подготовка
    const { user, bodies, router } = openOutcome(finishedCampaignBattle());
    server.use(
      http.post(PREVIEW_URL, () =>
        problem(409, {
          code: 'CAMPAIGN_CHANGED',
          detail: 'Пока шёл бой, кампания изменилась.',
          reasons: ['Глава 2 уже завершена: принята победа в бою с боссом «Озев» (результат от: Вадим).'],
          closedBy: { submittedAt: '2026-09-27T19:02:00Z' },
        }),
      ),
    );

    // проверка
    expect(await screen.findByTestId('result-changed-reason')).toHaveTextContent('Глава 2 уже завершена');
    expect(screen.queryByTestId('outcome-accept')).not.toBeInTheDocument();
    expect(screen.queryByTestId('outcome-edit-open')).not.toBeInTheDocument();

    // вызов
    await user.click(screen.getByTestId('result-dismiss-changed'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(bodies.map((body) => body.action)).toEqual(['DISMISS']);
  });

  it('«Отклонить» — сначала окно с последствиями, запрос только после подтверждения', async () => {
    // подготовка
    const { user, bodies } = openOutcome(finishedCampaignBattle());
    await screen.findByTestId('outcome-preview');

    // вызов
    await user.click(screen.getByTestId('outcome-dismiss'));

    // проверка
    const dialog = within(await screen.findByTestId('consequences-dialog'));
    expect(dialog.getAllByTestId('consequence').map((item) => item.textContent)).toEqual(
      resultPreviewFixture().dismissConsequences,
    );
    await user.click(dialog.getByTestId('consequences-cancel'));
    expect(bodies).toEqual([]);

    // вызов
    await user.click(screen.getByTestId('outcome-dismiss'));
    await user.click(within(await screen.findByTestId('consequences-dialog')).getByTestId('consequences-confirm'));

    // проверка
    await waitFor(() => expect(bodies.map((body) => body.action)).toEqual(['DISMISS']));
  });

  it('пролог (36.1): победа принимается без окна наград, сразу переход главы', async () => {
    // вызов
    const { bodies, previews, router } = openOutcome(finishedCampaignBattle('VICTORY', 'PROLOGUE'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12/transition'));
    expect(previews()).toBe(0);
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toMatchObject({ action: 'ACCEPT', questNumber: null, chapter: 0, bossCode: 'VIRAXEN' });
  });

  it('«Редактировать» отправляет исправленные награды', async () => {
    // подготовка
    server.use(
      http.get('*/api/v1/catalog/quests', () => HttpResponse.json([])),
      http.get('*/api/v1/catalog/achievements', () => HttpResponse.json([])),
    );
    const { user, bodies } = openOutcome(finishedCampaignBattle());
    await screen.findByTestId('outcome-preview');

    // вызов
    await user.click(screen.getByTestId('outcome-edit-open'));
    const bones = (await screen.findAllByTestId('outcome-edit-resource')).find((input) => input.dataset.code === 'BONES');
    if (bones === undefined) throw new Error('Нет поля «Кости»');
    await user.clear(bones);
    await user.type(bones, '5');
    await user.click(screen.getByTestId('outcome-edit-accept'));

    // проверка
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({
      action: 'ACCEPT',
      overrides: { bossCode: 'KOROVON', perHunter: { CORAL: 2, BONES: 5 }, openQuests: [4], achievements: [] },
    });
  });

  it('без законченного боя этой кампании — сообщение, запросов нет', async () => {
    // вызов
    server.use(signedIn());
    renderRoutes(routes, '/campaigns/12/outcome');

    // проверка
    expect(await screen.findByTestId('outcome-no-battle')).toBeInTheDocument();
  });

  it('экран победы боя кампании ведёт «К наградам»; меню показывает неотправленный результат (D-13)', async () => {
    // подготовка
    server.use(signedIn());
    const activeBattle = memoryActiveBattle();
    activeBattle.start(finishedCampaignBattle());
    const { router } = renderRoutes(routes, '/battle', { activeBattle });
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('battle-result-menu'));

    // проверка: выход в меню не удаляет бой с неотправленным результатом
    expect(await screen.findByTestId('pending-result-send')).toHaveAttribute('href', '/campaigns/12/outcome');
    expect(activeBattle.getSnapshot().battle).not.toBeNull();

    // вызов
    await router.navigate('/battle');
    await user.click(await screen.findByTestId('battle-to-rewards'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12/outcome'));
  });
});
