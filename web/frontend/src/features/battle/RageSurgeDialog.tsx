import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';

/** Окно «Выплеск ярости» (R-8): после OK ярость сбрасывается до 1 за охотника. */
export function RageSurgeDialog({ opened, onConfirm }: { opened: boolean; onConfirm: () => void }) {
  return (
    <Modal
      opened={opened}
      onClose={onConfirm}
      title={ru.battle.rageSurgeTitle}
      withCloseButton={false}
      closeOnClickOutside={false}
      centered
    >
      <Stack gap="md" data-testid="rage-surge-dialog">
        <Text>{ru.battle.rageSurgeText}</Text>
        <Group justify="flex-end">
          <Button onClick={onConfirm} data-testid="rage-surge-ok">
            {ru.battle.ok}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
