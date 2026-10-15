import { Alert, Button, Group, Text } from '@mantine/core';
import { useNetwork } from '@mantine/hooks';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { ru } from '../i18n/ru';
import { RUSTORE_URL, intentUrl, isAndroidBrowser, isAppInstalled } from './openInApp';

/** Отказ, когда приложение найдено: до конца сеанса браузера. */
const DISMISSED_KEY = 'primal.openInApp.dismissed';
/** Отказ, когда приложение не найдено: время отказа, предложение возвращается через 30 дней. */
const STORE_DISMISSED_KEY = 'primal.openInApp.storeDismissedAt';
const STORE_DISMISS_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * `checking` — проверка идёт, `installed` — приложение найдено, `store` — не найдено (нет, версия до 1.3 или браузер
 * не умеет проверять), `none` — не Android.
 */
type AppState = 'checking' | 'installed' | 'store' | 'none';

function storage(kind: 'session' | 'local'): Storage | null {
  try {
    return kind === 'session' ? sessionStorage : localStorage;
  } catch {
    return null;
  }
}

function dismissedNow(): boolean {
  try {
    if (storage('session')?.getItem(DISMISSED_KEY) === '1') return true;
    const at = Number(storage('local')?.getItem(STORE_DISMISSED_KEY));
    return at > 0 && Date.now() - at < STORE_DISMISS_MS;
  } catch {
    return false;
  }
}

/**
 * «Продолжить в приложении» на Android (в браузере, онлайн). Приложение найдено (версия 1.3+) — та же страница
 * открывается в нём. Не найдено — та же кнопка: установленная 1.3+ откроется (браузеры без проверки), иначе — страница
 * приложения в RuStore (установить или обновить: версия до 1.3 из браузера не открывается).
 */
export function OpenInAppBanner() {
  const { online } = useNetwork();
  const location = useLocation();
  const [app, setApp] = useState<AppState>(() => (isAndroidBrowser() ? 'checking' : 'none'));
  const [dismissed, setDismissed] = useState(dismissedNow);

  useEffect(() => {
    if (app !== 'checking') return;
    let active = true;
    void isAppInstalled().then((installed) => {
      if (active) setApp(installed ? 'installed' : 'store');
    });
    return () => {
      active = false;
    };
  }, [app]);

  if (!online || dismissed || (app !== 'installed' && app !== 'store')) return null;

  const installed = app === 'installed';
  const page = { ...window.location, pathname: location.pathname, search: location.search };
  const dismiss = () => {
    setDismissed(true);
    try {
      if (installed) storage('session')?.setItem(DISMISSED_KEY, '1');
      else storage('local')?.setItem(STORE_DISMISSED_KEY, String(Date.now()));
    } catch {
      // без хранилища предложение просто появится снова при следующем открытии
    }
  };

  return (
    <Alert color="blue" variant="light" mb="sm" data-testid="open-in-app-banner" data-app={app}>
      <Text size="sm" mb="xs">
        {installed ? ru.app.openInApp : ru.app.openInAppStore}
      </Text>
      <Group gap="xs">
        <Button size="xs" component="a" href={installed ? intentUrl(page) : intentUrl(page, RUSTORE_URL)}>
          {ru.app.openInAppButton}
        </Button>
        <Button size="xs" variant="subtle" onClick={dismiss}>
          {ru.app.openInAppDismiss}
        </Button>
      </Group>
    </Alert>
  );
}
