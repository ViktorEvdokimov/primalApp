/**
 * Маршруты, которым нужен вход (doc/architecture.md §5.1). Главное меню, экспедиция, бой, вход и
 * страница приглашения открываются без входа.
 */
export const PROTECTED_PREFIXES = ['/campaigns', '/settings'] as const;

export function isProtectedPath(pathname: string): boolean {
  return PROTECTED_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

export function loginUrl(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}

/** Куда вернуться после входа: только адрес этого сайта и не сам вход — иначе главное меню. */
export function safeNext(next: string | null): string {
  if (next === null || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/login')) return '/';
  return next;
}
