import { Button, Group, List, Modal, Stack, Text } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';

interface ConfirmConsequencesDialogProps {
  opened: boolean;
  title: string;
  intro: string;
  /** Пункты от сервера: `dismissConsequences` итога боя или `rejectConsequences` перехода главы. */
  consequences: string[];
  loading?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Подтверждение «Отклонить» с описанием того, чего приложение не сделает (решение пользователя 4, `api.md`
 * §7.2, §8). Только «Отклонить» в этом окне отправляет запрос.
 */
export function ConfirmConsequencesDialog({
  opened,
  title,
  intro,
  consequences,
  loading = false,
  onCancel,
  onConfirm,
}: ConfirmConsequencesDialogProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={title} centered>
      <Stack gap="md" data-testid="consequences-dialog">
        <Text>{intro}</Text>
        <List spacing={4} size="sm">
          {consequences.map((line) => (
            <List.Item key={line} data-testid="consequence">
              {line}
            </List.Item>
          ))}
        </List>
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel} data-testid="consequences-cancel">
            {ru.progression.back}
          </Button>
          <Button color="red" loading={loading} onClick={onConfirm} data-testid="consequences-confirm">
            {ru.progression.dismiss}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
