import { Button, Group, List, Modal, Stack, Text } from '@mantine/core';
import type { ActiveBattle } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { describeActiveBattle } from './campaignBattle';

interface ActiveBattleWarningProps {
  battles: ActiveBattle[];
  opened: boolean;
  onCancel: () => void;
  onStart: () => void;
}

/**
 * «Уже идёт бой: …» перед стартом (`battle.md` §9). Только предупреждение: начать бой можно всегда, главу
 * засчитывает первая принятая победа.
 */
export function ActiveBattleWarning({ battles, opened, onCancel, onStart }: ActiveBattleWarningProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={ru.progression.warningTitle} centered>
      <Stack gap="md" data-testid="active-battle-warning">
        <List spacing={4} size="sm">
          {battles.map((battle) => (
            <List.Item key={battle.id} data-testid="active-battle-warning-item">
              {describeActiveBattle(battle)}
            </List.Item>
          ))}
        </List>
        <Text size="sm">{ru.progression.warningText}</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel} data-testid="active-battle-warning-cancel">
            {ru.progression.cancel}
          </Button>
          <Button onClick={onStart} data-testid="active-battle-warning-start">
            {ru.progression.warningStart}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
