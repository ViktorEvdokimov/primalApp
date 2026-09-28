import { isProtectedPath, loginUrl } from '../shared/routing';
import { ApiError } from './errors';

type UnauthorizedHandler = (loginUrl: string) => void;

let onUnauthorized: UnauthorizedHandler = (url) => window.location.assign(url);

/** Приложение подменяет переход на вход навигацией роутера, чтобы не перезагружать страницу. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  onUnauthorized = handler;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((part) => part.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

const CSRF_COOKIE = 'XSRF-TOKEN';

/**
 * Cookie `XSRF-TOKEN` сервер выдаёт на любой запрос, но первый запрос браузера может быть изменяющим
 * (например, запрос кода входа): тогда токен запрашивается отдельно.
 */
async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  try {
    await fetch(new URL('/api/v1/auth/csrf', window.location.origin), { credentials: 'same-origin' });
  } catch {
    // нет сети — запрос ниже вернёт понятную ошибку
  }
  return readCookie(CSRF_COOKIE);
}

/**
 * Запрос к API — через него работает и сгенерированный клиент (orval, `src/api/generated`):
 * - изменяющие запросы получают заголовок `X-XSRF-TOKEN` из cookie `XSRF-TOKEN` (при необходимости её получают);
 * - 401 на защищённой странице ведёт на вход (`/login?next=…`); меню, экспедиция и бой открываются без входа;
 * - ошибка возвращается исключением {@link ApiError} с кодом из problem+json.
 */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json, application/problem+json');
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await csrfToken();
    if (token) {
      headers.set('X-XSRF-TOKEN', token);
    }
  }

  let response: Response;
  try {
    const absoluteUrl = new URL(url, window.location.origin);
    response = await fetch(absoluteUrl, { ...options, method, headers, credentials: 'same-origin' });
  } catch (cause) {
    throw ApiError.network(cause);
  }

  if (response.status === 401 && isProtectedPath(window.location.pathname)) {
    onUnauthorized(loginUrl(window.location.pathname + window.location.search));
  }
  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  return (contentType.includes('json') ? await response.json() : await response.text()) as T;
}
