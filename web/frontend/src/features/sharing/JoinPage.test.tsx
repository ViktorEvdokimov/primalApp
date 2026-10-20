import { screen, waitFor, within } from '@testing-library/react';
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
    http.get(`*/api/v1/share/${TOKEN}`, () =>
      HttpResponse.json({
        kind: 'CAMPAIGN',
        name: 'Кампания Алисы',
        ownerName: 'Алиса',
        hunters: [
          { class: 'DAREON', playerName: 'Алиса' },
          { class: 'MIRA', playerName: 'Вадим' },
        ],
      }),
    ),
    http.post(`*/api/v1/share/${TOKEN}/join`, async ({ request }) => {
      joins.push(await request.json());
      return HttpResponse.json({ kind: 'CAMPAIGN', id: 12 });
    }),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture({ access: 'LINK', mainHunter: 'MIRA' }))),
  );
  return joins;
}

describe('Вход по ссылке-приглашению', () => {
  it('гость сразу видит окно выбора персонажа и присоединяется им; токен убирается из адресной строки', async () => {
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
    const dialog = await screen.findByRole('dialog', { name: 'Кто вы в этой кампании?' });
    expect(within(dialog).getByText('Имя его игрока станет вашим именем в истории боёв.')).toBeInTheDocument();
    await waitFor(() =>
      expect(within(dialog).getAllByTestId('join-hunter').map((button) => button.textContent)).toEqual([
        'Алиса · Дареон',
        'Вадим · Мира',
      ]),
    );
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();

    // вызов
    me = true;
    await user.click(within(dialog).getByRole('button', { name: 'Вадим · Мира' }));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(router.state.historyAction).toBe('REPLACE');
    expect(joins).toEqual([{ displayName: null, hunterClass: 'MIRA' }]);
    expect(await screen.findByTestId('sheet-name')).toHaveTextContent('SheetTest');
  });

  it('вошедший пользователь выбирает персонажа — без пояснения про имя гостя', async () => {
    // подготовка
    const joins = invitation();
    server.use(signedIn());
    const { router } = renderRoutes(routes, `/s/${TOKEN}`);
    const user = userEvent.setup();
    const dialog = await screen.findByRole('dialog', { name: 'Кто вы в этой кампании?' });
    expect(within(dialog).queryByText(/станет вашим именем/)).not.toBeInTheDocument();

    // вызов
    await user.click(await within(dialog).findByRole('button', { name: 'Алиса · Дареон' }));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(joins).toEqual([{ displayName: null, hunterClass: 'DAREON' }]);
  });

  it('закрытое окно открывается снова кнопкой «Выбрать персонажа»', async () => {
    // подготовка
    invitation();
    server.use(signedIn());
    renderRoutes(routes, `/s/${TOKEN}`);
    const user = userEvent.setup();
    await screen.findByRole('dialog', { name: 'Кто вы в этой кампании?' });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // вызов
    await user.click(screen.getByTestId('join-submit'));

    // проверка
    expect(await screen.findByRole('dialog', { name: 'Кто вы в этой кампании?' })).toBeInTheDocument();
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
