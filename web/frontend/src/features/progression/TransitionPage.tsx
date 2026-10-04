import { Alert, Anchor, Button, Group, Loader, Stack, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { keepPreviousData, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { getGetCampaignQueryKey, getListCampaignsQueryKey } from '../../api/generated/campaigns/campaigns';
import { submitChapterTransition, useGetChapterTransition } from '../../api/generated/chapters/chapters';
import type { TransitionPreview, TransitionRequestAction } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { errorMessage } from '../campaign/useCampaignSheet';
import { ConfirmConsequencesDialog } from './ConfirmConsequencesDialog';
import { DecisionDialog } from './DecisionDialog';
import { RewardsView } from './RewardsView';

/**
 * Переход главы (`api.md` §8): сначала решение главы (окно закрывается только ответом), затем награды главы с
 * правилами и сообщениями; «Принять» или «Отклонить» с подтверждением последствий.
 */
export function TransitionPage() {
  const campaignId = Number(useParams().id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [decisions, setDecisions] = useState<Record<string, string>>({});
  const [rejecting, setRejecting] = useState(false);
  const [sending, setSending] = useState(false);
  const answers = Object.entries(decisions).map(([code, option]) => `${code}:${option}`);
  const preview = useGetChapterTransition(campaignId, answers.length === 0 ? undefined : { decision: answers }, {
    // gcTime 0: прошлый переход (другая глава) не показывается из кэша, пока грузится новый
    query: { retry: false, staleTime: 0, gcTime: 0, placeholderData: keepPreviousData },
  });

  const back = (
    <Anchor component={Link} to={`/campaigns/${campaignId}`} size="sm" data-testid="transition-back">
      {ru.progression.backToSheet}
    </Anchor>
  );

  const submit = async (action: TransitionRequestAction, data: TransitionPreview) => {
    setSending(true);
    try {
      const sheet = await submitChapterTransition(campaignId, {
        action,
        decisions: action === 'ACCEPT' ? decisions : null,
        expectedVersion: data.version,
      });
      queryClient.setQueryData(getGetCampaignQueryKey(campaignId), sheet);
      void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
      navigate(`/campaigns/${campaignId}`, { replace: true });
    } catch (error) {
      // Кампанию изменили после превью: список последствий мог измениться — показываем новый
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
        notifications.show({ color: 'yellow', message: ru.sheet.conflict });
        void preview.refetch();
      } else {
        notifications.show({ color: 'red', message: errorMessage(error) });
      }
    } finally {
      setSending(false);
    }
  };

  if (preview.isError && preview.data === undefined) {
    const missing = preview.error instanceof ApiError && preview.error.code === 'CAMPAIGN_CHANGED';
    return (
      <Stack gap="md" data-testid="page-campaign-transition">
        {back}
        <Alert color={missing ? 'yellow' : 'red'} data-testid="transition-error">
          {missing ? ru.progression.transitionMissing : errorMessage(preview.error)}
        </Alert>
      </Stack>
    );
  }
  if (preview.data === undefined) {
    return (
      <Stack gap="md" data-testid="page-campaign-transition">
        {back}
        <Loader />
      </Stack>
    );
  }

  const data = preview.data;
  // Ответ учитывается сразу, не дожидаясь нового превью
  const unanswered = data.decisions.find((decision) => !(decision.code in decisions)) ?? null;
  const lines = [
    ...(data.forgeLevelUp ? [ru.progression.forge] : []),
    ...(data.labLevelUp ? [ru.progression.lab] : []),
    ...(data.hunterKitUpgrade ? [ru.progression.kit] : []),
    ...(data.finalBattle === null ? [] : [ru.progression.finalBattle(data.finalBattle.name)]),
    ...data.messages,
  ];

  return (
    <Stack gap="md" data-testid="page-campaign-transition" data-to-chapter={data.toChapter}>
      {back}
      <Title order={2} data-testid="transition-title">
        {ru.progression.transitionTitle(data.toChapter)}
      </Title>
      <RewardsView
        lines={lines}
        perHunter={data.perHunter}
        openQuests={data.openQuests}
        expireQuests={data.expireQuests.filter((quest) => quest.wasOpen)}
        achievements={data.achievements}
        rewardCards={data.rewardCards}
        rules={data.rules}
      />
      <Group grow>
        <Button
          loading={sending}
          disabled={!data.decisionsComplete || preview.isFetching}
          onClick={() => void submit('ACCEPT', data)}
          data-testid="transition-accept"
        >
          {ru.progression.accept}
        </Button>
        <Button
          variant="subtle"
          color="red"
          disabled={sending}
          onClick={() => setRejecting(true)}
          data-testid="transition-reject"
        >
          {ru.progression.dismiss}
        </Button>
      </Group>

      <DecisionDialog
        decision={unanswered}
        onAnswer={(code, option) => setDecisions((current) => ({ ...current, [code]: option }))}
      />
      <ConfirmConsequencesDialog
        opened={rejecting}
        title={ru.progression.rejectTitle(data.toChapter)}
        intro={ru.progression.rejectIntro}
        consequences={data.rejectConsequences}
        loading={sending}
        onCancel={() => setRejecting(false)}
        onConfirm={() => {
          setRejecting(false);
          void submit('REJECT', data);
        }}
      />
    </Stack>
  );
}
