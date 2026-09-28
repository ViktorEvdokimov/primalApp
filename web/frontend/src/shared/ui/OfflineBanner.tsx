import { Alert } from '@mantine/core';
import { useNetwork } from '@mantine/hooks';
import { ru } from '../i18n/ru';

/**
 * «Нет подключения» (задача 7.1): экспедиция и начатый бой работают без сети, а итог боя кампании
 * отправится, когда сеть появится.
 */
export function OfflineBanner() {
  const { online } = useNetwork();
  if (online) return null;
  return (
    <Alert color="yellow" variant="light" mb="sm" data-testid="offline-banner">
      {ru.app.offline}
    </Alert>
  );
}
