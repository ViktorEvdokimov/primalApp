import { SegmentedControl, Stack, Text, TextInput } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';
import type { StanceFormErrors, StanceFormValues } from './stanceForm';

interface StanceFieldsProps {
  values: StanceFormValues;
  errors?: StanceFormErrors;
  onChange: (values: StanceFormValues) => void;
  /** Префикс data-testid: `<префикс>-toughness`, `<префикс>-mode`, `<префикс>-at-health`. */
  testIdPrefix: string;
}

const MODES: StanceFormValues['mode'][] = ['HEALTH', 'ON_DEMAND', 'FINAL'];

/** Прочность за охотника и смена стойки (D-2): подготовка боя и окно «Смена стойки!». */
export function StanceFields({ values, errors = {}, onChange, testIdPrefix }: StanceFieldsProps) {
  return (
    <Stack gap="sm">
      <TextInput
        label={ru.stance.toughness}
        description={ru.stance.toughnessHint}
        inputMode="numeric"
        value={values.toughness}
        error={errors.toughness}
        onChange={(event) => onChange({ ...values, toughness: event.currentTarget.value })}
        data-testid={`${testIdPrefix}-toughness`}
      />
      <div>
        <Text size="sm" fw={500} mb={4}>
          {ru.stance.change}
        </Text>
        <SegmentedControl
          fullWidth
          value={values.mode}
          onChange={(mode) => onChange({ ...values, mode: mode as StanceFormValues['mode'] })}
          data={MODES.map((mode) => ({ value: mode, label: ru.stance.modes[mode] }))}
          data-testid={`${testIdPrefix}-mode`}
        />
      </div>
      {values.mode === 'HEALTH' && (
        <TextInput
          label={ru.stance.atHealth}
          inputMode="numeric"
          value={values.atHealth}
          error={errors.atHealth}
          onChange={(event) => onChange({ ...values, atHealth: event.currentTarget.value })}
          data-testid={`${testIdPrefix}-at-health`}
        />
      )}
    </Stack>
  );
}
