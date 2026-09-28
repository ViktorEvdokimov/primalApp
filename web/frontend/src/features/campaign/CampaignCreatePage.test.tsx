import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { CampaignSummary } from '../../api/generated/primal.schemas';
import { guestMe } from '../../test/fixtures/auth';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const CLASSES = ['DAREON', 'MIRA', 'TOREG', 'LIONAR', 'KARA', 'HELEREN', 'DRUSK', 'ZARAIA'];

async function openCreate() {
  server.use(signedIn());
  const view = renderRoutes(routes, '/campaigns/new');
  await screen.findByTestId('page-campaign-new');
  await screen.findByText('Дареон');
  return { ...view, user: userEvent.setup() };
}

const chip = (code: string) => screen.getByTestId(`campaign-new-class-${code}`);

describe('Новая кампания', () => {
  it('создать можно только с названием и отрядом из 2–5 охотников', async () => {
    // подготовка
    const { user } = await openCreate();
    await user.type(screen.getByTestId('campaign-new-name'), 'Кампания Алисы');

    // вызов и проверка: один охотник — нельзя
    await user.click(chip('DAREON'));
    expect(screen.getByTestId('campaign-new-submit')).toBeDisabled();
    await user.click(chip('MIRA'));
    expect(screen.getByTestId('campaign-new-submit')).toBeEnabled();

    // пять выбранных — остальные классы недоступны
    for (const code of ['TOREG', 'LIONAR', 'KARA']) await user.click(chip(code));
    expect(screen.getByTestId('campaign-new-count')).toHaveTextContent('Выбрано: 5 из 5');
    expect(chip('HELEREN')).toBeDisabled();
    expect(chip('ZARAIA')).toBeDisabled();
    expect(chip('DAREON')).toBeEnabled();

    // снятие класса снова открывает выбор
    await user.click(chip('KARA'));
    expect(chip('HELEREN')).toBeEnabled();
  });

  it('создание отправляет отряд в порядке выбора и открывает подготовку пролога', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ id: 12 }, { status: 201 });
      }),
    );
    const { user, router } = await openCreate();
    await user.type(screen.getByTestId('campaign-new-name'), '  Кампания Алисы ');
    await user.click(chip('MIRA'));
    await user.click(chip('DAREON'));
    await user.type(screen.getByTestId('campaign-new-player-MIRA'), 'Алиса');

    // вызов
    await user.click(screen.getByTestId('campaign-new-submit'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12/battle/new'));
    expect(bodies).toEqual([
      {
        name: 'Кампания Алисы',
        hunters: [
          { class: 'MIRA', playerName: 'Алиса' },
          { class: 'DAREON', playerName: '' },
        ],
      },
    ]);
  });

  it('ошибка сервера (лимит кампаний) показывается текстом', async () => {
    // подготовка
    server.use(
      http.post('*/api/v1/campaigns', () =>
        HttpResponse.json(
          { status: 422, code: 'CAMPAIGN_LIMIT_REACHED', detail: 'У вас уже 10 кампаний.' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const { user } = await openCreate();
    await user.type(screen.getByTestId('campaign-new-name'), 'Одиннадцатая');
    await user.click(chip('DAREON'));
    await user.click(chip('MIRA'));

    // вызов
    await user.click(screen.getByTestId('campaign-new-submit'));

    // проверка
    expect(await screen.findByTestId('campaign-new-error')).toHaveTextContent('У вас уже 10 кампаний.');
  });

  it('все 8 классов доступны для выбора', async () => {
    // вызов
    await openCreate();

    // проверка
    for (const code of CLASSES) expect(chip(code)).toBeEnabled();
  });
});

describe('Список кампаний', () => {
  const campaign: CampaignSummary = {
    id: 12,
    name: 'Кампания Алисы',
    chapter: 0,
    status: 'ACTIVE',
    access: 'OWNER',
    ownerName: 'Алиса',
    hunters: [
      { class: 'DAREON', playerName: 'Алиса' },
      { class: 'MIRA', playerName: 'Вадим' },
    ],
    pendingTransition: false,
    updatedAt: '2026-09-28T10:00:00Z',
  };

  it('кампания видна с главой «Пролог» и отрядом', async () => {
    // подготовка
    server.use(signedIn(), http.get('*/api/v1/campaigns', () => HttpResponse.json([campaign])));

    // вызов
    renderRoutes(routes, '/campaigns');

    // проверка
    expect(await screen.findByTestId('campaign-item-name')).toHaveTextContent('Кампания Алисы');
    expect(screen.getByTestId('campaign-item-chapter')).toHaveTextContent('Пролог');
    await waitFor(() => expect(screen.getByTestId('campaign-item-hunters')).toHaveTextContent('Дареон — Алиса, Мира — Вадим'));
    expect(screen.getByTestId('campaign-item-open')).toHaveAttribute('href', '/campaigns/12');
  });

  it('«В главное меню» возвращает в главное меню', async () => {
    // подготовка
    server.use(signedIn(), http.get('*/api/v1/campaigns', () => HttpResponse.json([campaign])));
    renderRoutes(routes, '/campaigns');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('campaigns-to-menu'));

    // проверка
    expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
  });

  it('удаление — после подтверждения', async () => {
    // подготовка
    let deleted = false;
    server.use(
      signedIn(),
      http.get('*/api/v1/campaigns', () => HttpResponse.json(deleted ? [] : [campaign])),
      http.delete('*/api/v1/campaigns/12', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoutes(routes, '/campaigns');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('campaign-item-delete'));

    // вызов
    await user.click(await screen.findByTestId('campaign-delete-confirm'));

    // проверка
    expect(await screen.findByTestId('campaigns-empty')).toBeInTheDocument();
    expect(deleted).toBe(true);
  });

  it('гость не создаёт кампании и не удаляет чужие', async () => {
    // подготовка
    server.use(signedIn(guestMe), http.get('*/api/v1/campaigns', () => HttpResponse.json([{ ...campaign, access: 'LINK' }])));

    // вызов
    renderRoutes(routes, '/campaigns');

    // проверка
    expect(await screen.findByTestId('campaign-item-name')).toBeInTheDocument();
    expect(screen.queryByTestId('campaigns-create')).not.toBeInTheDocument();
    expect(screen.queryByTestId('campaign-item-delete')).not.toBeInTheDocument();
    expect(screen.getByText('Владелец: Алиса')).toBeInTheDocument();
  });
});
