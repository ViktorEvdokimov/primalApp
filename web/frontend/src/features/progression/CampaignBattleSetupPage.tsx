import { Alert, Anchor, Button, Loader, Stack, Text, Title } from '@mantine/core';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { useGetBattleSetup } from '../../api/generated/battles/battles';
import { useGetCampaign } from '../../api/generated/campaigns/campaigns';
import type { BattleSetup } from '../../api/generated/primal.schemas';
import { createLocalBattle, type Difficulty, type LocalBattle } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { randomId } from '../../shared/randomId';
import { bossStances, isDifficulty, toBattleBoss, useBossCatalog } from '../battle/bossCatalog';
import { useActiveBattle } from '../battle/useActiveBattle';
import { BattleSetupForm, type BattleSetupValues } from '../expedition/BattleSetupForm';
import { MANUAL_BOSS } from '../expedition/BossSelect';
import { ReplaceBattleDialog } from '../expedition/ExpeditionSetupPage';
import { ActiveBattleWarning } from './ActiveBattleWarning';
import { QuestSelect } from './QuestSelectPage';
import { useBattleMark } from './useBattleMark';

/** Параметр адреса: номер задания или `free` — «Продолжить без задания». */
const FREE = 'free';

/** Шаги перед стартом: предупреждение об идущих боях, затем замена незаконченного боя. */
type Step = 'WARNING' | 'REPLACE';

/**
 * Подготовка к бою кампании (`battle.md` §9): выбор задания, затем экран подготовки экспедиции с префиллом
 * из `battle-setup`. Идущие бои только предупреждают — старт не блокируется.
 */
export function CampaignBattleSetupPage() {
  const campaignId = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const questParam = params.get('quest');
  const questNumber = questParam !== null && questParam !== FREE ? Number(questParam) : undefined;
  const setup = useGetBattleSetup(campaignId, questNumber === undefined ? undefined : { questNumber }, {
    // gcTime 0: подготовка прошлого боя (другая глава, progressSeq) не показывается из кэша
    query: { retry: false, staleTime: 0, gcTime: 0 },
  });
  const sheet = useGetCampaign(campaignId, { query: { retry: false } });
  const catalog = useBossCatalog();

  const back = (
    <Anchor component={Link} to={`/campaigns/${campaignId}`} size="sm" data-testid="campaign-battle-back">
      {ru.progression.backToSheet}
    </Anchor>
  );

  if (setup.isError) {
    const error = setup.error;
    const pending = error instanceof ApiError && error.code === 'CHAPTER_TRANSITION_PENDING';
    return (
      <Stack gap="md" data-testid="page-campaign-battle-new">
        {back}
        <Alert color="red" data-testid="campaign-battle-error">
          {error instanceof ApiError && error.detail !== undefined ? error.detail : ru.progression.setupError}
        </Alert>
        {pending && (
          <Button component={Link} to={`/campaigns/${campaignId}/transition`} data-testid="campaign-battle-to-transition">
            {ru.progression.toTransition}
          </Button>
        )}
      </Stack>
    );
  }
  if (setup.data === undefined || sheet.data === undefined || catalog.bosses === undefined) {
    return (
      <Stack gap="md" data-testid="page-campaign-battle-new">
        {back}
        <Loader />
      </Stack>
    );
  }

  // Выбор задания — если бой не предопределён (пролог, глава 11) и задание ещё не выбрано
  if (questParam === null && setup.data.forcedBoss === null) {
    return (
      <Stack gap="md" data-testid="page-campaign-battle-new">
        {back}
        <QuestSelect
          quests={setup.data.openQuests}
          onSelect={(number) => setParams({ quest: number === null ? FREE : String(number) })}
        />
      </Stack>
    );
  }

  return (
    <SetupForm
      key={`${questParam ?? ''}-${setup.data.progressSeq}`}
      setup={setup.data}
      campaignName={sheet.data.name}
      questNumber={questNumber ?? null}
      back={back}
      onOtherQuest={() => setParams({})}
    />
  );
}

interface SetupFormProps {
  setup: BattleSetup;
  campaignName: string;
  questNumber: number | null;
  back: ReactNode;
  onOtherQuest: () => void;
}

function SetupForm({ setup, campaignName, questNumber, back, onOtherQuest }: SetupFormProps) {
  const navigate = useNavigate();
  const active = useActiveBattle();
  const marks = useBattleMark();
  const [pending, setPending] = useState<{ battle: LocalBattle; step: Step } | null>(null);
  const quest = setup.openQuests.find((item) => item.number === questNumber);
  const title =
    setup.purpose === 'QUEST' && quest !== undefined
      ? ru.progression.quest(quest.number, quest.name)
      : ru.progression.purpose[setup.purpose === 'QUEST' ? 'FREE' : setup.purpose];
  const difficulty: Difficulty = isDifficulty(setup.difficulty) ? setup.difficulty : 0;

  const begin = (battle: LocalBattle) => {
    const replaced = active.getBattle();
    if (replaced !== null) marks.abandon(replaced);
    active.start(battle);
    marks.mark(battle);
    navigate('/battle');
  };

  // Предупреждение об идущих боях → подтверждение замены незаконченного боя → старт
  const proceed = (battle: LocalBattle, from: Step | null) => {
    if (from === null && setup.activeBattles.length > 0) {
      setPending({ battle, step: 'WARNING' });
    } else if (from !== 'REPLACE' && active.hasUnfinished) {
      setPending({ battle, step: 'REPLACE' });
    } else {
      setPending(null);
      begin(battle);
    }
  };

  const start = ({ boss, difficulty: level, hunterCount, toughnessPerHunter, stanceChange }: BattleSetupValues) => {
    const battle = createLocalBattle({
      id: randomId(),
      mode: 'CAMPAIGN',
      campaign: {
        id: setup.campaignId,
        name: campaignName,
        chapter: setup.chapter,
        progressSeq: setup.progressSeq,
        purpose: setup.purpose,
        questNumber: setup.purpose === 'QUEST' ? questNumber : null,
        startMarked: false,
      },
      boss: boss === undefined ? null : toBattleBoss(boss),
      difficulty: level,
      stances: boss === undefined ? [] : bossStances(boss, level),
      params: { hunterCount, toughnessPerHunter, stanceChange },
      now: new Date().toISOString(),
    });
    proceed(battle, null);
  };

  return (
    <Stack gap="md" data-testid="page-campaign-battle-new">
      {back}
      <div>
        <Title order={2}>{ru.progression.setupTitle}</Title>
        <Text fw={600} data-testid="campaign-battle-purpose" data-purpose={setup.purpose}>
          {title}
        </Text>
        {setup.forcedBoss !== null && (
          <Text size="sm" c="dimmed" data-testid="campaign-battle-forced-boss">
            {ru.progression.forcedBoss(setup.forcedBoss.name)}
          </Text>
        )}
      </div>

      <BattleSetupForm
        testIdPrefix="campaign-battle"
        initialBoss={setup.boss?.code ?? MANUAL_BOSS}
        initialDifficulty={difficulty}
        initialHunters={String(setup.hunterCount)}
        bossLocked={setup.forcedBoss !== null}
        onStart={start}
      />
      {setup.forcedBoss === null && (
        <Button variant="subtle" onClick={onOtherQuest} data-testid="campaign-battle-other-quest">
          {ru.progression.otherQuest}
        </Button>
      )}

      <ActiveBattleWarning
        battles={setup.activeBattles}
        opened={pending?.step === 'WARNING'}
        onCancel={() => setPending(null)}
        onStart={() => pending !== null && proceed(pending.battle, 'WARNING')}
      />
      <ReplaceBattleDialog
        opened={pending?.step === 'REPLACE'}
        onCancel={() => setPending(null)}
        onConfirm={() => pending !== null && proceed(pending.battle, 'REPLACE')}
        testIdPrefix="campaign-battle"
      />
    </Stack>
  );
}
