import { ActionIcon, Button, Group, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';
import type { useDamageInput } from './useDamageInput';

const QUICK_AMOUNTS = [1, 5, 10, 50];

interface DamageInputProps {
  input: ReturnType<typeof useDamageInput>;
  /** Короткий отклик на нажатие кнопок ввода. */
  onPress: () => void;
}

/** Нанесение урона: кнопки +N с таймером 2 с и ручной ввод (doc/battle.md §8). */
export function DamageInput({ input, onPress }: DamageInputProps) {
  return (
    <Stack gap="xs">
      {/* «Ожидание…» — в строке заголовка: кнопки +N не сдвигаются под пальцем между нажатиями */}
      <Group justify="space-between" wrap="nowrap">
        <Text fw={700}>{ru.battle.dealDamage}</Text>
        {input.timerRunning && (
          <Text c="orange" data-testid="battle-pending">
            {ru.battle.pending(input.pending)}
          </Text>
        )}
      </Group>
      <SimpleGrid cols={4} spacing="xs">
        {QUICK_AMOUNTS.map((amount) => (
          <Button
            key={amount}
            size="md"
            onClick={() => {
              onPress();
              input.pressQuick(amount);
            }}
            data-testid={`battle-quick-${amount}`}
          >
            +{amount}
          </Button>
        ))}
      </SimpleGrid>
      <Group gap="xs" align="flex-end" wrap="nowrap">
        <TextInput
          style={{ flex: 1 }}
          label={ru.battle.damageInput}
          inputMode="numeric"
          autoComplete="off"
          value={input.text}
          onFocus={input.focusField}
          onChange={(event) => input.changeText(event.currentTarget.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') input.commit();
          }}
          rightSection={
            <ActionIcon
              variant="subtle"
              aria-label={ru.battle.toggleSign}
              onClick={input.toggleSign}
              data-testid="battle-damage-sign"
            >
              ±
            </ActionIcon>
          }
          data-testid="battle-damage-input"
        />
        <Button onClick={input.commit} data-testid="battle-damage-ok">
          {ru.battle.ok}
        </Button>
        <Button
          variant="default"
          onClick={() => {
            onPress();
            input.cancel();
          }}
          data-testid="battle-damage-cancel"
        >
          {ru.battle.cancelInput}
        </Button>
      </Group>
      <Text size="xs" c="dimmed" data-testid="battle-negative-hint">
        {ru.battle.negativeHint}
      </Text>
      {input.timerRunning && (
        <Button fullWidth onClick={input.commit} data-testid="battle-apply-now">
          {ru.battle.applyNow(input.pending)}
        </Button>
      )}
    </Stack>
  );
}
