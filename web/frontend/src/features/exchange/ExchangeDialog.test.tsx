import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import type { CampaignSheet, InventoryItem } from '../../api/generated/primal.schemas';
import { hunterFixture, sheetFixture } from '../../test/fixtures/campaign';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const sword: InventoryItem = { id: 401, kind: 'EQUIPMENT', name: 'Большой меч', level: 1, element: null, source: null };
const alemor: InventoryItem = { id: 404, kind: 'POTION', name: 'Алемор', level: 1, element: null, source: 'LAB_01' };

/** Кузня огня открыта; на «Лавовый щит» Дареону не хватает 2 чешуи, у Миры их две. */
function exchangeSheet(): CampaignSheet {
  return sheetFixture({
    openForges: ['FIRE'],
    hunters: [
      hunterFixture({ resources: { FIRE: 2, BONES: 1, BLOOD: 1 }, items: [sword, alemor] }),
      hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2, resources: { SCALES: 2 } }),
    ],
  });
}

async function openExchange() {
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(exchangeSheet())),
  );
  renderRoutes(routes, '/campaigns/12/forge');
  await screen.findByTestId('forge-level');
  const user = userEvent.setup();
  const shield = screen.getAllByTestId('forge-item').find((node) => node.dataset.name === 'Лавовый щит');
  if (shield === undefined) throw new Error('Нет «Лавового щита»');
  await user.click(within(shield).getByTestId('forge-item-exchange'));
  return { user, dialog: within(await screen.findByTestId('exchange-dialog')) };
}

const options = (select: HTMLElement) => [...(select as HTMLSelectElement).options].map((option) => option.text);

describe('«Обменять ресурсы» из кузницы', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('обмен: «что получить» — недостающая чешуя; взамен — материя Бойца; с кем — только у кого есть чешуя', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/exchange', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ hunters: exchangeSheet().hunters });
      }),
    );
    const { user, dialog } = await openExchange();

    // проверка: предзаполнено
    expect(dialog.getByTestId('exchange-lacking')).toHaveTextContent('Охотнику Боец не хватает: Чешуя 2.');
    expect(dialog.getByTestId('exchange-want')).toHaveValue('SCALES');
    expect(options(dialog.getByTestId('exchange-offer'))).toEqual(['Кости (1)', 'Кровь (1)']);
    expect(options(dialog.getByTestId('exchange-partner'))).toEqual(['Мира (2)']);

    // вызов: взамен — кровь
    await user.selectOptions(dialog.getByTestId('exchange-offer'), 'BLOOD');
    await user.click(dialog.getByTestId('exchange-submit'));

    // проверка
    expect(await screen.findByText('Боец отдал(а) «Кровь» охотнику Мира и получил(а) «Чешуя».')).toBeInTheDocument();
    expect(bodies).toEqual([{ fromHunterId: 31, toHunterId: 32, give: { BLOOD: 1 }, receive: { SCALES: 1 } }]);
  });

  it('преобразование: 1 стихия → недостающая материя', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/convert', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(hunterFixture({ resources: { FIRE: 1, SCALES: 1, BONES: 1, BLOOD: 1 } }));
      }),
    );
    const { user, dialog } = await openExchange();

    // вызов
    await user.click(dialog.getByText('Преобразование'));
    expect(dialog.getByTestId('exchange-gain')).toHaveValue('SCALES');
    await user.click(dialog.getByTestId('exchange-submit'));

    // проверка
    expect(await screen.findByText('Огонь → Чешуя.')).toBeInTheDocument();
    expect(bodies).toEqual([{ spend: ['FIRE'], gain: 'SCALES' }]);
  });

  it('преобразование: 2 одинаковые материи, а есть одна — запаса не хватает', async () => {
    // подготовка
    const { user, dialog } = await openExchange();

    // вызов
    await user.click(dialog.getByText('Преобразование'));
    await user.click(dialog.getByText('2 материи → 1 материя'));
    await user.selectOptions(dialog.getByTestId('exchange-spend-second'), 'BONES');

    // проверка
    expect(dialog.getByTestId('exchange-not-enough')).toBeInTheDocument();
    expect(dialog.getByTestId('exchange-submit')).toBeDisabled();
  });

  it('продажа: зелья в списке нет, за карту — недостающая материя', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/items/401/sell', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(hunterFixture({ resources: { FIRE: 2, BONES: 1, BLOOD: 1, SCALES: 1 }, items: [alemor] }));
      }),
    );
    const { user, dialog } = await openExchange();

    // вызов
    await user.click(dialog.getByText('Продажа предмета'));
    const cards = [...(dialog.getByTestId('exchange-card') as HTMLSelectElement).options].map((option) => option.text);
    expect(cards).toEqual(['Большой меч · 1']);
    expect(dialog.getByTestId('exchange-gain')).toHaveValue('SCALES');
    await user.click(dialog.getByTestId('exchange-submit'));

    // проверка
    expect(await screen.findByText('«Большой меч» сброшена — получено: Чешуя.')).toBeInTheDocument();
    expect(bodies).toEqual([{ gain: 'SCALES' }]);
  });
});

describe('«Обменять ресурсы» из инвентаря', () => {
  /** Отряд из трёх: у Миры — огонь и чешуя, у Торега — только чешуя. */
  function squadSheet(): CampaignSheet {
    return sheetFixture({
      hunters: [
        hunterFixture({ resources: { HORN: 1, BONES: 2 } }),
        hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2, resources: { FIRE: 1, SCALES: 1 } }),
        hunterFixture({ id: 33, class: 'TOREG', playerName: 'Торег', position: 3, resources: { SCALES: 3 } }),
      ],
    });
  }

  async function openFromSheet() {
    server.use(
      signedIn(),
      http.get('*/api/v1/campaigns/12', () => HttpResponse.json(squadSheet())),
    );
    renderRoutes(routes, '/campaigns/12');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('inventory-exchange'));
    return { user, dialog: within(await screen.findByTestId('exchange-dialog')) };
  }

  it('сначала — что получить; без выбора остальных списков нет', async () => {
    // вызов
    const { dialog } = await openFromSheet();

    // проверка
    expect(dialog.queryByTestId('exchange-lacking')).not.toBeInTheDocument();
    expect(dialog.getByTestId('exchange-want')).toHaveValue('');
    expect(dialog.queryByTestId('exchange-offer')).not.toBeInTheDocument();
    expect(dialog.queryByTestId('exchange-partner')).not.toBeInTheDocument();
    expect(dialog.getByTestId('exchange-submit')).toBeDisabled();
  });

  it('чешуя: взамен — только материи, с кем — Мира и Торег; огонь: взамен — рог, с кем — только Мира', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/exchange', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ hunters: squadSheet().hunters });
      }),
    );
    const { user, dialog } = await openFromSheet();

    // вызов и проверка: чешуя
    await user.selectOptions(dialog.getByTestId('exchange-want'), 'SCALES');
    expect(options(dialog.getByTestId('exchange-offer'))).toEqual(['Кости (2)']);
    expect(options(dialog.getByTestId('exchange-partner'))).toEqual(['Мира (1)', 'Торег (3)']);

    // вызов и проверка: огонь
    await user.selectOptions(dialog.getByTestId('exchange-want'), 'FIRE');
    expect(options(dialog.getByTestId('exchange-offer'))).toEqual(['Рог (1)']);
    expect(options(dialog.getByTestId('exchange-partner'))).toEqual(['Мира (1)']);
    await user.click(dialog.getByTestId('exchange-submit'));

    // проверка
    await screen.findByText('Боец отдал(а) «Рог» охотнику Мира и получил(а) «Огонь».');
    expect(bodies).toEqual([{ fromHunterId: 31, toHunterId: 32, give: { HORN: 1 }, receive: { FIRE: 1 } }]);
  });

  it('нечего предложить того же типа или ни у кого нет — пояснение, обмен недоступен', async () => {
    // подготовка
    const { user, dialog } = await openFromSheet();

    // вызов и проверка: растение — у Бойца растений нет и ни у кого нет
    await user.selectOptions(dialog.getByTestId('exchange-want'), 'NILLEA');
    expect(dialog.getByTestId('exchange-no-offer')).toHaveTextContent('нужен ресурс того же типа (растения)');
    expect(dialog.getByTestId('exchange-no-partner')).toHaveTextContent('Ни у кого из охотников нет ресурса «Ниллея».');
    expect(dialog.getByTestId('exchange-submit')).toBeDisabled();
  });
});
