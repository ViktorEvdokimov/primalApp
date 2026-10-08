import { Alert, Button, Divider, Group, Modal, Stack, Text, Title } from '@mantine/core';
import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import type { BattleCommand, CampaignBattleInfo, LocalBattle } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { DamageInput } from './DamageInput';
import { InfoPanel } from './InfoPanel';
import { RageControls } from './RageControls';
import { RageSurgeDialog } from './RageSurgeDialog';
import { ResultScreen } from './ResultScreen';
import { StanceDialog } from './StanceDialog';
import { StatusToggles } from './StatusToggles';
import { useActiveBattle } from './useActiveBattle';
import { useDamageInput } from './useDamageInput';
import { useHighlights } from './useHighlights';
import { useVibration, vibrationFor } from './useVibration';
import { useWakeLock } from './useWakeLock';

/** «Задание 1», «Пролог», «Бой без задания» — строка боя кампании над информацией о монстре. */
function campaignPurpose(campaign: CampaignBattleInfo): string {
  if (campaign.purpose === 'QUEST' && campaign.questNumber !== null) return ru.progression.questShort(campaign.questNumber);
  return ru.progression.purpose[campaign.purpose === 'QUEST' ? 'FREE' : campaign.purpose];
}

/** Экран боя (app/doc/behavior.md §2–6). Команды выполняет движок через `useActiveBattle`. */
export function BattlePage() {
  const active = useActiveBattle();
  if (active.battle === null) {
    return (
      <Stack gap="md" data-testid="page-battle">
        <Title order={2}>{ru.pages.battle}</Title>
        <Text c="dimmed" data-testid="battle-empty">
          {ru.battle.noBattle}
        </Text>
        <Button component={Link} to="/expedition/new" data-testid="battle-to-expedition">
          {ru.battle.toExpedition}
        </Button>
      </Stack>
    );
  }
  // Новый бой — новое состояние экрана: ввод, подсветка, сообщение
  return <BattleView key={active.battle.id} battle={active.battle} />;
}

function BattleView({ battle }: { battle: LocalBattle }) {
  const navigate = useNavigate();
  const active = useActiveBattle();
  const vibrate = useVibration();
  const { highlighted, highlight } = useHighlights();
  const [message, setMessage] = useState('');
  const [surrenderOpened, setSurrenderOpened] = useState(false);
  const state = battle.state;
  useWakeLock(state.status === 'IN_PROGRESS');

  const run = useCallback(
    (command: BattleCommand) => {
      const before = active.getBattle()?.state;
      const result = active.dispatch(command);
      if (result === null || before === undefined) return;
      setMessage(result.message);
      if (result.ok) {
        highlight(before, result.state);
        vibrate(vibrationFor(result.events));
      } else {
        vibrate('SHORT');
      }
    },
    [active, highlight, vibrate],
  );

  const undoLast = () => {
    const before = active.getBattle()?.state;
    const result = active.undo();
    if (result === null || before === undefined) return;
    setMessage(result.message);
    highlight(before, result.battle.state);
    vibrate('SHORT');
  };

  const input = useDamageInput(useCallback((amount: number) => run({ type: 'DEAL_DAMAGE', amount }), [run]));

  const leave = (path: string) => {
    active.clear();
    navigate(path);
  };
  const campaign = battle.campaign;

  if (state.status !== 'IN_PROGRESS') {
    return (
      <Stack data-testid="page-battle">
        <ResultScreen
          state={state}
          message={message}
          undoDescription={active.nextUndo}
          onUndo={undoLast}
          primary={
            campaign === null
              ? { label: ru.battle.newBattle, onClick: () => leave('/expedition/new'), testId: 'battle-new' }
              : {
                  // Поражение наград не даёт: итог записывается и открывается лист кампании
                  label: state.status === 'VICTORY' ? ru.progression.toRewards : ru.progression.finishDefeat,
                  onClick: () => navigate(`/campaigns/${campaign.id}/outcome`),
                  testId: 'battle-to-rewards',
                }
          }
          // Итог боя кампании остаётся в браузере до отправки: меню покажет «Результат боя не отправлен»
          onMenu={() => (campaign === null ? leave('/') : navigate('/'))}
        />
      </Stack>
    );
  }

  return (
    <Stack gap="sm" data-testid="page-battle">
      {campaign !== null && (
        <Text size="sm" fw={600} data-testid="battle-campaign">
          {ru.progression.campaignBattle(campaign.name, campaignPurpose(campaign))}
        </Text>
      )}
      <Text size="sm" c="dimmed" data-testid="battle-boss">
        {battle.boss?.name ?? ru.battle.manualBoss} · {ru.battle.difficulty(battle.difficulty)}
      </Text>
      <InfoPanel state={state} highlighted={highlighted} />
      {message !== '' && (
        <Text size="sm" c="orange.3" fw={500} data-testid="battle-message">
          {message}
        </Text>
      )}
      {!active.persistent && (
        <Alert color="yellow" data-testid="battle-not-persistent">
          {ru.battle.notPersistent}
        </Alert>
      )}
      <Divider />

      <DamageInput input={input} onPress={() => vibrate('SHORT')} />
      <Button variant="default" fullWidth onClick={() => run({ type: 'HEAL_WOUND' })} data-testid="battle-heal">
        {ru.battle.healWound}
      </Button>
      {active.nextUndo !== null && (
        <Stack gap={2}>
          <Button variant="light" fullWidth onClick={undoLast} data-testid="battle-undo">
            {ru.battle.undo}
          </Button>
          <Text size="xs" c="dimmed" ta="center" data-testid="battle-undo-description">
            {ru.battle.undoWhat(active.nextUndo)}
          </Text>
        </Stack>
      )}
      <Divider />

      <RageControls hunterCount={state.hunterCount} onAdjust={(delta) => run({ type: 'ADJUST_RAGE', delta })} />
      <Divider />

      <StatusToggles
        hardened={state.monster.hardened}
        resilient={state.monster.resilient}
        onChange={(status, value) =>
          run(status === 'hardened' ? { type: 'SET_STATUS', hardened: value } : { type: 'SET_STATUS', resilient: value })
        }
      />
      <Divider />

      {state.monster.stanceChange.mode === 'ON_DEMAND' && (
        <Button fullWidth onClick={() => run({ type: 'CHANGE_STANCE' })} data-testid="battle-change-stance">
          {ru.battle.changeStance}
        </Button>
      )}
      <Button fullWidth size="md" onClick={() => run({ type: 'END_ROUND', damage: input.take() })} data-testid="battle-end-round">
        {ru.battle.endRound}
      </Button>
      <Button
        fullWidth
        variant="outline"
        color="red"
        onClick={() => {
          vibrate('SHORT');
          setSurrenderOpened(true);
        }}
        data-testid="battle-surrender"
      >
        {ru.battle.surrender}
      </Button>
      <Group grow>
        <Button variant="light" component={Link} to="/info" data-testid="battle-info">
          {ru.info.open}
        </Button>
        <Button variant="subtle" component={Link} to="/" data-testid="battle-menu">
          {ru.battle.toMenu}
        </Button>
      </Group>

      <StanceDialog pending={state.pendingStance} onConfirm={run} onCancel={undoLast} />
      <RageSurgeDialog
        opened={state.rageSurgePending && state.pendingStance === null}
        onConfirm={() => run({ type: 'ACK_RAGE_SURGE' })}
      />
      <Modal opened={surrenderOpened} onClose={() => setSurrenderOpened(false)} title={ru.battle.surrenderTitle} centered>
        <Stack gap="md">
          <Text>{ru.battle.surrenderText}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setSurrenderOpened(false)} data-testid="battle-surrender-cancel">
              {ru.battle.cancel}
            </Button>
            <Button
              color="red"
              onClick={() => {
                setSurrenderOpened(false);
                run({ type: 'SURRENDER' });
              }}
              data-testid="battle-surrender-confirm"
            >
              {ru.battle.surrender}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
