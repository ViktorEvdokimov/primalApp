import { Button, Group, Modal, Stack, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import { invalidValueMessages, type BattleCommand, type PendingStance } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { StanceFields } from './StanceFields';
import { parseStanceForm, parseWholeNumber, stanceFormFrom, type StanceFormErrors } from './stanceForm';

interface StanceDialogProps {
  pending: PendingStance | null;
  onConfirm: (command: Extract<BattleCommand, { type: 'CONFIRM_STANCE' }>) => void;
  /** «Отмена» — отмена действия, которое сменило стойку (doc/battle.md §6). */
  onCancel: () => void;
}

/** Окно «Смена стойки!» (app/doc/behavior.md §3). Модальное: бой ждёт подтверждения. */
export function StanceDialog({ pending, onConfirm, onCancel }: StanceDialogProps) {
  return (
    <Modal
      opened={pending !== null}
      onClose={() => {}}
      withCloseButton={false}
      closeOnClickOutside={false}
      closeOnEscape={false}
      title={ru.battle.stanceDialogTitle}
      centered
    >
      {pending !== null && (
        // Перенесённый урон может сразу сменить стойку ещё раз — поля заполняются заново для каждой стойки
        <StanceForm key={pending.stance} pending={pending} onConfirm={onConfirm} onCancel={onCancel} />
      )}
    </Modal>
  );
}

function StanceForm({ pending, onConfirm, onCancel }: StanceDialogProps & { pending: PendingStance }) {
  const [values, setValues] = useState(() => stanceFormFrom(pending.prefill));
  const [health, setHealth] = useState('');
  const [errors, setErrors] = useState<StanceFormErrors & { health?: string }>({});

  const confirm = () => {
    const parsed = parseStanceForm(values);
    const healthValue = health.trim() === '' ? undefined : parseWholeNumber(health);
    const healthError = healthValue === null || (healthValue !== undefined && (healthValue < 1 || healthValue > 10));
    setErrors({ ...(parsed.ok ? {} : parsed.errors), health: healthError ? invalidValueMessages.health : undefined });
    if (!parsed.ok || healthValue === null || healthError) return;
    onConfirm({
      type: 'CONFIRM_STANCE',
      toughnessPerHunter: parsed.toughnessPerHunter,
      stanceChange: parsed.stanceChange,
      ...(healthValue === undefined ? {} : { health: healthValue }),
    });
  };

  return (
    <Stack gap="sm" data-testid="stance-dialog">
      <Text>{ru.battle.stanceDialogStance(pending.stance)}</Text>
      <Text size="sm" data-testid="stance-dialog-source" data-from-catalog={pending.prefill !== null}>
        {pending.prefill === null ? ru.battle.stanceWithoutCatalog : ru.battle.stanceFromCatalog}
      </Text>
      {pending.carriedDamage > 0 && (
        <Text size="sm" fw={500} data-testid="stance-dialog-carried">
          {pending.carriedDamageResets
            ? ru.battle.carriedDamageResets(pending.carriedDamage)
            : ru.battle.carriedDamage(pending.carriedDamage)}
        </Text>
      )}
      <StanceFields values={values} errors={errors} onChange={setValues} testIdPrefix="stance" />
      <TextInput
        label={ru.battle.stanceHealth}
        description={ru.battle.stanceHealthHint}
        inputMode="numeric"
        value={health}
        error={errors.health}
        onChange={(event) => setHealth(event.currentTarget.value)}
        data-testid="stance-health"
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onCancel} data-testid="stance-dialog-cancel">
          {ru.battle.cancel}
        </Button>
        {/* Фокус на OK, а не на первом поле: на телефоне не выскакивает клавиатура */}
        <Button onClick={confirm} data-autofocus data-testid="stance-dialog-ok">
          {ru.battle.ok}
        </Button>
      </Group>
    </Stack>
  );
}
