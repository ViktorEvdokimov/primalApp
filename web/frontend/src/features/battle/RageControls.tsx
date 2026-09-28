import { Button, SimpleGrid, Stack, Text } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';

interface RageControlsProps {
  hunterCount: number;
  onAdjust: (delta: number) => void;
}

/** Ярость: −1, +1, +1 за охотника, +1 за охотника кроме одного (app/doc/behavior.md §2.3). */
export function RageControls({ hunterCount, onAdjust }: RageControlsProps) {
  const buttons: { label: string; delta: number; testId: string }[] = [
    { label: ru.battle.rageMinus, delta: -1, testId: 'battle-rage-minus-1' },
    { label: ru.battle.ragePlus, delta: 1, testId: 'battle-rage-plus-1' },
    { label: ru.battle.ragePerHunter, delta: hunterCount, testId: 'battle-rage-per-hunter' },
    { label: ru.battle.ragePerHunterMinusOne, delta: hunterCount - 1, testId: 'battle-rage-per-hunter-minus-1' },
  ];
  return (
    <Stack gap="xs">
      <Text fw={700}>{ru.battle.rageTitle}</Text>
      <SimpleGrid cols={2} spacing="xs">
        {buttons.map((button) => (
          <Button
            key={button.testId}
            variant="light"
            disabled={button.delta === 0}
            onClick={() => onAdjust(button.delta)}
            data-testid={button.testId}
          >
            {button.label}
          </Button>
        ))}
      </SimpleGrid>
    </Stack>
  );
}
