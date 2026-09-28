import { Alert, Anchor, Badge, Button, Group, Loader, Modal, Stack, Tabs, Text, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { useLiveUpdates } from '../../api/useLiveUpdates';
import { getGetCampaignQueryKey, getListCampaignsQueryKey, useDeleteCampaign } from '../../api/generated/campaigns/campaigns';
import { useDictionaries } from '../../api/generated/catalog/catalog';
import type { CampaignSheet } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useActiveBattle } from '../battle/useActiveBattle';
import { describeActiveBattle } from '../progression/campaignBattle';
import { PendingResultBanner } from '../progression/PendingResultBanner';
import { ShareDialog } from '../sharing/ShareDialog';
import { AchievementsTab } from './AchievementsTab';
import { BattleHistory } from './BattleHistory';
import { BossLabel } from './BossLabel';
import { ChapterControl } from './ChapterControl';
import { HuntersTab } from './HuntersTab';
import { NotesTab } from './NotesTab';
import { QuestsTab } from './QuestsTab';
import { TrophiesTab } from './TrophiesTab';
import { useCampaignSheet, type SheetActions } from './useCampaignSheet';

const TABS = ['hunters', 'quests', 'achievements', 'trophies', 'history', 'notes'] as const;
type Tab = (typeof TABS)[number];

/** Лист кампании — аналог `CampaignSheetScreen` app (behavior.md §7): отряд, задания, достижения, заметки, глава. */
export function CampaignSheetPage() {
  const campaignId = Number(useParams().id);
  const { sheet, ...actions } = useCampaignSheet(campaignId);
  // Изменения других участников приходят сразу (api.md §9.3)
  useLiveUpdates(campaignId);

  if (sheet.isPending) return <Loader data-testid="page-campaign-sheet" />;
  if (sheet.isError) {
    const notFound = sheet.error instanceof ApiError && (sheet.error.status === 404 || sheet.error.status === 403);
    return (
      <Stack gap="md" data-testid="page-campaign-sheet">
        <Alert color="red" data-testid="sheet-error">
          <Group justify="space-between">
            <Text size="sm">{notFound ? ru.sheet.notFound : ru.sheet.loadError}</Text>
            {!notFound && (
              <Button size="xs" variant="light" onClick={() => void sheet.refetch()}>
                {ru.campaigns.retry}
              </Button>
            )}
          </Group>
        </Alert>
        <Anchor component={Link} to="/campaigns">
          {ru.sheet.backToList}
        </Anchor>
      </Stack>
    );
  }
  return <Sheet sheet={sheet.data} actions={actions} />;
}

function Sheet({ sheet, actions }: { sheet: CampaignSheet; actions: SheetActions }) {
  const dictionaries = useDictionaries({ query: { staleTime: 60 * 60 * 1000 } });
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab');
  const tab: Tab = TABS.find((item) => item === requested) ?? 'hunters';

  return (
    <Stack gap="md" data-testid="page-campaign-sheet">
      <Anchor component={Link} to="/campaigns" size="sm" data-testid="sheet-back">
        {ru.sheet.backToList}
      </Anchor>

      <Stack gap={6}>
        <Group justify="space-between" wrap="nowrap">
          <Title order={2} data-testid="sheet-name">
            {sheet.name}
          </Title>
          {sheet.access === 'OWNER' && <ShareButton campaignId={sheet.id} />}
        </Group>
        <Group gap="xs">
          <Badge size="lg" variant="light" data-testid="sheet-chapter" data-chapter={sheet.chapter}>
            {ru.campaigns.chapter(sheet.chapter)}
          </Badge>
          <ChapterControl campaignId={sheet.id} chapter={sheet.chapter} pendingTransition={sheet.pendingTransition} actions={actions} />
        </Group>
        <Group gap="xs">
          <Badge variant="default" data-testid="sheet-forge" data-level={sheet.forgeLevel}>
            {ru.sheet.forge(sheet.forgeLevel)}
          </Badge>
          <Badge variant="default" data-testid="sheet-lab" data-level={sheet.labLevel}>
            {ru.sheet.lab(sheet.labLevel)}
          </Badge>
          <Badge variant="default" data-testid="sheet-difficulty" data-level={sheet.difficulty}>
            {ru.sheet.difficulty(sheet.difficulty)}
          </Badge>
        </Group>
        {sheet.finalBoss !== null && (
          <Group gap={6} data-testid="sheet-final-boss">
            <Text size="sm">{ru.sheet.finalBossLabel}</Text>
            <BossLabel boss={sheet.finalBoss} />
          </Group>
        )}
        {sheet.access === 'LINK' && (
          <Text size="sm" c="dimmed">
            {ru.campaigns.owner(sheet.ownerName)}
          </Text>
        )}
      </Stack>

      <BattleActions sheet={sheet} />

      <Tabs value={tab} onChange={(value) => setParams(value === 'hunters' ? {} : { tab: value ?? 'hunters' }, { replace: true })}>
        <Tabs.List style={{ flexWrap: 'nowrap', overflowX: 'auto', overflowY: 'hidden' }}>
          {TABS.map((item) => (
            <Tabs.Tab key={item} value={item} data-testid={`sheet-tab-${item}`}>
              {ru.sheet.tabs[item]}
            </Tabs.Tab>
          ))}
        </Tabs.List>
        <Tabs.Panel value="hunters" pt="md">
          {dictionaries.data === undefined ? (
            <Loader />
          ) : (
            <HuntersTab campaignId={sheet.id} hunters={sheet.hunters} dictionaries={dictionaries.data} actions={actions} />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="quests" pt="md">
          <QuestsTab campaignId={sheet.id} chapter={sheet.chapter} quests={sheet.quests} actions={actions} />
        </Tabs.Panel>
        <Tabs.Panel value="achievements" pt="md">
          <AchievementsTab campaignId={sheet.id} achievements={sheet.achievements} actions={actions} />
        </Tabs.Panel>
        <Tabs.Panel value="trophies" pt="md">
          <TrophiesTab trophies={sheet.trophies} />
        </Tabs.Panel>
        <Tabs.Panel value="history" pt="md">
          {tab === 'history' && <BattleHistory campaignId={sheet.id} />}
        </Tabs.Panel>
        <Tabs.Panel value="notes" pt="md">
          <NotesTab campaignId={sheet.id} notes={sheet.notes} actions={actions} />
        </Tabs.Panel>
      </Tabs>

      {sheet.access === 'OWNER' && <DeleteCampaign sheet={sheet} actions={actions} />}
    </Stack>
  );
}

/**
 * Бой кампании на листе: результат этого браузера не отправлен, идущие бои других участников, «Начать бой»,
 * «Переход главы» или «Кампания пройдена!».
 */
function BattleActions({ sheet }: { sheet: CampaignSheet }) {
  const active = useActiveBattle();
  return (
    <Stack gap="xs">
      <PendingResultBanner battle={active.battle} campaignId={sheet.id} />
      {sheet.activeBattles.map((battle) => (
        <Alert key={battle.id} color="blue" variant="light" data-testid="sheet-active-battle">
          {ru.progression.activeBattle(describeActiveBattle(battle))}
        </Alert>
      ))}
      {sheet.status === 'COMPLETED' && (
        <Alert color="teal" data-testid="sheet-completed">
          {ru.progression.completedBanner}
        </Alert>
      )}
      {sheet.pendingTransition && (
        <Alert color="orange" data-testid="sheet-pending-transition">
          <Group justify="space-between" gap="xs">
            <Text size="sm">{ru.sheet.pendingTransition}</Text>
            <Button size="xs" component={Link} to={`/campaigns/${sheet.id}/transition`} data-testid="sheet-transition">
              {ru.progression.toTransition}
            </Button>
          </Group>
        </Alert>
      )}
      {sheet.status === 'ACTIVE' && (
        <Button component={Link} to={`/campaigns/${sheet.id}/battle/new`} size="md" data-testid="sheet-start-battle">
          {ru.progression.startBattle}
        </Button>
      )}
    </Stack>
  );
}

/** «Поделиться» — только владельцу: ссылка-приглашение и QR-код. */
function ShareButton({ campaignId }: { campaignId: number }) {
  const [opened, setOpened] = useState(false);
  return (
    <>
      <Button size="xs" variant="light" onClick={() => setOpened(true)} data-testid="sheet-share">
        {ru.sharing.share}
      </Button>
      <ShareDialog campaignId={campaignId} opened={opened} onClose={() => setOpened(false)} />
    </>
  );
}

/** «Удалить кампанию» — только владельцу (`403 OWNER_ONLY` для остальных). */
function DeleteCampaign({ sheet, actions }: { sheet: CampaignSheet; actions: SheetActions }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const remove = useDeleteCampaign();
  const [confirming, setConfirming] = useState(false);

  const confirm = () => {
    remove.mutateAsync({ id: sheet.id }).then(
      () => {
        queryClient.removeQueries({ queryKey: getGetCampaignQueryKey(sheet.id) });
        void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
        void navigate('/campaigns', { replace: true });
      },
      (error: unknown) => {
        setConfirming(false);
        actions.failed(error);
      },
    );
  };

  return (
    <>
      <Group justify="flex-end">
        <Button variant="subtle" color="red" onClick={() => setConfirming(true)} data-testid="sheet-delete">
          {ru.sheet.deleteCampaign}
        </Button>
      </Group>
      <Modal opened={confirming} onClose={() => setConfirming(false)} title={ru.campaigns.deleteTitle} centered>
        <Stack gap="md">
          <Text>{ru.campaigns.deleteText(sheet.name)}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(false)} data-testid="sheet-delete-cancel">
              {ru.campaigns.cancel}
            </Button>
            <Button color="red" loading={remove.isPending} onClick={confirm} data-testid="sheet-delete-confirm">
              {ru.campaigns.delete}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
