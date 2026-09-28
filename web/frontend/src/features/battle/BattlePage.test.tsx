import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import {
  createBattleStore,
  createLocalBattle,
  invalidValueMessages,
  type LocalBattle,
  type NewBattleParams,
  type StanceDef,
} from '../../domain/battle';
import { memoryActiveBattle, renderRoutes } from '../../test/render';
import { createActiveBattle, type ActiveBattle } from './useActiveBattle';

const VIRAXEN_STANCES: StanceDef[] = [
  { stance: 1, toughnessPerHunter: 4, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
  { stance: 2, toughnessPerHunter: 6, stanceChange: { mode: 'HEALTH', atHealth: 4 } },
];

function localBattle(params: Partial<NewBattleParams> = {}, stances: StanceDef[] = []): LocalBattle {
  return createLocalBattle({
    id: crypto.randomUUID(),
    mode: 'EXPEDITION',
    campaign: null,
    boss: stances.length > 0 ? { code: 'VIRAXEN', name: 'Вираксен', element: 'FIRE' } : null,
    difficulty: 0,
    stances,
    params: { hunterCount: 4, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 }, ...params },
    now: '2026-09-27T18:00:00.000Z',
  });
}

async function open(path: string, battle: LocalBattle | null, activeBattle: ActiveBattle = memoryActiveBattle()) {
  if (battle !== null) activeBattle.start(battle);
  const view = renderRoutes(routes, path, { activeBattle });
  await screen.findByTestId(path === '/' ? 'main-menu' : 'page-battle');
  return { ...view, user: userEvent.setup() };
}

const openBattle = (battle: LocalBattle | null, activeBattle?: ActiveBattle) => open('/battle', battle, activeBattle);
const text = (testId: string) => screen.getByTestId(testId).textContent;

async function enterDamage(user: ReturnType<typeof userEvent.setup>, value: string) {
  await user.clear(screen.getByTestId('battle-damage-input'));
  await user.type(screen.getByTestId('battle-damage-input'), `${value}{Enter}`);
}

describe('Экран боя', () => {
  const vibrate = vi.fn<(pattern: VibratePattern) => boolean>(() => true);

  beforeEach(() => {
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'vibrate');
  });

  describe('Начало', () => {
    it('без боя — предложение начать экспедицию', async () => {
      // вызов
      await openBattle(null);

      // проверка
      expect(screen.getByTestId('battle-empty')).toBeInTheDocument();
      expect(screen.getByTestId('battle-to-expedition')).toHaveAttribute('href', '/expedition/new');
    });

    it('информационная панель: фаза, раунд, здоровье, ярость, урон, прочность, статус, смена стойки', async () => {
      // вызов
      await openBattle(localBattle());

      // проверка
      expect(text('battle-phase')).toBe('Фаза I');
      expect(text('battle-round')).toBe('Раунд 1/10');
      expect(text('battle-health')).toBe('Здоровье: 10');
      expect(text('battle-rage')).toBe('Ярость: 4');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 0');
      expect(text('battle-toughness')).toBe('Прочность: 8');
      expect(text('battle-status')).toBe('Статус: Обычный');
      expect(text('battle-stance-change')).toBe('Смена стойки: при 7 HP');
      expect(screen.queryByTestId('battle-change-stance')).not.toBeInTheDocument();
      expect(screen.queryByTestId('battle-undo')).not.toBeInTheDocument();
      expect(screen.getByTestId('battle-negative-hint')).toHaveTextContent('Отрицательное значение отменяет накопленный урон');
    });
  });

  describe('Урон', () => {
    it('урон из поля: рана, сообщение, подсветка изменённых параметров и двойная вибрация', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());

      // вызов
      await enterDamage(user, '10');

      // проверка
      expect(text('battle-health')).toBe('Здоровье: 9');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 2');
      expect(text('battle-message')).toBe('Нанесено ран: 1.');
      expect(screen.getByTestId('battle-health')).toHaveAttribute('data-highlighted', 'true');
      expect(screen.getByTestId('battle-rage')).not.toHaveAttribute('data-highlighted');
      expect(vibrate).toHaveBeenLastCalledWith([30, 40, 30]);
    });

    it('урон без раны — короткая вибрация', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());

      // вызов
      await enterDamage(user, '3');

      // проверка
      expect(text('battle-message')).toBe('Урон накоплен, но рана не нанесена.');
      expect(vibrate).toHaveBeenLastCalledWith(30);
    });

    it('кнопки +N копят урон, «Применить урон сейчас» применяет его сразу', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ toughnessPerHunter: 20 }));

      // вызов
      await user.click(screen.getByTestId('battle-quick-5'));
      await user.click(screen.getByTestId('battle-quick-1'));

      // проверка
      expect(text('battle-pending')).toBe('Ожидание... 6 урона');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 0');
      await user.click(screen.getByTestId('battle-apply-now'));
      expect(text('battle-accumulated')).toBe('Накопленный урон: 6');
      expect(screen.queryByTestId('battle-pending')).not.toBeInTheDocument();
    });

    it('отрицательный урон уменьшает только накопленный урон (D-3)', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await enterDamage(user, '5');

      // вызов
      await enterDamage(user, '-3');

      // проверка
      expect(text('battle-accumulated')).toBe('Накопленный урон: 2');
      expect(text('battle-message')).toBe('Накопленный урон уменьшен на 3.');
    });

    it('«Заживить рану» при полном здоровье — сообщение, бой не меняется', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());

      // вызов
      await user.click(screen.getByTestId('battle-heal'));

      // проверка
      expect(text('battle-message')).toBe('Здоровье уже максимальное');
      expect(screen.queryByTestId('battle-undo')).not.toBeInTheDocument();
    });

    it('«Отменить действие» показывает, что отменится, и восстанавливает бой', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await enterDamage(user, '10');
      expect(text('battle-undo-description')).toBe('Отменится: урон +10');

      // вызов
      await user.click(screen.getByTestId('battle-undo'));

      // проверка
      expect(text('battle-health')).toBe('Здоровье: 10');
      expect(text('battle-message')).toBe('Отменено: урон +10');
    });
  });

  describe('Раунды и ярость', () => {
    it('«Закончить раунд» наносит введённый урон и начинает следующий раунд', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await user.type(screen.getByTestId('battle-damage-input'), '3');

      // вызов
      await user.click(screen.getByTestId('battle-end-round'));

      // проверка
      expect(text('battle-round')).toBe('Раунд 2/10');
      expect(text('battle-rage')).toBe('Ярость: 8');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 3');
      expect(text('battle-message')).toBe('Урон накоплен, но рана не нанесена. Раунд 2. Ярость: 8');
    });

    it('выплеск ярости: окно, после OK ярость — 1 за охотника (R-8)', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await user.click(screen.getByTestId('battle-rage-per-hunter'));

      // вызов: 4 + 4 + 4 = 12 — 3 за охотника
      await user.click(screen.getByTestId('battle-rage-per-hunter'));

      // проверка
      const dialog = await screen.findByTestId('rage-surge-dialog');
      expect(dialog).toHaveTextContent('Каждый охотник получает урон, равный силе монстра');
      await user.click(screen.getByTestId('rage-surge-ok'));
      await waitFor(() => expect(screen.queryByTestId('rage-surge-dialog')).not.toBeInTheDocument());
      expect(text('battle-rage')).toBe('Ярость: 4');
      expect(text('battle-message')).toMatch(/^Выплеск ярости!/);
    });

    it('кнопки ярости: −1, +1, +1/охот-1', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ hunterCount: 3 }));

      // вызов и проверка
      await user.click(screen.getByTestId('battle-rage-minus-1'));
      expect(text('battle-rage')).toBe('Ярость: 2');
      await user.click(screen.getByTestId('battle-rage-plus-1'));
      expect(text('battle-rage')).toBe('Ярость: 3');
      await user.click(screen.getByTestId('battle-rage-per-hunter-minus-1'));
      expect(text('battle-rage')).toBe('Ярость: 5');
    });
  });

  describe('Смена стойки', () => {
    it('окно предзаполнено стойкой из снимка каталога, OK применяет её и наносит перенесённый урон', async () => {
      // подготовка: 1 охотник, прочность 4, смена при 7
      const { user } = await openBattle(localBattle({ hunterCount: 1, toughnessPerHunter: 4 }, VIRAXEN_STANCES));

      // вызов: 20 урона — 3 раны до порога, 8 урона переносится
      await enterDamage(user, '20');

      // проверка
      const dialog = await screen.findByTestId('stance-dialog');
      expect(dialog).toHaveTextContent('Монстр перешёл на стойку 2.');
      expect(text('stance-dialog-source')).toBe('Параметры заполнены из базы боссов — при необходимости исправьте:');
      expect(text('stance-dialog-carried')).toBe('Перенесённый урон 8 будет нанесён с новой прочностью после «OK».');
      expect(screen.getByTestId('stance-toughness')).toHaveValue('6');
      expect(screen.getByTestId('stance-at-health')).toHaveValue('4');

      await user.click(screen.getByTestId('stance-dialog-ok'));
      await waitFor(() => expect(screen.queryByTestId('stance-dialog')).not.toBeInTheDocument());
      expect(text('battle-phase')).toBe('Фаза II');
      expect(text('battle-toughness')).toBe('Прочность: 6');
      expect(text('battle-health')).toBe('Здоровье: 6');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 2');
    });

    it('«Отмена» в окне откатывает урон и стойку', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ hunterCount: 1, toughnessPerHunter: 4 }, VIRAXEN_STANCES));
      await enterDamage(user, '20');
      await screen.findByTestId('stance-dialog');

      // вызов
      await user.click(screen.getByTestId('stance-dialog-cancel'));

      // проверка
      await waitFor(() => expect(screen.queryByTestId('stance-dialog')).not.toBeInTheDocument());
      expect(text('battle-phase')).toBe('Фаза I');
      expect(text('battle-health')).toBe('Здоровье: 10');
      expect(text('battle-accumulated')).toBe('Накопленный урон: 0');
    });

    it('стойки нет в каталоге — поля пустые, неверные значения не принимаются', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ hunterCount: 1, toughnessPerHunter: 4 }));
      await enterDamage(user, '20');
      await screen.findByTestId('stance-dialog');
      expect(text('stance-dialog-source')).toBe('Данных о стойке в базе боссов нет — укажите параметры:');
      expect(screen.getByTestId('stance-toughness')).toHaveValue('');
      expect(within(screen.getByTestId('stance-mode')).getByRole('radio', { name: 'По запросу' })).toBeChecked();

      // вызов: смена по здоровью без порога
      await user.click(within(screen.getByTestId('stance-mode')).getByRole('radio', { name: 'По здоровью' }));
      await user.click(screen.getByTestId('stance-dialog-ok'));

      // проверка
      expect(screen.getByText(invalidValueMessages.atHealth)).toBeInTheDocument();
      await user.type(screen.getByTestId('stance-toughness'), '6');
      await user.click(within(screen.getByTestId('stance-mode')).getByRole('radio', { name: 'Последняя' }));
      await user.click(screen.getByTestId('stance-dialog-ok'));
      await waitFor(() => expect(screen.queryByTestId('stance-dialog')).not.toBeInTheDocument());
      expect(text('battle-stance-change')).toBe('Смена стойки: последняя стойка');
      expect(text('battle-health')).toBe('Здоровье: 6');
    });

    it('при «Устойчивости стойки» окно предупреждает, что перенесённый урон сбросится', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ hunterCount: 1, toughnessPerHunter: 4 }));
      await user.click(screen.getByTestId('battle-resilient'));

      // вызов
      await enterDamage(user, '20');

      // проверка
      await screen.findByTestId('stance-dialog');
      expect(text('stance-dialog-carried')).toBe('Перенесённый урон 8 сбросится («Устойчивость стойки»).');
    });

    it('«Сменить стойку» — только при смене по запросу', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ stanceChange: { mode: 'ON_DEMAND' } }));
      expect(text('battle-stance-change')).toBe('Смена стойки: по запросу');

      // вызов
      await user.click(screen.getByTestId('battle-change-stance'));

      // проверка
      expect(await screen.findByTestId('stance-dialog')).toHaveTextContent('Монстр перешёл на стойку 2.');
    });
  });

  describe('Статусы монстра', () => {
    it('переключатели «Затвердевший» и «Устойчивость стойки» независимы', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());

      // вызов и проверка
      await user.click(screen.getByTestId('battle-hardened'));
      expect(text('battle-status')).toBe('Статус: Затвердевший');
      await user.click(screen.getByTestId('battle-resilient'));
      expect(text('battle-status')).toBe('Статус: Затвердевший, Устойчивость стойки');
      await user.click(screen.getByTestId('battle-hardened'));
      expect(text('battle-status')).toBe('Статус: Устойчивость стойки');
    });

    it.each([
      ['hardened', 'сбрасывается'],
      ['resilient', 'не переносится на новую карту стойки'],
    ])('кнопка «i» открывает описание статуса %s', async (status, fragment) => {
      // подготовка
      const { user } = await openBattle(localBattle());

      // вызов
      await user.click(screen.getByTestId(`battle-${status}-info`));

      // проверка
      expect(await screen.findByTestId('battle-status-info')).toHaveTextContent(fragment);
      await user.click(screen.getByTestId('battle-status-info-close'));
      await waitFor(() => expect(screen.queryByTestId('battle-status-info')).not.toBeInTheDocument());
    });
  });

  describe('Итог боя', () => {
    it('«Сдаться» после подтверждения — поражение «Вы сдались.»; итог можно отменить', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await user.click(screen.getByTestId('battle-surrender'));

      // вызов
      await user.click(await screen.findByTestId('battle-surrender-confirm'));

      // проверка
      expect(await screen.findByTestId('battle-result')).toHaveAttribute('data-result', 'DEFEAT');
      expect(text('battle-result-title')).toBe('ПОРАЖЕНИЕ');
      expect(text('battle-result-reason')).toBe('Вы сдались.');
      await user.click(screen.getByTestId('battle-result-undo'));
      expect(await screen.findByTestId('battle-health')).toHaveTextContent('Здоровье: 10');
    });

    it('после 10-го раунда — поражение «Закончились раунды...»', async () => {
      // подготовка
      const battle = localBattle({ hunterCount: 1 });
      const { user } = await openBattle({ ...battle, state: { ...battle.state, round: 10 } });

      // вызов
      await user.click(screen.getByTestId('battle-end-round'));

      // проверка
      expect(await screen.findByTestId('battle-result')).toHaveAttribute('data-result', 'DEFEAT');
      expect(text('battle-result-reason')).toBe('Закончились раунды...');
    });

    it('победа — «ПОБЕДА!»; «Новый бой» удаляет бой и открывает подготовку', async () => {
      // подготовка
      const activeBattle = memoryActiveBattle();
      const { user } = await openBattle(localBattle({ toughnessPerHunter: 1, stanceChange: { mode: 'ON_DEMAND' } }), activeBattle);
      await enterDamage(user, '40');
      expect(await screen.findByTestId('battle-result')).toHaveAttribute('data-result', 'VICTORY');
      expect(text('battle-result-title')).toBe('ПОБЕДА!');
      expect(text('battle-result-reason')).toBe('Монстр повержен!');

      // вызов
      await user.click(screen.getByTestId('battle-new'));

      // проверка
      expect(await screen.findByTestId('page-expedition-new')).toBeInTheDocument();
      expect(activeBattle.getSnapshot().battle).toBeNull();
    });

    it('«Выход в меню» с экрана итога удаляет бой', async () => {
      // подготовка
      const { user } = await openBattle(localBattle({ toughnessPerHunter: 1, stanceChange: { mode: 'ON_DEMAND' } }));
      await enterDamage(user, '40');
      await screen.findByTestId('battle-result');

      // вызов
      await user.click(screen.getByTestId('battle-result-menu'));

      // проверка
      expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
      expect(screen.queryByTestId('menu-resume-battle')).not.toBeInTheDocument();
    });
  });

  describe('Главное меню и хранение', () => {
    it('«Выход в меню» во время боя сохраняет бой: в меню «Вернуться к бою»', async () => {
      // подготовка
      const { user } = await openBattle(localBattle());
      await enterDamage(user, '5');

      // вызов
      await user.click(screen.getByTestId('battle-menu'));
      await user.click(await screen.findByTestId('menu-resume-battle'));

      // проверка
      expect(await screen.findByTestId('battle-accumulated')).toHaveTextContent('Накопленный урон: 5');
    });

    it('«Вернуться к бою» — только при незаконченном бое', async () => {
      // вызов: боя нет
      const empty = await open('/', null);

      // проверка
      expect(screen.queryByTestId('menu-resume-battle')).not.toBeInTheDocument();
      empty.unmount();

      // вызов: бой окончен
      const finished = memoryActiveBattle();
      finished.start(localBattle());
      finished.dispatch({ type: 'SURRENDER' });
      await open('/', null, finished);

      // проверка
      expect(screen.queryByTestId('menu-resume-battle')).not.toBeInTheDocument();
    });

    it('без localStorage бой идёт, экран предупреждает, что бой не сохранится', async () => {
      // подготовка
      const activeBattle = createActiveBattle(
        createBattleStore(() => {
          throw new DOMException('Доступ запрещён', 'SecurityError');
        }),
      );

      // вызов
      await openBattle(localBattle(), activeBattle);

      // проверка
      expect(screen.getByTestId('battle-not-persistent')).toBeInTheDocument();
    });
  });
});
