import { Button, Group, Modal, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { createLocalBattle, type LocalBattle } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { randomId } from '../../shared/randomId';
import { bossStances, toBattleBoss } from '../battle/bossCatalog';
import { useActiveBattle } from '../battle/useActiveBattle';
import { useBattleMark } from '../progression/useBattleMark';
import { BattleSetupForm, type BattleSetupValues } from './BattleSetupForm';

const DEFAULT_HUNTERS = '4';

/** «Подготовка к бою» экспедиции — как QuickBattleHost в app (app/doc/behavior.md §1). Вход не нужен. */
export function ExpeditionSetupPage() {
  const navigate = useNavigate();
  const active = useActiveBattle();
  const marks = useBattleMark();
  const [replacement, setReplacement] = useState<LocalBattle | null>(null);

  const begin = (battle: LocalBattle) => {
    // Заменённый бой кампании брошен: его отметка о начале снимается (doc/battle.md §9)
    const replaced = active.getBattle();
    if (replaced !== null) marks.abandon(replaced);
    active.start(battle);
    navigate('/battle');
  };

  const startBattle = ({ boss, difficulty, hunterCount, toughnessPerHunter, stanceChange }: BattleSetupValues) => {
    const battle = createLocalBattle({
      id: randomId(),
      mode: 'EXPEDITION',
      campaign: null,
      boss: boss === undefined ? null : toBattleBoss(boss),
      difficulty,
      stances: boss === undefined ? [] : bossStances(boss, difficulty),
      params: { hunterCount, toughnessPerHunter, stanceChange },
      now: new Date().toISOString(),
    });
    if (active.hasUnfinished) setReplacement(battle);
    else begin(battle);
  };

  return (
    <Stack gap="md" data-testid="page-expedition-new">
      <div>
        <Title order={2}>{ru.pages.expeditionNew}</Title>
        <Text c="dimmed">{ru.expedition.ready}</Text>
      </div>

      <BattleSetupForm testIdPrefix="expedition" initialHunters={DEFAULT_HUNTERS} onStart={startBattle} />
      <Button component={Link} to="/" variant="subtle" data-testid="expedition-menu">
        {ru.expedition.toMenu}
      </Button>

      <ReplaceBattleDialog
        opened={replacement !== null}
        onCancel={() => setReplacement(null)}
        onConfirm={() => replacement !== null && begin(replacement)}
        testIdPrefix="expedition"
      />
    </Stack>
  );
}

interface ReplaceBattleDialogProps {
  opened: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  testIdPrefix: string;
}

/** Незаконченный бой будет потерян — подтверждение перед новым боем. */
export function ReplaceBattleDialog({ opened, onCancel, onConfirm, testIdPrefix }: ReplaceBattleDialogProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={ru.battle.replaceTitle} centered>
      <Stack gap="md">
        <Text>{ru.battle.replaceUnfinished}</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel} data-testid={`${testIdPrefix}-replace-cancel`}>
            {ru.battle.cancel}
          </Button>
          <Button color="red" onClick={onConfirm} data-testid={`${testIdPrefix}-replace-confirm`}>
            {ru.battle.replaceConfirm}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
