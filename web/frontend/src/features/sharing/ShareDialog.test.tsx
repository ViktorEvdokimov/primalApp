import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import type { ShareLinkView } from '../../api/generated/primal.schemas';
import { sheetFixture } from '../../test/fixtures/campaign';
import { renderRoutes } from '../../test/render';
import { server, signedIn } from '../../test/server';

const link = (suffix: string, joined: ShareLinkView['joined'] = []): ShareLinkView => ({
  url: `http://localhost:8088/s/${suffix}`,
  createdAt: '2026-09-27T18:20:00Z',
  joined,
});

/** Лист владельца; ссылка кампании на «сервере» — в `state.link`. */
function openSheet(initial: ShareLinkView | null) {
  const state = { link: initial, requests: [] as string[] };
  server.use(
    signedIn(),
    http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture())),
    http.get('*/api/v1/campaigns/12/share-link', () =>
      state.link === null
        ? HttpResponse.json({ status: 404, code: 'NOT_FOUND' }, { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
        : HttpResponse.json(state.link),
    ),
    http.post('*/api/v1/campaigns/12/share-link', () => {
      state.requests.push('POST');
      state.link = link(`token-${state.requests.length}`);
      return HttpResponse.json(state.link, { status: 201 });
    }),
    http.delete('*/api/v1/campaigns/12/share-link', () => {
      state.requests.push('DELETE');
      state.link = null;
      return new HttpResponse(null, { status: 204 });
    }),
  );
  renderRoutes(routes, '/campaigns/12');
  return { state, user: userEvent.setup() };
}

describe('Окно «Поделиться»', () => {
  it('ссылки нет — «Создать ссылку»; после создания видны адрес и список присоединившихся', async () => {
    // подготовка
    const { user, state } = openSheet(null);
    await user.click(await screen.findByTestId('sheet-share'));
    const dialog = within(await screen.findByTestId('share-dialog'));

    // вызов
    await user.click(await dialog.findByTestId('share-create'));

    // проверка
    await waitFor(() => expect(dialog.getByTestId('share-url')).toHaveValue('http://localhost:8088/s/token-1'));
    expect(dialog.getByTestId('share-nobody')).toBeInTheDocument();
    expect(state.requests).toEqual(['POST']);
  });

  it('присоединившиеся; «Перевыпустить» и «Отозвать» — только после подтверждения', async () => {
    // подготовка
    const { user, state } = openSheet(link('old', [{ kind: 'GUEST', name: 'Вадим', joinedAt: '2026-09-27T18:30:00Z' }]));
    await user.click(await screen.findByTestId('sheet-share'));
    const dialog = within(await screen.findByTestId('share-dialog'));
    expect(await dialog.findByTestId('share-participant')).toHaveTextContent('Вадим (гость)');

    // вызов: перевыпуск с отменой и подтверждением
    await user.click(dialog.getByTestId('share-reissue'));
    await user.click(dialog.getByTestId('share-confirm-cancel'));
    expect(state.requests).toEqual([]);
    await user.click(dialog.getByTestId('share-reissue'));
    await user.click(dialog.getByTestId('share-confirm-ok'));

    // проверка
    await waitFor(() => expect(dialog.getByTestId('share-url')).toHaveValue('http://localhost:8088/s/token-1'));

    // вызов: отзыв
    await user.click(dialog.getByTestId('share-revoke'));
    await user.click(dialog.getByTestId('share-confirm-ok'));

    // проверка
    expect(await dialog.findByTestId('share-no-link')).toBeInTheDocument();
    expect(state.requests).toEqual(['POST', 'DELETE']);
  });

  describe('«Копировать»', () => {
    afterEach(() => {
      Reflect.deleteProperty(document, 'execCommand');
    });

    it('ссылка в буфере обмена и сообщение «Ссылка скопирована»', async () => {
      // подготовка
      const { user } = openSheet(link('abc'));
      await user.click(await screen.findByTestId('sheet-share'));
      const dialog = within(await screen.findByTestId('share-dialog'));
      await dialog.findByTestId('share-url');

      // вызов
      await user.click(dialog.getByTestId('share-copy'));

      // проверка
      expect(await screen.findByText('Ссылка скопирована.')).toBeInTheDocument();
      expect(await navigator.clipboard.readText()).toBe('http://localhost:8088/s/abc');
    });

    it('скопировать не удалось — просьба скопировать вручную, а не ложное «скопирована»', async () => {
      // подготовка: буфер отказал, старый способ тоже
      const { user } = openSheet(link('abc'));
      await user.click(await screen.findByTestId('sheet-share'));
      const dialog = within(await screen.findByTestId('share-dialog'));
      await dialog.findByTestId('share-url');
      vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('NotAllowedError'));
      Object.defineProperty(document, 'execCommand', { value: () => false, configurable: true });

      // вызов
      await user.click(dialog.getByTestId('share-copy'));

      // проверка
      expect(await screen.findByText('Не удалось скопировать — выделите ссылку и скопируйте вручную.')).toBeInTheDocument();
      expect(screen.queryByText('Ссылка скопирована.')).not.toBeInTheDocument();
    });
  });

  it('участнику по ссылке «Поделиться» не показывается', async () => {
    // подготовка
    server.use(signedIn(), http.get('*/api/v1/campaigns/12', () => HttpResponse.json(sheetFixture({ access: 'LINK' }))));

    // вызов
    renderRoutes(routes, '/campaigns/12');

    // проверка
    await screen.findByTestId('sheet-name');
    expect(screen.queryByTestId('sheet-share')).not.toBeInTheDocument();
  });
});
