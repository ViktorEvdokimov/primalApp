import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { MeResponse } from '../api/generated/primal.schemas';
import { userMe } from './fixtures/auth';
import { bossesFixture, dictionariesFixture } from './fixtures/catalog';

/** Каталог нужен многим экранам, поэтому отвечает по умолчанию; тест может подменить ответ через `server.use(...)`. */
export const catalogHandlers = [
  http.get('*/api/v1/catalog/bosses', () => HttpResponse.json(bossesFixture)),
  http.get('*/api/v1/catalog/dictionaries', () => HttpResponse.json(dictionariesFixture)),
];

/** Как сервер: `GET /auth/csrf` выдаёт cookie `XSRF-TOKEN` (в jsdom — через `document.cookie`). */
export const csrfHandler = http.get('*/api/v1/auth/csrf', () => {
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
  return new HttpResponse(null, { status: 204 });
});

/** По умолчанию входа нет: `GET /auth/me` → 401. Тест со входом подменяет ответ (`signedIn`). */
export const anonymousHandler = http.get('*/api/v1/auth/me', () =>
  HttpResponse.json(
    { status: 401, code: 'UNAUTHENTICATED', detail: 'Войдите, чтобы продолжить.' },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  ),
);

/** Ответ `GET /auth/me` для вошедшего пользователя. */
export function signedIn(me: MeResponse = userMe) {
  return http.get('*/api/v1/auth/me', () => HttpResponse.json(me));
}

/** Подмена API в тестах: обработчики добавляются в каждом тесте через `server.use(...)`. */
/** Кампаний по умолчанию нет. */
export const campaignsHandler = http.get('*/api/v1/campaigns', () => HttpResponse.json([]));

export const server = setupServer(...catalogHandlers, csrfHandler, anonymousHandler, campaignsHandler);
