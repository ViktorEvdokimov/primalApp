/** Сайт и приложение для Android: установлено ли приложение и адрес для перехода в него. */

/** Пакет приложения для Android (app/androidApp). */
export const ANDROID_PACKAGE = 'com.primalapp.android';

/** Страница приложения в RuStore — там его устанавливают и обновляют. */
export const RUSTORE_URL = `https://www.rustore.ru/catalog/app/${ANDROID_PACKAGE}`;

interface RelatedApplication {
  platform: string;
  id?: string;
}

type NavigatorWithRelatedApps = Navigator & {
  getInstalledRelatedApps?: () => Promise<RelatedApplication[]>;
};

/** Сайт открыт на Android в браузере (не установленный PWA). */
export function isAndroidBrowser(): boolean {
  const standalone = typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches;
  return /Android/i.test(navigator.userAgent) && !standalone;
}

/**
 * Установлено ли приложение: Chrome на Android отвечает по related_applications манифеста сайта (платформа `play` —
 * любое приложение Android, откуда бы оно ни было установлено). Приложение видно с версии 1.3 — в ней объявлена связь
 * с сайтом (asset_statements); `false` — нет приложения, старая версия или браузер не умеет проверять.
 */
export async function isAppInstalled(): Promise<boolean> {
  const nav = navigator as NavigatorWithRelatedApps;
  if (typeof nav.getInstalledRelatedApps !== 'function') return false;
  try {
    const apps = await nav.getInstalledRelatedApps();
    return apps.some((app) => app.platform === 'play' && app.id === ANDROID_PACKAGE);
  } catch {
    return false;
  }
}

/**
 * Адрес intent:// — Android открывает ту же страницу в приложении. Если открыть нечем (приложения нет или версия до
 * 1.3, где сайт не открывается), браузер переходит на [fallbackUrl]: по умолчанию остаётся на странице.
 */
export function intentUrl(
  location: { host: string; pathname: string; search: string; href: string },
  fallbackUrl: string = location.href,
): string {
  return (
    `intent://${location.host}${location.pathname}${location.search}` +
    `#Intent;scheme=https;package=${ANDROID_PACKAGE};S.browser_fallback_url=${encodeURIComponent(fallbackUrl)};end`
  );
}
