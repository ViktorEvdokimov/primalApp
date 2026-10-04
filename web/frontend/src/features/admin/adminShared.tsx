import { Alert, Badge, Button, Group, Modal, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { ru } from '../../shared/i18n/ru';

export function EditedBadge({ edited }: { edited: boolean }) {
  if (!edited) return null;
  return (
    <Badge color="orange" variant="light" data-testid="admin-edited">
      {ru.admin.edited}
    </Badge>
  );
}

interface ActionsProps {
  error: string | null;
  missing: number;
  dirty: boolean;
  edited: boolean;
  saving: boolean;
  onSave: () => void;
  onDiscard: () => void;
  onReset: () => Promise<unknown>;
}

/** «Сохранить», «Отменить изменения», «Вернуть исходные» (с подтверждением) и ошибки формы. */
export function Actions({ error, missing, dirty, edited, saving, onSave, onDiscard, onReset }: ActionsProps) {
  const [confirming, setConfirming] = useState(false);
  return (
    <Stack gap="xs">
      <Modal opened={confirming} onClose={() => setConfirming(false)} title={ru.admin.reset} centered>
        <Stack gap="md">
          <Text size="sm">{ru.admin.resetConfirm}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(false)}>
              {ru.inventory.cancel}
            </Button>
            <Button
              color="red"
              onClick={() => {
                setConfirming(false);
                void onReset();
              }}
              data-testid="admin-reset-confirm"
            >
              {ru.admin.reset}
            </Button>
          </Group>
        </Stack>
      </Modal>
      {error !== null && (
        <Alert color="red" data-testid="admin-error">
          {error}
        </Alert>
      )}
      {missing > 0 && (
        <Text size="sm" c="red" data-testid="admin-missing">
          {ru.admin.problems(missing)}
        </Text>
      )}
      {dirty && missing === 0 && (
        <Text size="sm" c="dimmed">
          {ru.admin.unsaved}
        </Text>
      )}
      <Group>
        <Button disabled={!dirty || missing > 0} loading={saving} onClick={onSave} data-testid="admin-save">
          {ru.admin.save}
        </Button>
        <Button variant="default" disabled={!dirty} onClick={onDiscard} data-testid="admin-discard">
          {ru.admin.discard}
        </Button>
        {edited && (
          <Button variant="subtle" color="red" onClick={() => setConfirming(true)} data-testid="admin-reset">
            {ru.admin.reset}
          </Button>
        )}
      </Group>
    </Stack>
  );
}
