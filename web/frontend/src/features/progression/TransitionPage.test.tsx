import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app/routes';
import { sheetFixture } from '../../test/fixtures/campaign';
import { transitionFixture } from '../../test/fixtures/progression';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

function openTransition() {
  const bodies: unknown[] = [];
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture({ chapter: 7 }))),
    http.get('*/api/v1/campaigns/12/chapter-transition', ({ request }) => {
      const answer = new URL(request.url).searchParams.get('decision');
      return HttpResponse.json(transitionFixture(answer === null ? null : answer.split(':')[1]!));
    }),
    http.post('*/api/v1/campaigns/12/chapter-transition', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json(sheetFixture({ chapter: 7, version: 46 }));
    }),
  );
  const view = renderRoutes(routes, '/campaigns/12/transition');
  return { ...view, bodies, user: userEvent.setup() };
}

describe('Переход главы', () => {
  it('сначала решение главы (окно закрывается только ответом), затем награды и «Принять»', async () => {
    // подготовка
    const { user, bodies, router } = openTransition();
    const dialog = within(await screen.findByTestId('decision-dialog'));
    expect(dialog.getByText('Хотите ли вы тренироваться в лагере у Волтьяра?')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.getByTestId('decision-dialog')).toBeInTheDocument();

    // вызов
    await user.click(dialog.getByTestId('decision-option-YES'));

    // проверка
    await waitFor(() => expect(screen.queryByTestId('decision-dialog')).not.toBeInTheDocument());
    expect(await screen.findByTestId('rewards-achievements')).toHaveTextContent('«Голос Волтьяра»');
    expect(screen.getByTestId('rewards-expire-quests')).toHaveTextContent('Истекает время заданий: 7');
    expect(screen.getByTestId('rewards-expiry-note')).toHaveTextContent('Последствия невыполненных заданий');
    expect(screen.getAllByTestId('rewards-expiry').map((line) => line.textContent)).toEqual([
      'Задание 7 «Храм Зарка»: добавить задание 26.',
    ]);
    expect(screen.getByText('Каждый охотник улучшает свой набор.')).toBeInTheDocument();

    // вызов
    await user.click(screen.getByTestId('transition-accept'));

    // проверка
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(bodies).toEqual([{ action: 'ACCEPT', decisions: { TRAIN_WITH_VOLTYAR: 'YES' }, expectedVersion: 44 }]);
  });

  it('«Отклонить» — окно с последствиями, затем REJECT', async () => {
    // подготовка
    const { user, bodies, router } = openTransition();
    await user.click(within(await screen.findByTestId('decision-dialog')).getByTestId('decision-option-NO'));
    await waitFor(() => expect(screen.queryByTestId('decision-dialog')).not.toBeInTheDocument());

    // вызов
    await user.click(screen.getByTestId('transition-reject'));

    // проверка
    const dialog = within(await screen.findByTestId('consequences-dialog'));
    expect(dialog.getAllByTestId('consequence')[0]).toHaveTextContent('Глава останется 6.');
    await user.click(dialog.getByTestId('consequences-confirm'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/campaigns/12'));
    expect(bodies).toEqual([{ action: 'REJECT', decisions: null, expectedVersion: 44 }]);
  });
});
