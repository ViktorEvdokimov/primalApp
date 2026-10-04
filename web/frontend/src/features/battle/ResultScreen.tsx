import { Button, Stack, Text, Title } from '@mantine/core';
import type { BattleState } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';

interface ResultScreenProps {
  state: BattleState;
  message: string;
  undoDescription: string | null;
  onUndo: () => void;
  /** Главное действие: «Новый бой» в экспедиции; в бою кампании — «К наградам» или «Завершить бой» (поражение). */
  primary: { label: string; onClick: () => void; testId: string };
  onMenu: () => void;
}

/**
 * Экраны победы и поражения (app/doc/behavior.md §4–5; причина поражения — D-14). Ошибочный итог можно
 * отменить, пока не начат новый бой (doc/battle.md §6).
 */
export function ResultScreen({ state, message, undoDescription, onUndo, primary, onMenu }: ResultScreenProps) {
  const victory = state.status === 'VICTORY';
  return (
    <Stack gap="md" align="center" py="xl" data-testid="battle-result" data-result={state.status}>
      <Title order={1} data-testid="battle-result-title">
        {victory ? ru.battle.victoryTitle : ru.battle.defeatTitle}
      </Title>
      <Text size="lg" data-testid="battle-result-reason">
        {victory
          ? ru.battle.victoryText
          : state.defeatReason === 'SURRENDER'
            ? ru.battle.defeatSurrender
            : ru.battle.defeatRounds}
      </Text>
      {message !== '' && (
        <Text c="dimmed" size="sm" data-testid="battle-message">
          {message}
        </Text>
      )}
      <Stack gap="xs" w="100%" maw={320}>
        <Button size="lg" onClick={primary.onClick} data-testid={primary.testId}>
          {primary.label}
        </Button>
        {undoDescription !== null && (
          <Button variant="light" onClick={onUndo} data-testid="battle-result-undo">
            {ru.battle.undo}: {undoDescription}
          </Button>
        )}
        <Button variant="subtle" onClick={onMenu} data-testid="battle-result-menu">
          {ru.battle.toMenu}
        </Button>
      </Stack>
    </Stack>
  );
}
