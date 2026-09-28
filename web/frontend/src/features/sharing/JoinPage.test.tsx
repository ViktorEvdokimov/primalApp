import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import { sheetFixture } from '../../test/fixtures/campaign';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const TOKEN = 'Q2hhcHRlcjEyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

function invitation() {
  const joins: unknown[] = [];
  server.use(
    http.get(`*/api/v1/share/${TOKEN}`, () => HttpResponse.json({ kind: 'CAMPAIGN', name: 'Кампания Алисы', ownerName: 'Алиса' })),
    http.post(`*/api/v1/share/${TOKEN}/join`, async ({ request }) => {
      joins.push(await request.json());
      return HttpResponse.json({ kind: 'CAMPAIGN', id: 12 });
    }),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture({ access: 'LINK' }))),
  );
  return joins;
}

describe('Вход по ссылке-приглашению', () => {
  it('гость без входа указывает имя, присоединяется; токен убирается из адресной строки', async () => {
    // подготовка
    const joins = invitation();
    let me = false;
    server.use(
      http.get('*/api/v1/auth/me', () =>
        me
          ? HttpResponse.json({ kind: 'GUEST', user: null, device: { id: 'd', displayName: 'Вадим' } })
          : HttpResponse.json({ status: 401, code: 'UNAUTHENTICATED' }, { status: 401 }),
      ),
    );
    const { router } = renderRoutes(routes, `/s/${TOKEN}`);
    const user = userEvent.setup();
    expect(await screen.findByTestId('join-campaign')).toHaveTextContent('Вас пригласили в кампанию «Кампания Алисы»');

    // вызов
    await user.type(screen.getByTestId('join-name'), 'Вадим');
    me = true;
    await user.click(screen.getByTestId('join-submit'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(router.state.historyAction).toBe('REPLACE');
    expect(joins).toEqual([{ displayName: 'Вадим' }]);
    expect(await screen.findByTestId('sheet-name')).toHaveTextContent('SheetTest');
  });

  it('вошедший пользователь присоединяется без имени', async () => {
    // подготовка
    const joins = invitation();
    server.use(signedIn());
    const { router } = renderRoutes(routes, `/s/${TOKEN}`);
    const user = userEvent.setup();
    await screen.findByTestId('join-campaign');
    expect(screen.queryByTestId('join-name')).not.toBeInTheDocument();

    // вызов
    await user.click(screen.getByTestId('join-submit'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(joins).toEqual([{ displayName: null }]);
  });

  it('отозванная ссылка — объяснение', async () => {
    // подготовка
    server.use(
      http.get(`*/api/v1/share/${TOKEN}`, () =>
        HttpResponse.json(
          { status: 404, code: 'SHARE_LINK_INVALID', detail: 'Ссылка недействительна' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    // вызов
    renderRoutes(routes, `/s/${TOKEN}`);

    // проверка
    expect(await screen.findByTestId('join-invalid')).toHaveTextContent('Ссылка недействительна: её отозвали или перевыпустили.');
  });
});
