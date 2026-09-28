import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import type { TransitionDecision } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

interface DecisionDialogProps {
  /** Решение без ответа; {@code null} — окно закрыто. */
  decision: TransitionDecision | null;
  onAnswer: (decision: string, option: string) => void;
}

/**
 * Решение главы (глава 7: «Хотите ли вы тренироваться в лагере у Волтьяра?») — до наград главы. Окно
 * закрывается только ответом: без него переход не применить (как в app, D-10).
 */
export function DecisionDialog({ decision, onAnswer }: DecisionDialogProps) {
  return (
    <Modal
      opened={decision !== null}
      onClose={() => undefined}
      withCloseButton={false}
      closeOnClickOutside={false}
      closeOnEscape={false}
      title={ru.progression.decisionTitle}
      centered
    >
      {decision !== null && (
        <Stack gap="md" data-testid="decision-dialog" data-decision={decision.code}>
          <Text>{decision.question}</Text>
          <Group justify="flex-end">
            {decision.options.map((option) => (
              <Button
                key={option.code}
                onClick={() => onAnswer(decision.code, option.code)}
                data-testid={`decision-option-${option.code}`}
              >
                {option.label}
              </Button>
            ))}
          </Group>
        </Stack>
      )}
    </Modal>
  );
}
