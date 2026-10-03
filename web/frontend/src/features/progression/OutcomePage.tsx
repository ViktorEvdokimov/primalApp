import { Alert, Anchor, Button, Group, Loader, NativeSelect, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { previewBattleResult } from '../../api/generated/battles/battles';
import { useGetCampaign } from '../../api/generated/campaigns/campaigns';
import type { HunterSheet, ResultAppliedNext, ResultPreview } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useActiveBattle } from '../battle/useActiveBattle';
import { hasPendingResult, resultRequest, type CampaignLocalBattle } from './campaignBattle';
import { ConfirmConsequencesDialog } from './ConfirmConsequencesDialog';
import { OutcomeEditForm } from './OutcomeEditForm';
import { CampaignChangedPanel, NotSentPanel } from './ResultSubmission';
import { RewardsView } from './RewardsView';
import { campaignChanged, useResultSubmission, type ResultAction } from './useResultSubmission';

/**
 * Итог боя кампании (`battle.md` §9, п. 5–8): превью наград и «Принять», «Редактировать», «Отклонить».
 * Бой берётся из браузера — это бой, результат которого ещё не отправлен.
 */
export function OutcomePage() {
  const campaignId = Number(useParams().id);
  const active = useActiveBattle();
  // Бой запоминается при открытии: после отправки он удаляется из браузера, а экран ещё показывает итог
  const [battle] = useState(() => active.battle);
  const back = (
    <Anchor component={Link} to={`/campaigns/${campaignId}`} size="sm" data-testid="outcome-back">
      {ru.progression.backToSheet}
    </Anchor>
  );

  if (!hasPendingResult(battle) || battle.campaign.id !== campaignId) {
    return (
      <Stack gap="md" data-testid="page-campaign-outcome">
        {back}
        <Text c="dimmed" data-testid="outcome-no-battle">
          {ru.progression.noCampaignBattle}
        </Text>
      </Stack>
    );
  }
  return <Outcome battle={battle} back={back} />;
}

function Outcome({ battle, back }: { battle: CampaignLocalBattle; back: ReactNode }) {
  const navigate = useNavigate();
  const campaignId = battle.campaign.id;
  const submission = useResultSubmission(battle);
  const [editing, setEditing] = useState(false);
  const [dismissing, setDismissing] = useState(false);
  const [completed, setCompleted] = useState(false);
  const victory = battle.state.status === 'VICTORY';
  // Пролог (36.1): победа принимается без окна наград — сразу переход главы
  const autoAccept = battle.campaign.purpose === 'PROLOGUE' && victory;

  const preview = useQuery({
    queryKey: ['battle-result-preview', battle.id],
    queryFn: () => previewBattleResult(campaignId, battle.id, resultRequest(battle)),
    enabled: !autoAccept,
    retry: false,
    staleTime: Infinity,
  });
  // Карты наград выдаются любому охотнику: кому — выбирается до «Принять» (по умолчанию первому в отряде)
  const squad = useGetCampaign(campaignId, { query: { enabled: !autoAccept, retry: false } });
  const hunters = squad.data?.hunters ?? [];
  const [holders, setHolders] = useState<Record<number, number>>({});
  const rewardCards = preview.data?.rewards.rewardCards ?? [];
  const cardHolders = (): number[] | null => {
    const first = hunters[0]?.id;
    return rewardCards.length === 0 || first === undefined ? null : rewardCards.map((_, index) => holders[index] ?? first);
  };

  const finish = (next: ResultAppliedNext | null) => {
    if (next === 'CHAPTER_TRANSITION') navigate(`/campaigns/${campaignId}/transition`, { replace: true });
    else if (next === 'CAMPAIGN_SHEET') navigate(`/campaigns/${campaignId}`, { replace: true });
    else if (next === 'CAMPAIGN_COMPLETED') setCompleted(true);
  };
  const send = (action: ResultAction, overrides: Parameters<typeof submission.submit>[1] = null) =>
    void submission.submit(action, overrides, action === 'ACCEPT' ? cardHolders() : null).then(finish);

  // Пролог отправляется один раз при открытии экрана; ref — последняя версия отправки для эффекта
  const acceptNow = useRef<() => void>(() => undefined);
  useEffect(() => {
    acceptNow.current = () => send('ACCEPT');
  });
  const started = useRef(false);
  useEffect(() => {
    if (!autoAccept || started.current) return;
    started.current = true;
    acceptNow.current();
  }, [autoAccept]);

  // Сеть вернулась, а итог не ушёл — отправить снова (задача 7.1). Слушатель — с открытия экрана, а что
  // делать, решается по состоянию отправки на момент события (ref обновляется сразу после рендера)
  const retryNow = useRef<() => void>(() => undefined);
  const offlineSubmit = submission.state.kind === 'OFFLINE';
  useLayoutEffect(() => {
    retryNow.current = offlineSubmit ? () => void submission.retry().then(finish) : () => undefined;
  });
  useEffect(() => {
    const online = () => retryNow.current();
    window.addEventListener('online', online);
    return () => window.removeEventListener('online', online);
  }, []);

  if (completed) {
    return (
      <Stack gap="md" align="center" py="xl" data-testid="campaign-completed">
        <Title order={1}>{ru.progression.completedTitle}</Title>
        <Text size="lg">{ru.progression.completedText}</Text>
        <Button component={Link} to={`/campaigns/${campaignId}`} data-testid="campaign-completed-sheet">
          {ru.progression.backToSheet}
        </Button>
      </Stack>
    );
  }

  const state = submission.state;
  const sending = state.kind === 'SENDING';
  const changed = state.kind === 'CHANGED' ? state.changed : campaignChanged(preview.error);
  const offline =
    state.kind === 'OFFLINE' || (preview.error instanceof ApiError && preview.error.code === 'NETWORK_ERROR');

  return (
    <Stack gap="md" data-testid="page-campaign-outcome">
      {back}
      <Title order={2} data-testid="outcome-title">
        {victory ? ru.progression.rewardsVictory : ru.progression.rewardsDefeat}
      </Title>

      {changed !== null && (
        <CampaignChangedPanel changed={changed} loading={sending} onDismiss={() => send('DISMISS')} />
      )}
      {changed === null && offline && (
        <NotSentPanel
          loading={sending || preview.isFetching}
          onRetry={() => (state.kind === 'OFFLINE' ? void submission.retry().then(finish) : void preview.refetch())}
        />
      )}
      {state.kind === 'ERROR' && (
        <Alert color="red" data-testid="outcome-error">
          {state.message}
        </Alert>
      )}
      {(autoAccept || preview.isPending) && changed === null && !offline && (
        <Group gap="xs">
          <Loader size="xs" />
          <Text size="sm" c="dimmed">
            {ru.progression.loadingRewards}
          </Text>
        </Group>
      )}

      {preview.data !== undefined && changed === null && (
        editing ? (
          <OutcomeEditForm
            victory={victory}
            rewards={preview.data.rewards}
            battleBoss={battle.boss?.code ?? null}
            loading={sending}
            onCancel={() => setEditing(false)}
            onAccept={(overrides) => send('ACCEPT', overrides)}
          />
        ) : (
          <PreviewView
            preview={preview.data}
            hunters={hunters}
            holders={holders}
            onHolder={(index, hunterId) => setHolders((current) => ({ ...current, [index]: hunterId }))}
            sending={sending}
            onAccept={() => send('ACCEPT')}
            onEdit={() => setEditing(true)}
            onDismiss={() => setDismissing(true)}
          />
        )
      )}

      <ConfirmConsequencesDialog
        opened={dismissing && preview.data !== undefined}
        title={ru.progression.dismissTitle}
        intro={ru.progression.dismissIntro}
        consequences={preview.data?.dismissConsequences ?? []}
        loading={sending}
        onCancel={() => setDismissing(false)}
        onConfirm={() => {
          setDismissing(false);
          send('DISMISS');
        }}
      />
    </Stack>
  );
}

interface PreviewViewProps {
  preview: ResultPreview;
  hunters: HunterSheet[];
  holders: Record<number, number>;
  onHolder: (index: number, hunterId: number) => void;
  sending: boolean;
  onAccept: () => void;
  onEdit: () => void;
  onDismiss: () => void;
}

function PreviewView({ preview, hunters, holders, onHolder, sending, onAccept, onEdit, onDismiss }: PreviewViewProps) {
  const { rewards } = preview;
  const victory = preview.result === 'VICTORY';
  const bossMissing = victory && rewards.trophy === null;
  const lines = [
    ...(preview.quest === null ? [] : [ru.progression.quest(preview.quest.number, preview.quest.name)]),
    ...(rewards.trophy === null ? [] : [ru.progression.trophy(rewards.trophy.name)]),
    ...rewards.messages,
  ];
  return (
    <Stack gap="md" data-testid="outcome-preview" data-purpose={preview.purpose}>
      {victory && preview.otherActiveBattles.length > 0 && (
        <Alert color="yellow" data-testid="outcome-others-warning">
          {ru.progression.othersWarning(preview.otherActiveBattles.map((battle) => battle.startedBy.name).join(', '))}
        </Alert>
      )}
      <RewardsView
        lines={lines}
        perHunter={rewards.perHunter}
        openQuests={rewards.openQuests}
        achievements={rewards.achievements}
        rewardCards={rewards.rewardCards}
        rules={preview.rules}
      />
      {rewards.rewardCards.length > 0 && hunters.length > 0 && (
        <Stack gap="xs" data-testid="outcome-card-holders">
          {rewards.rewardCards.map((card, index) => (
            <NativeSelect
              key={`${card}-${index}`}
              label={ru.progression.rewardCardHolder(card)}
              value={String(holders[index] ?? hunters[0]?.id)}
              data={hunters.map((hunter) => ({ value: String(hunter.id), label: hunter.playerName }))}
              onChange={(event) => onHolder(index, Number(event.currentTarget.value))}
              data-testid="outcome-card-holder"
              data-card={card}
            />
          ))}
        </Stack>
      )}
      {bossMissing && (
        <Alert color="yellow" data-testid="outcome-boss-missing">
          {ru.progression.bossMissing}
        </Alert>
      )}
      <Group grow>
        <Button loading={sending} disabled={bossMissing} onClick={onAccept} data-testid="outcome-accept">
          {ru.progression.accept}
        </Button>
        <Button variant="light" disabled={sending} onClick={onEdit} data-testid="outcome-edit-open">
          {ru.progression.edit}
        </Button>
      </Group>
      <Button variant="subtle" color="red" disabled={sending} onClick={onDismiss} data-testid="outcome-dismiss">
        {ru.progression.dismiss}
      </Button>
    </Stack>
  );
}
