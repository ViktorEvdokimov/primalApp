import { Badge, Button, Card, Group, Stack, Text } from '@mantine/core';
import type { DeviceSummary } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' });

interface DevicesListProps {
  devices: DeviceSummary[];
  revoking: boolean;
  onRevoke: (device: DeviceSummary) => void;
}

/** «Мои устройства»: браузеры, где выполнен вход; текущее помечено. */
export function DevicesList({ devices, revoking, onRevoke }: DevicesListProps) {
  return (
    <Stack gap="xs" data-testid="settings-devices">
      {devices.map((device) => (
        <Card key={device.id} withBorder padding="sm" data-testid="settings-device" data-current={device.current}>
          <Group justify="space-between" wrap="nowrap" align="flex-start">
            <Stack gap={2}>
              <Group gap="xs">
                <Text fw={500}>{device.userAgent}</Text>
                {device.current && (
                  <Badge size="sm" variant="light" data-testid="settings-device-current">
                    {ru.settings.current}
                  </Badge>
                )}
              </Group>
              <Text size="sm" c="dimmed">
                {ru.settings.lastSeen(DATE_FORMAT.format(new Date(device.lastSeenAt)))}
              </Text>
            </Stack>
            <Button
              size="xs"
              variant="light"
              color="red"
              disabled={revoking}
              onClick={() => onRevoke(device)}
              data-testid="settings-device-revoke"
            >
              {ru.settings.revoke}
            </Button>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
