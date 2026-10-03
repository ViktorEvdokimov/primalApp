import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import type { CampaignSheet } from '../../api/generated/primal.schemas';
import { hunterFixture, sheetFixture } from '../../test/fixtures/campaign';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';
import { defaultPlants } from './lab';

function labSheet(resources: Record<string, number>, overrides: Partial<CampaignSheet> = {}): CampaignSheet {
  return sheetFixture({
    hunters: [hunterFixture({ resources }), hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2 })],
    ...overrides,
  });
}

async function openLab(sheet: CampaignSheet) {
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheet)),
  );
  const view = renderRoutes(routes, '/campaigns/12/lab');
  await screen.findByTestId('lab-level');
  return { ...view, user: userEvent.setup() };
}

const options = (select: HTMLElement) => [...(select as HTMLSelectElement).options].map((option) => option.text);

const potion = (name: string) => {
  const found = screen.getAllByTestId('lab-potion').find((node) => node.dataset.name === name);
  if (found === undefined) throw new Error(`Нет зелья «${name}»`);
  return within(found);
};

describe('Лаборатория: выбор растений', () => {
  const evok = [
    { options: ['TARMARET' as const], any: false },
    { options: ['ANTHEMON' as const, 'MELLIS' as const], any: false },
  ];

  it('по умолчанию — первое растение планшета, которого хватает (пример правил: нет антемона — меллис)', () => {
    // вызов и проверка
    expect(defaultPlants(evok, { TARMARET: 1, ANTHEMON: 1, MELLIS: 1 })).toEqual(['TARMARET', 'ANTHEMON']);
    expect(defaultPlants(evok, { TARMARET: 1, MELLIS: 1 })).toEqual(['TARMARET', 'MELLIS']);
    expect(defaultPlants(evok, { ANTHEMON: 1, MELLIS: 1 })).toBeNull();
  });

  it('«любые 2» — два одинаковых растения, если их хватает', () => {
    // подготовка
    const all = ['NILLEA', 'TARMARET', 'ALBALACEA', 'MELLIS', 'ANTHEMON', 'SELICORNIA'] as const;
    const any = { options: [...all], any: true };

    // вызов и проверка
    expect(defaultPlants([any, any], { SELICORNIA: 2 })).toEqual(['SELICORNIA', 'SELICORNIA']);
    expect(defaultPlants([any, any], { SELICORNIA: 1 })).toBeNull();
  });
});

describe('Лаборатория', () => {
  it('кнопка «Лаборатория» на листе открывает лабораторию', async () => {
    // подготовка
    server.use(
      signedIn(),
      http.get('*/api/v1/campaigns/12', () => HttpResponse.json(labSheet({}))),
    );
    const { router } = renderRoutes(routes, '/campaigns/12');
    const user = userEvent.setup();

    // вызов
    await user.click(await screen.findByTestId('sheet-open-lab'));

    // проверка
    expect(await screen.findByTestId('lab-level')).toHaveTextContent('Лаборатория: 1');
    expect(router.state.location.pathname).toBe('/campaigns/12/lab');
  });

  it('6 зелий с ценой: растение, выбор «A / B», «любое»; без растений — «Не хватает», кнопка недоступна', async () => {
    // вызов
    await openLab(labSheet({ ANTHEMON: 1, NILLEA: 1 }));

    // проверка
    expect(screen.getAllByTestId('lab-potion').map((node) => node.dataset.name)).toEqual([
      'Алемор',
      'Имперум',
      'Ирден',
      'Хатрокс',
      'Эвок',
      'Видья',
    ]);
    expect(potion('Алемор').getAllByTestId('lab-unit')[0]).toHaveTextContent('1любое растение');
    expect(potion('Эвок').getAllByTestId('lab-unit')[1]).toHaveTextContent('1Антемон/Меллис');
    expect(potion('Имперум').getByTestId('lab-potion-brew')).toBeEnabled();
    expect(potion('Видья').getByTestId('lab-potion-missing')).toHaveTextContent('Не хватает растений');
    expect(potion('Видья').getByTestId('lab-potion-brew')).toBeDisabled();
    expect(screen.getAllByTestId('lab-stock-item').map((node) => node.dataset.code)).toEqual(['NILLEA', 'ANTHEMON']);
  });

  it('«Эвок»: выбор растения, проверка запаса, запрос с выбранными растениями', async () => {
    // подготовка
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/campaigns/12/hunters/31/lab', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(
          {
            potion: 'LAB_05',
            name: 'Эвок',
            level: 1,
            hunter: hunterFixture({
              resources: { ANTHEMON: 1 },
              items: [{ id: 502, kind: 'POTION', name: 'Эвок', level: 1, element: null, source: 'LAB_05' }],
            }),
          },
          { status: 201 },
        );
      }),
    );
    const { user } = await openLab(labSheet({ TARMARET: 1, ANTHEMON: 1, MELLIS: 1 }));
    await user.click(potion('Эвок').getByTestId('lab-potion-brew'));
    const dialog = within(await screen.findByTestId('lab-confirm'));
    expect(dialog.getByTestId('lab-choice')).toHaveValue('ANTHEMON');

    // вызов: меллис вместо антемона
    await user.selectOptions(dialog.getByTestId('lab-choice'), 'MELLIS');
    expect(dialog.getByTestId('lab-confirm-cost')).toHaveTextContent('Будет списано: Тармарет 1, Меллис 1.');
    await user.click(dialog.getByTestId('lab-confirm-ok'));

    // проверка
    expect(await screen.findByText('«Эвок» приготовлено — возьмите из коробки карту 1-го уровня.')).toBeInTheDocument();
    expect(bodies).toEqual([{ potion: 'LAB_05', plants: ['TARMARET', 'MELLIS'] }]);
    await waitFor(() =>
      expect(screen.getAllByTestId('lab-stock-item').map((node) => node.dataset.code)).toEqual(['ANTHEMON']),
    );
  });

  it('не хватает растений — «Обменять ресурсы» открывает только обмен с недостающим', async () => {
    // подготовка: на «Эвок» нужен тармарет, у Миры он есть
    const { user } = await openLab(
      labSheet(
        {},
        {
          hunters: [
            hunterFixture({ resources: { ANTHEMON: 1, NILLEA: 1 } }),
            hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2, resources: { TARMARET: 1 } }),
          ],
        },
      ),
    );

    // вызов
    await user.click(await potion('Эвок').findByTestId('lab-potion-exchange'));

    // проверка
    const dialog = within(await screen.findByTestId('exchange-dialog'));
    expect(dialog.getByTestId('exchange-lacking')).toHaveTextContent('Охотнику Боец не хватает: Тармарет 1.');
    expect(dialog.queryByTestId('exchange-mode')).not.toBeInTheDocument();
    expect(dialog.getByTestId('exchange-trade')).toBeInTheDocument();
    expect(dialog.getByTestId('exchange-want')).toHaveValue('TARMARET');
    expect(options(dialog.getByTestId('exchange-offer'))).toEqual(['Ниллея (1)', 'Антемон (1)']);
    expect(options(dialog.getByTestId('exchange-partner'))).toEqual(['Мира (1)']);
  });

  it('«Алемор»: выбор двух любых; на выбранные не хватает — подсказка, кнопка недоступна', async () => {
    // подготовка
    const { user } = await openLab(labSheet({ SELICORNIA: 1, MELLIS: 1 }));
    await user.click(potion('Алемор').getByTestId('lab-potion-brew'));
    const dialog = within(await screen.findByTestId('lab-confirm'));
    const choices = dialog.getAllByTestId('lab-choice');
    expect(choices.map((node) => (node as HTMLSelectElement).value)).toEqual(['MELLIS', 'SELICORNIA']);

    // вызов: обе — селикорния, а она одна
    await user.selectOptions(choices[0]!, 'SELICORNIA');

    // проверка
    expect(dialog.getByTestId('lab-confirm-cost')).toHaveTextContent('На выбранные растения запаса не хватает');
    expect(dialog.getByTestId('lab-confirm-ok')).toBeDisabled();
  });

  it('уровень лаборатории — уровень карты в подтверждении', async () => {
    // подготовка
    const { user } = await openLab(labSheet({ ANTHEMON: 1, NILLEA: 1 }, { labLevel: 3 }));

    // вызов
    await user.click(potion('Имперум').getByTestId('lab-potion-brew'));

    // проверка
    expect(
      within(await screen.findByTestId('lab-confirm')).getByText('«Имперум» 3-го уровня для охотника Боец.'),
    ).toBeInTheDocument();
  });
});
