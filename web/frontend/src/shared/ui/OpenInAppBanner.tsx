import { Alert, Button, Group, Text } from '@mantine/core';
import { useNetwork } from '@mantine/hooks';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { ru } from '../i18n/ru';
import { intentUrl, isAndroidBrowser, isAppInstalled } from './openInApp';

const DISMISSED_KEY = 'primal.openInApp.dismissed';

/**
 * «Открыть в приложении»: на Android, в режиме онлайн, если приложение установлено. Отказ запоминается до конца
 * сеанса браузера.
 */
export function OpenInAppBanner() {
  const { online } = useNetwork();
  const location = useLocation();
  const [installed, setInstalled] = useState(false);
  const [dismissed, setDismissed] = useState(() => {
    try {
      return sessionStorage.getItem(DISMISSED_KEY) === '1';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    if (!isAndroidBrowser()) return;
    let active = true;
    void isAppInstalled().then((value) => {
      if (active) setInstalled(value);
    });
    return () => {
      active = false;
    };
  }, []);

  if (!online || !installed || dismissed) return null;

  const dismiss = () => {
    setDismissed(true);
    try {
      sessionStorage.setItem(DISMISSED_KEY, '1');
    } catch {
      // без sessionStorage окно просто появится снова при следующем открытии
    }
  };

  return (
    <Alert color="blue" variant="light" mb="sm" data-testid="open-in-app-banner">
      <Text size="sm" mb="xs">
        {ru.app.openInApp}
      </Text>
      <Group gap="xs">
        <Button
          size="xs"
          component="a"
          href={intentUrl({ ...window.location, pathname: location.pathname, search: location.search })}
        >
          {ru.app.openInAppButton}
        </Button>
        <Button size="xs" variant="subtle" onClick={dismiss}>
          {ru.app.openInAppDismiss}
        </Button>
      </Group>
    </Alert>
  );
}
