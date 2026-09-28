import { ActionIcon, Button, Group, Modal, Stack, Switch, Text } from '@mantine/core';
import { useState } from 'react';
import { ru } from '../../shared/i18n/ru';

type Status = 'hardened' | 'resilient';

const STATUSES: Record<Status, { title: string; info: string }> = {
  hardened: { title: ru.battle.hardened, info: ru.battle.hardenedInfo },
  resilient: { title: ru.battle.resilient, info: ru.battle.resilientInfo },
};

interface StatusTogglesProps {
  hardened: boolean;
  resilient: boolean;
  onChange: (status: Status, value: boolean) => void;
}

/** Два независимых статуса монстра с описанием по кнопке «i» (R-3). */
export function StatusToggles({ hardened, resilient, onChange }: StatusTogglesProps) {
  const [info, setInfo] = useState<Status | null>(null);
  const values: Record<Status, boolean> = { hardened, resilient };
  return (
    <Stack gap="xs">
      {(Object.keys(STATUSES) as Status[]).map((status) => (
        <Group key={status} justify="space-between" wrap="nowrap">
          <Group gap="xs" wrap="nowrap">
            <Text>{STATUSES[status].title}:</Text>
            <ActionIcon
              size="sm"
              radius="xl"
              color="gray"
              variant="filled"
              aria-label={ru.battle.infoAbout(STATUSES[status].title)}
              onClick={() => setInfo(status)}
              data-testid={`battle-${status}-info`}
            >
              i
            </ActionIcon>
          </Group>
          <Switch
            size="md"
            aria-label={STATUSES[status].title}
            checked={values[status]}
            onChange={(event) => onChange(status, event.currentTarget.checked)}
            data-testid={`battle-${status}`}
          />
        </Group>
      ))}
      <Modal
        opened={info !== null}
        onClose={() => setInfo(null)}
        title={info === null ? '' : STATUSES[info].title}
        centered
      >
        <Stack gap="md">
          <Text data-testid="battle-status-info">{info === null ? '' : STATUSES[info].info}</Text>
          <Group justify="flex-end">
            <Button onClick={() => setInfo(null)} data-testid="battle-status-info-close">
              {ru.battle.understood}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
