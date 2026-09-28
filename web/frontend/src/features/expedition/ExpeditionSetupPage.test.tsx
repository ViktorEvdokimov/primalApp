import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import { createLocalBattle, invalidValueMessages } from '../../domain/battle';
import { bossesFixture } from '../../test/fixtures/catalog';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { server } from '../../test/server';

/** Экран подготовки с загруженным каталогом. */
async function openSetup(activeBattle = memoryActiveBattle()) {
  const view = renderRoutes(routes, '/expedition/new', { activeBattle });
  await screen.findByRole('option', { name: 'Коралл - Коровон' });
  return { ...view, user: userEvent.setup() };
}

const bossSelect = () => screen.getByTestId('expedition-boss');
const difficultyOption = (level: number) =>
  within(screen.getByTestId('expedition-difficulty')).getByRole('radio', { name: String(level) });
const modeOption = (name: string) => within(screen.getByTestId('expedition-mode')).getByRole('radio', { name });

describe('Подготовка экспедиции', () => {
  describe('Список боссов', () => {
    it('«Ввести данные вручную» первым, затем боссы в порядке каталога, Пробуждённый последним', async () => {
      // подготовка: сервер отдаёт боссов в обратном порядке
      server.use(http.get('*/api/v1/catalog/bosses', () => HttpResponse.json([...bossesFixture].reverse())));

      // вызов
      await openSetup();

      // проверка
      const labels = within(bossSelect()).getAllByRole('option').map((option) => option.textContent);
      expect(labels).toEqual(['Ввести данные вручную', 'Коралл - Коровон', 'Огонь - Вираксен', 'Пробуждённый']);
    });

    it('каталог недоступен: сообщение об ошибке, ручной ввод работает', async () => {
      // подготовка
      server.use(http.get('*/api/v1/catalog/bosses', () => HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 })));
      const view = renderRoutes(routes, '/expedition/new');
      const user = userEvent.setup();
      expect(await screen.findByTestId('expedition-catalog-error')).toBeInTheDocument();

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      expect(await screen.findByTestId('page-battle')).toBeInTheDocument();
      expect(view.activeBattle.getSnapshot().battle?.boss).toBeNull();
    });
  });

  describe('Поля подготовки', () => {
    it('по умолчанию — ручной ввод: 4 охотника, прочность 4, смена стойки при 7, сложность 0', async () => {
      // вызов
      await openSetup();

      // проверка
      expect(bossSelect()).toHaveValue('');
      expect(screen.getByTestId('expedition-hunters')).toHaveValue('4');
      expect(screen.getByTestId('expedition-toughness')).toHaveValue('4');
      expect(modeOption('По здоровью')).toBeChecked();
      expect(screen.getByTestId('expedition-at-health')).toHaveValue('7');
      expect(difficultyOption(0)).toBeChecked();
      expect(screen.getByTestId('expedition-deck')).toHaveAttribute('alt', 'Колода карт реакций, уровень 0');
    });

    it('выбор босса предзаполняет поля стойкой I, смена сложности — стойкой I этого уровня', async () => {
      // подготовка
      const { user } = await openSetup();

      // вызов
      await user.selectOptions(bossSelect(), 'KOROVON');

      // проверка
      expect(screen.getByTestId('expedition-toughness')).toHaveValue('2');
      expect(screen.getByTestId('expedition-at-health')).toHaveValue('6');

      await user.click(difficultyOption(2));
      expect(screen.getByTestId('expedition-toughness')).toHaveValue('13');
      expect(screen.getByTestId('expedition-deck')).toHaveAttribute('alt', 'Колода карт реакций, уровень 2');
    });

    it('у Пробуждённого единственная сложность — 3', async () => {
      // подготовка
      const { user } = await openSetup();

      // вызов
      await user.selectOptions(bossSelect(), 'AWAKENED');

      // проверка
      expect(difficultyOption(3)).toBeChecked();
      expect(difficultyOption(0)).toBeDisabled();
      expect(difficultyOption(2)).toBeDisabled();
      expect(screen.getByTestId('expedition-toughness')).toHaveValue('30');
      expect(screen.getByTestId('expedition-at-health')).toHaveValue('8');
    });

    it('возврат к ручному вводу восстанавливает значения по умолчанию', async () => {
      // подготовка
      const { user } = await openSetup();
      await user.selectOptions(bossSelect(), 'VIRAXEN');

      // вызов
      await user.selectOptions(bossSelect(), '');

      // проверка
      expect(screen.getByTestId('expedition-toughness')).toHaveValue('4');
      expect(screen.getByTestId('expedition-at-health')).toHaveValue('7');
    });
  });

  describe('Число охотников', () => {
    it.each(['0', 'abc', '-2', '2.5', ''])('«%s» не даёт начать бой', async (text) => {
      // подготовка
      const { user, activeBattle } = await openSetup();
      await user.clear(screen.getByTestId('expedition-hunters'));
      if (text !== '') await user.type(screen.getByTestId('expedition-hunters'), text);

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      expect(screen.getByText(invalidValueMessages.hunterCount)).toBeInTheDocument();
      expect(activeBattle.getSnapshot().battle).toBeNull();
      expect(screen.getByTestId('page-expedition-new')).toBeInTheDocument();
    });

    it('12 охотников — можно (D-12): бой начинается и открывается экран боя', async () => {
      // подготовка
      const { user, activeBattle } = await openSetup();
      await user.clear(screen.getByTestId('expedition-hunters'));
      await user.type(screen.getByTestId('expedition-hunters'), '12');

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      expect(await screen.findByTestId('page-battle')).toBeInTheDocument();
      expect(activeBattle.getSnapshot().battle?.state.hunterCount).toBe(12);
    });
  });

  describe('Начало боя', () => {
    it('бой содержит снимок стоек босса и параметры из полей, включая исправленные вручную', async () => {
      // подготовка
      const { user, activeBattle } = await openSetup();
      await user.selectOptions(bossSelect(), 'VIRAXEN');
      await user.click(difficultyOption(1));
      await user.clear(screen.getByTestId('expedition-toughness'));
      await user.type(screen.getByTestId('expedition-toughness'), '6');
      await user.click(modeOption('Последняя'));

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      await screen.findByTestId('page-battle');
      const battle = activeBattle.getSnapshot().battle;
      expect(battle).toMatchObject({
        mode: 'EXPEDITION',
        campaign: null,
        submission: null,
        boss: { code: 'VIRAXEN', name: 'Вираксен', element: 'FIRE' },
        difficulty: 1,
        history: [],
      });
      expect(battle?.stances).toEqual([
        { stance: 1, toughnessPerHunter: 5, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        { stance: 2, toughnessPerHunter: 7, stanceChange: { mode: 'HEALTH', atHealth: 3 } },
        { stance: 3, toughnessPerHunter: 10, stanceChange: { mode: 'FINAL' } },
      ]);
      expect(battle?.state.monster).toMatchObject({ toughness: 24, stanceChange: { mode: 'FINAL' }, health: 10, rage: 4 });
    });

    it('ручной ввод: бой без босса и без снимка стоек', async () => {
      // подготовка
      const { user, activeBattle } = await openSetup();

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      await screen.findByTestId('page-battle');
      const battle = activeBattle.getSnapshot().battle;
      expect(battle?.boss).toBeNull();
      expect(battle?.stances).toEqual([]);
      expect(battle?.state.monster).toMatchObject({ toughness: 16, stanceChange: { mode: 'HEALTH', atHealth: 7 } });
    });

    it('сайт открыт по HTTP с адреса в сети (нет crypto.randomUUID) — бой всё равно начинается', async () => {
      // подготовка: так ведёт себя браузер на http://192.168.x.x — randomUUID только в защищённом контексте
      Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
      try {
        const { user, activeBattle } = await openSetup();

        // вызов
        await user.click(screen.getByTestId('expedition-start'));

        // проверка
        await screen.findByTestId('page-battle');
        expect(activeBattle.getSnapshot().battle?.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
      } finally {
        Reflect.deleteProperty(crypto, 'randomUUID');
      }
    });

    it('пустая прочность — бой без порога раны, смена по запросу', async () => {
      // подготовка
      const { user, activeBattle } = await openSetup();
      await user.clear(screen.getByTestId('expedition-toughness'));
      await user.click(modeOption('По запросу'));

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      await screen.findByTestId('page-battle');
      expect(activeBattle.getSnapshot().battle?.state.monster).toMatchObject({
        toughness: null,
        stanceChange: { mode: 'ON_DEMAND' },
      });
    });

    it.each([
      ['прочность 0', 'expedition-toughness', '0', invalidValueMessages.toughness],
      ['порог смены стойки 10', 'expedition-at-health', '10', invalidValueMessages.atHealth],
      ['пустой порог смены стойки', 'expedition-at-health', '', invalidValueMessages.atHealth],
    ])('%s не даёт начать бой', async (_, testId, text, message) => {
      // подготовка
      const { user, activeBattle } = await openSetup();
      await user.clear(screen.getByTestId(testId));
      if (text !== '') await user.type(screen.getByTestId(testId), text);

      // вызов
      await user.click(screen.getByTestId('expedition-start'));

      // проверка
      expect(screen.getByText(message)).toBeInTheDocument();
      expect(activeBattle.getSnapshot().battle).toBeNull();
    });

    it('незаконченный бой заменяется только после подтверждения', async () => {
      // подготовка: в браузере идёт бой
      const activeBattle = memoryActiveBattle();
      const previous = createLocalBattle({
        id: 'previous',
        mode: 'EXPEDITION',
        campaign: null,
        boss: null,
        difficulty: 0,
        stances: [],
        params: { hunterCount: 2, toughnessPerHunter: 3, stanceChange: { mode: 'ON_DEMAND' } },
        now: '2026-09-27T18:00:00.000Z',
      });
      activeBattle.start(previous);
      const { user } = await openSetup(activeBattle);

      // вызов: «Отмена» оставляет прежний бой
      await user.click(screen.getByTestId('expedition-start'));
      await user.click(await screen.findByTestId('expedition-replace-cancel'));

      // проверка
      expect(activeBattle.getSnapshot().battle?.id).toBe('previous');
      expect(screen.getByTestId('page-expedition-new')).toBeInTheDocument();

      // вызов: подтверждение заменяет бой
      await user.click(screen.getByTestId('expedition-start'));
      await user.click(await screen.findByTestId('expedition-replace-confirm'));

      // проверка
      expect(await screen.findByTestId('page-battle')).toBeInTheDocument();
      expect(activeBattle.getSnapshot().battle?.id).not.toBe('previous');
    });
  });
});
