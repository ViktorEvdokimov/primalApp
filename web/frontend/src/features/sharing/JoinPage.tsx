import { Alert, Anchor, Button, Loader, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { getListCampaignsQueryKey } from '../../api/generated/campaigns/campaigns';
import { joinShareLink, useGetInvitation } from '../../api/generated/sharing/sharing';
import { ru } from '../../shared/i18n/ru';
import { ME_QUERY_KEY, useMe } from '../auth/useMe';
import { errorMessage } from '../campaign/useCampaignSheet';

/**
 * Вход по ссылке-приглашению `/s/:token` (`api.md` §9.2): «Вас пригласили в кампанию…», имя для гостя,
 * `join` — и лист кампании. Токен убирается из адресной строки (`history.replaceState`), чтобы не остаться в
 * истории и закладках.
 */
export function JoinPage() {
  const token = useParams().token ?? '';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { me, isLoading } = useMe();
  const invitation = useGetInvitation(token, { query: { retry: false } });
  const [name, setName] = useState<string | null>(null);
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const signedIn = me?.kind === 'USER';
  const guestName = name ?? (me?.kind === 'GUEST' ? (me.device.displayName ?? '') : '');

  const join = async () => {
    setJoining(true);
    setError(null);
    try {
      const joined = await joinShareLink(token, { displayName: signedIn || guestName.trim() === '' ? null : guestName.trim() });
      // Гость без cookie получил устройство: «кто я» и список кампаний изменились
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
      navigate(`/campaigns/${joined.id}`, { replace: true });
    } catch (failure) {
      setError(failure instanceof ApiError && failure.code === 'SHARE_LINK_INVALID' ? ru.sharing.invalid : errorMessage(failure));
      setJoining(false);
    }
  };

  const invalid = invitation.error instanceof ApiError && invitation.error.code === 'SHARE_LINK_INVALID';

  return (
    <Stack gap="md" data-testid="page-join">
      <Title order={2}>{ru.sharing.joinTitle}</Title>
      {(invitation.isPending || isLoading) && !invitation.isError && <Loader />}
      {invitation.isError && (
        <Alert color="red" data-testid="join-invalid">
          {invalid ? ru.sharing.invalid : errorMessage(invitation.error)}
        </Alert>
      )}
      {invitation.data !== undefined && !isLoading && (
        <Stack gap="md">
          <div>
            <Text size="lg" fw={600} data-testid="join-campaign">
              {ru.sharing.invited(invitation.data.name)}
            </Text>
            <Text size="sm" c="dimmed">
              {ru.sharing.owner(invitation.data.ownerName)}
            </Text>
          </div>
          {!signedIn && (
            <TextInput
              label={ru.sharing.guestName}
              description={ru.sharing.guestNameHint}
              value={guestName}
              maxLength={60}
              onChange={(event) => setName(event.currentTarget.value)}
              data-testid="join-name"
            />
          )}
          {error !== null && (
            <Alert color="red" data-testid="join-error">
              {error}
            </Alert>
          )}
          <Button size="lg" loading={joining} onClick={() => void join()} data-testid="join-submit">
            {ru.sharing.join}
          </Button>
          {!signedIn && (
            <Anchor component={Link} to={`/login?next=${encodeURIComponent(`/s/${token}`)}`} size="sm" data-testid="join-login">
              {ru.sharing.loginHint}
            </Anchor>
          )}
        </Stack>
      )}
    </Stack>
  );
}
