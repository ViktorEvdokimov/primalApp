import { Alert, Button, List, Stack, Text } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';
import { clockTime } from './campaignBattle';
import type { CampaignChanged } from './useResultSubmission';

/** Нет сети: результат сохранён в браузере и отправится снова (`battle.md` §9, п. 6). */
export function NotSentPanel({ onRetry, loading }: { onRetry: () => void; loading: boolean }) {
  return (
    <Alert color="yellow" data-testid="result-not-sent">
      <Stack gap="xs" align="flex-start">
        <Text size="sm">{ru.progression.notSent}</Text>
        <Text size="sm">{ru.errors.network}</Text>
        <Button size="xs" variant="light" loading={loading} onClick={onRetry} data-testid="result-resend">
          {ru.progression.resend}
        </Button>
      </Stack>
    </Alert>
  );
}

/**
 * Кампания изменилась, пока шёл бой (`409 CAMPAIGN_CHANGED`): причины и единственное действие — «Отклонить
 * результат» (записывает бой в историю, кампанию не меняет).
 */
export function CampaignChangedPanel({
  changed,
  onDismiss,
  loading,
}: {
  changed: CampaignChanged;
  onDismiss: () => void;
  loading: boolean;
}) {
  return (
    <Alert color="orange" title={ru.progression.changedTitle} data-testid="result-changed">
      <Stack gap="xs" align="flex-start">
        <List size="sm" spacing={4}>
          {changed.reasons.map((reason) => (
            <List.Item key={reason} data-testid="result-changed-reason">
              {reason}
            </List.Item>
          ))}
        </List>
        {changed.closedAt !== null && <Text size="sm">{ru.progression.closedAt(clockTime(changed.closedAt))}</Text>}
        <Button color="red" size="xs" loading={loading} onClick={onDismiss} data-testid="result-dismiss-changed">
          {ru.progression.dismissResult}
        </Button>
      </Stack>
    </Alert>
  );
}
