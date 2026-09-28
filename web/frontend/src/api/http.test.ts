import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/server';
import { ApiError } from './errors';
import { apiFetch, setUnauthorizedHandler } from './http';

describe('apiFetch — запросы к API', () => {
  const onUnauthorized = vi.fn();

  beforeEach(() => {
    setUnauthorizedHandler(onUnauthorized);
    document.cookie = 'XSRF-TOKEN=csrf-token-42';
  });

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    window.history.pushState({}, '', '/');
  });

  describe('CSRF', () => {
    it('изменяющий запрос получает заголовок X-XSRF-TOKEN из cookie', async () => {
      // подготовка
      let csrfHeader: string | null = null;
      server.use(
        http.post('*/api/v1/things', ({ request }) => {
          csrfHeader = request.headers.get('X-XSRF-TOKEN');
          return HttpResponse.json({ id: 1 }, { status: 201 });
        }),
      );

      // вызов
      const result = await apiFetch<{ id: number }>('/api/v1/things', { method: 'POST' });

      // проверка
      expect(result).toEqual({ id: 1 });
      expect(csrfHeader).toBe('csrf-token-42');
    });

    it('нет cookie XSRF-TOKEN — сначала запрашивается токен, затем запрос уходит с ним', async () => {
      // подготовка
      document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
      let csrfHeader: string | null = null;
      server.use(
        http.get('*/api/v1/auth/csrf', () => {
          document.cookie = 'XSRF-TOKEN=fresh-token';
          return new HttpResponse(null, { status: 204 });
        }),
        http.post('*/api/v1/auth/code', ({ request }) => {
          csrfHeader = request.headers.get('X-XSRF-TOKEN');
          return HttpResponse.json({ challengeId: 'c1' }, { status: 202 });
        }),
      );

      // вызов
      await apiFetch('/api/v1/auth/code', { method: 'POST' });

      // проверка
      expect(csrfHeader).toBe('fresh-token');
    });

    it('чтение не отправляет CSRF-заголовок', async () => {
      // подготовка
      let csrfHeader: string | null = 'не проверено';
      server.use(
        http.get('*/api/v1/things', ({ request }) => {
          csrfHeader = request.headers.get('X-XSRF-TOKEN');
          return HttpResponse.json([]);
        }),
      );

      // вызов
      await apiFetch('/api/v1/things');

      // проверка
      expect(csrfHeader).toBeNull();
    });
  });

  describe('401 — нужен вход', () => {
    it('на защищённой странице ведёт на вход с возвратом обратно', async () => {
      // подготовка
      window.history.pushState({}, '', '/campaigns/12?tab=quests');
      server.use(http.get('*/api/v1/campaigns/12', () => problem(401, 'UNAUTHENTICATED')));

      // вызов
      const request = apiFetch('/api/v1/campaigns/12');

      // проверка
      await expect(request).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' });
      expect(onUnauthorized).toHaveBeenCalledWith('/login?next=%2Fcampaigns%2F12%3Ftab%3Dquests');
    });

    it('на публичной странице (экспедиция) вход не требуется', async () => {
      // подготовка
      window.history.pushState({}, '', '/expedition/new');
      server.use(http.get('*/api/v1/auth/me', () => problem(401, 'UNAUTHENTICATED')));

      // вызов
      const request = apiFetch('/api/v1/auth/me');

      // проверка
      await expect(request).rejects.toBeInstanceOf(ApiError);
      expect(onUnauthorized).not.toHaveBeenCalled();
    });
  });

  describe('Ошибки', () => {
    it('problem+json превращается в ApiError с кодом и дополнительными полями', async () => {
      // подготовка
      server.use(
        http.post('*/api/v1/auth/code/verify', () =>
          HttpResponse.json(
            { status: 400, code: 'INVALID_CODE', title: 'Неверный код', detail: 'Код не подходит', attemptsLeft: 3 },
            { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
          ),
        ),
      );

      // вызов
      const error = await apiFetch('/api/v1/auth/code/verify', { method: 'POST' }).catch((e: unknown) => e);

      // проверка
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status: 400, code: 'INVALID_CODE', detail: 'Код не подходит' });
      expect((error as ApiError).problem?.attemptsLeft).toBe(3);
    });

    it('обрыв сети → ApiError с кодом NETWORK_ERROR', async () => {
      // подготовка
      server.use(http.get('*/api/v1/catalog/bosses', () => HttpResponse.error()));

      // вызов
      const error = await apiFetch('/api/v1/catalog/bosses').catch((e: unknown) => e);

      // проверка
      expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
    });

    it('ответ без тела (204) возвращает undefined', async () => {
      // подготовка
      server.use(http.delete('*/api/v1/things/1', () => new HttpResponse(null, { status: 204 })));

      // вызов
      const result = await apiFetch('/api/v1/things/1', { method: 'DELETE' });

      // проверка
      expect(result).toBeUndefined();
    });
  });
});

function problem(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } });
}
