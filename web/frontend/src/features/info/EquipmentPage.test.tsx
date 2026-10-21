import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import { infoFixture } from '../../test/fixtures/info';
import { renderRoutes } from '../../test/render';
import { server } from '../../test/server';

const option = (testId: string, attribute: string, value: string) => {
  const found = screen.getAllByTestId(testId).find((node) => node.getAttribute(attribute) === value);
  if (found === undefined) throw new Error(`Нет ${testId} ${value}`);
  return found;
};
const names = () => screen.queryAllByTestId('equipment-item').map((node) => node.dataset.name);
const caption = (name: string) =>
  within(screen.getAllByTestId('equipment-item').find((node) => node.dataset.name === name)!).getByTestId(
    'equipment-caption',
  );
const images = () => screen.getAllByTestId('equipment-image').map((node) => node.getAttribute('src'));

async function openEquipment(path = '/info/equipment') {
  const view = renderRoutes(routes, path);
  await screen.findAllByTestId('equipment-item');
  return { ...view, user: userEvent.setup() };
}

describe('Инфо → Экипировка', () => {
  beforeEach(() => {
    server.use(http.get('*/api/v1/info', () => HttpResponse.json(infoFixture())));
  });

  it('раздел в списке «Инфо» — число предметов с картами; открывает «Экипировку»', async () => {
    // подготовка
    const { router } = renderRoutes(routes, '/info');
    const user = userEvent.setup();
    const card = (await screen.findAllByTestId('info-section')).find((node) => node.dataset.section === 'EQUIPMENT')!;
    expect(card).toHaveTextContent('Экипировка124');

    // вызов
    await user.click(card);

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/info/equipment'));
    expect(await screen.findAllByTestId('equipment-item')).not.toHaveLength(0);
  });

  it('«Снаряжение»: базовое, затем кузни по стихиям — как на планшете; подпись — кузня и вид', async () => {
    // вызов
    await openEquipment();

    // проверка
    expect(screen.getAllByTestId('equipment-tab').map((node) => node.textContent)).toEqual(['Снаряжение', 'Зелья']);
    expect(names()).toEqual([
      'Большой меч',
      'Большой лук',
      'Молот',
      'Щит и меч',
      'Парные клинки',
      'Пушка',
      'Копьё',
      'Барабан',
      'Основной шлем',
      'Основной доспех',
      'Язык пламени',
      'Лук-испепелитель',
      'Молот пламени',
      'Вулканический щит',
      'Крылья дракона',
      'Игнис',
      'Магматическое копьё',
      'Лавовый барабан',
      'Чешуйчатый шлем',
      'Чешуйчатый доспех',
      'Перчатка Волтьяра',
      'Лавовый щит',
      'Кровавый риф',
      'Рифовый шлем',
    ]);
    expect(caption('Основной шлем')).toHaveTextContent('Базовое · Шлем');
    expect(caption('Язык пламени')).toHaveTextContent('Огонь · Оружие · большой меч');
    expect(images()[0]).toBe('/cards/GREATSWORD-1.webp');
  });

  it('фильтр по классу отбирает только оружие; тип и уровень — в адресе; базового на 2-м уровне нет', async () => {
    // подготовка
    const { user, router } = await openEquipment();

    // вызов
    await user.click(option('equipment-class-option', 'data-class', 'KARA'));

    // проверка: оружие — только Кары, шлемы, доспехи и предметы — все
    expect(names()).toEqual([
      'Парные клинки',
      'Основной шлем',
      'Основной доспех',
      'Крылья дракона',
      'Чешуйчатый шлем',
      'Чешуйчатый доспех',
      'Перчатка Волтьяра',
      'Лавовый щит',
      'Рифовый шлем',
    ]);

    // вызов
    await user.click(option('equipment-type-option', 'data-type', 'WEAPON'));
    await user.click(option('equipment-level-option', 'data-level', '2'));

    // проверка
    expect(names()).toEqual(['Крылья дракона']);
    expect(images()).toEqual(['/cards/FIRE_05-2.webp']);
    expect(screen.getByTestId('equipment-basic-level')).toHaveTextContent('Базовое снаряжение есть только 1-го уровня.');
    expect(router.state.location.search).toBe('?class=KARA&type=WEAPON&level=2');
  });

  it('«Зелья»: 6 зелий, уровень из адреса, без фильтров типа и класса; карта открывается крупно', async () => {
    // подготовка
    const { user } = await openEquipment('/info/equipment?tab=POTIONS&type=HELMET&class=KARA&level=3');

    // проверка
    expect(names()).toEqual(['Алемор', 'Имперум', 'Ирден', 'Хатрокс', 'Эвок', 'Видья']);
    expect(images()[2]).toBe('/cards/LAB_03-3.webp');
    expect(screen.queryByTestId('equipment-type')).not.toBeInTheDocument();
    expect(screen.queryByTestId('equipment-class')).not.toBeInTheDocument();

    // вызов
    await user.click(screen.getByRole('button', { name: 'Ирден · 3-й уровень' }));

    // проверка
    const dialog = await screen.findByRole('dialog', { name: 'Ирден · 3-й уровень' });
    expect(within(dialog).getByRole('img')).toHaveAttribute('src', '/cards/LAB_03-3.webp');
  });
});
