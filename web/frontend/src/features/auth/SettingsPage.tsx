import { Alert, Button, Divider, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  getListDevicesQueryKey,
  useListDevices,
  useLogout,
  useRevokeDevice,
  useRevokeOtherDevices,
  useUpdateMe,
} from '../../api/generated/auth/auth';
import type { DeviceSummary, MeResponse } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { DevicesList } from './DevicesList';
import { defaultNameOf, ME_QUERY_KEY, useMe } from './useMe';

/** Настройки: имя, устройства, выход (doc/api.md §3). Экран защищён — `me` уже загружен. */
export function SettingsPage() {
  const { me } = useMe();
  if (me === null) return null;
  return <Settings me={me} />;
}

function Settings({ me }: { me: MeResponse }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isUser = me.kind === 'USER';
  const [name, setName] = useState((isUser ? me.user?.displayName : me.device.displayName) ?? '');
  const [notice, setNotice] = useState<string | null>(null);
  const updateMe = useUpdateMe();
  const logout = useLogout();
  const revokeDevice = useRevokeDevice();
  const revokeOthers = useRevokeOtherDevices();
  const devices = useListDevices({ query: { enabled: isUser } });

  /** Устройство больше не действует: кэш сбрасывается, дальше — вход. */
  const signedOut = (path: string) => {
    queryClient.clear();
    queryClient.setQueryData(ME_QUERY_KEY, null);
    navigate(path, { replace: true });
  };

  const saveName = async () => {
    const updated = await updateMe.mutateAsync({ data: { displayName: name } });
    queryClient.setQueryData(ME_QUERY_KEY, updated);
    setNotice(ru.settings.saved);
  };

  const revoke = async (device: DeviceSummary) => {
    await revokeDevice.mutateAsync({ id: device.id });
    if (device.current) signedOut('/login');
    else await queryClient.invalidateQueries({ queryKey: getListDevicesQueryKey() });
  };

  const revokeAllOthers = async () => {
    await revokeOthers.mutateAsync();
    await queryClient.invalidateQueries({ queryKey: getListDevicesQueryKey() });
    setNotice(ru.settings.revokedOthers);
  };

  return (
    <Stack gap="md" data-testid="page-settings">
      <Title order={2}>{ru.pages.settings}</Title>
      {me.user !== null ? (
        <Text data-testid="settings-account">{ru.settings.account(me.user.email)}</Text>
      ) : (
        <Stack gap="xs">
          <Text c="dimmed">{ru.settings.guest}</Text>
          <Button component={Link} to="/login" variant="light" data-testid="settings-login">
            {ru.settings.loginByEmail}
          </Button>
        </Stack>
      )}
      {notice !== null && (
        <Alert color="green" withCloseButton onClose={() => setNotice(null)} data-testid="settings-notice">
          {notice}
        </Alert>
      )}

      <Stack gap="xs">
        <TextInput
          label={ru.settings.name}
          placeholder={ru.settings.namePlaceholder(defaultNameOf(me))}
          maxLength={60}
          value={name}
          onChange={(event) => setName(event.currentTarget.value)}
          data-testid="settings-name"
        />
        <Button variant="light" loading={updateMe.isPending} onClick={() => void saveName()} data-testid="settings-name-save">
          {ru.settings.save}
        </Button>
      </Stack>

      {isUser && (
        <>
          <Divider />
          <Title order={3}>{ru.settings.devices}</Title>
          <DevicesList
            devices={devices.data ?? []}
            revoking={revokeDevice.isPending}
            onRevoke={(device) => void revoke(device)}
          />
          <Button
            variant="default"
            loading={revokeOthers.isPending}
            onClick={() => void revokeAllOthers()}
            data-testid="settings-revoke-others"
          >
            {ru.settings.revokeOthers}
          </Button>
        </>
      )}

      <Divider />
      <Button
        color="red"
        variant="outline"
        loading={logout.isPending}
        onClick={() => void logout.mutateAsync().then(() => signedOut('/'))}
        data-testid="settings-logout"
      >
        {ru.settings.logout}
      </Button>
    </Stack>
  );
}
