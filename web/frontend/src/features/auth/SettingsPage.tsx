import { Alert, Button, Divider, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError, fieldErrors } from '../../api/errors';
import {
  getListDevicesQueryKey,
  useChangePassword,
  useListDevices,
  useLogout,
  useRevokeDevice,
  useRevokeOtherDevices,
  useUpdateMe,
  useUpdatePhone,
} from '../../api/generated/auth/auth';
import type { DeviceSummary, MeResponse, UserView } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { authErrorText, PASSWORD_MAX, passwordError, phoneError, repeatError } from './credentials';
import { DevicesList } from './DevicesList';
import { defaultNameOf, ME_QUERY_KEY, useMe } from './useMe';

/** Настройки: имя, телефон, пароль, устройства, выход (doc/api.md §3). Экран защищён — `me` уже загружен. */
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
        <Text data-testid="settings-account">
          {me.user.phone === null ? ru.settings.accountWithoutPhone : ru.settings.account(me.user.phone)}
        </Text>
      ) : (
        <Stack gap="xs">
          <Text c="dimmed">{ru.settings.guest}</Text>
          <Button component={Link} to="/login" variant="light" data-testid="settings-login">
            {ru.settings.loginOrRegister}
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

      {me.user !== null && (
        <>
          <PhoneSection user={me.user} onSaved={setNotice} />
          <Divider />
          <PasswordSection user={me.user} onSaved={setNotice} />
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

interface SectionProps {
  user: UserView;
  onSaved: (notice: string) => void;
}

/** Номер телефона — логин: после смены вход по новому номеру. Удалить номер нельзя. */
function PhoneSection({ user, onSaved }: SectionProps) {
  const queryClient = useQueryClient();
  const updatePhone = useUpdatePhone();
  const [phone, setPhone] = useState(user.phone ?? '');
  const [error, setError] = useState<string | undefined>(undefined);

  const save = async () => {
    const invalid = phoneError(phone);
    setError(invalid);
    if (invalid !== undefined) return;
    try {
      const updated = await updatePhone.mutateAsync({ data: { phone: phone.trim() } });
      queryClient.setQueryData(ME_QUERY_KEY, updated);
      setPhone(updated.user?.phone ?? '');
      onSaved(ru.settings.phoneSaved);
    } catch (cause) {
      const taken = cause instanceof ApiError && cause.code === 'PHONE_TAKEN' ? cause.detail : undefined;
      setError(taken ?? fieldErrors(cause).phone ?? authErrorText(cause));
    }
  };

  return (
    <Stack gap="xs">
      <TextInput
        label={ru.settings.phone}
        description={ru.settings.phoneHint}
        placeholder={ru.auth.phonePlaceholder}
        type="tel"
        autoComplete="tel"
        maxLength={24}
        value={phone}
        error={error}
        onChange={(event) => setPhone(event.currentTarget.value)}
        data-testid="settings-phone"
      />
      <Button variant="light" loading={updatePhone.isPending} onClick={() => void save()} data-testid="settings-phone-save">
        {ru.settings.phoneSave}
      </Button>
    </Stack>
  );
}

type PasswordErrors = Partial<Record<'currentPassword' | 'newPassword' | 'repeat' | 'form', string>>;

/** Смена пароля; у аккаунта без пароля (создан по почте) текущий не спрашивается. */
function PasswordSection({ user, onSaved }: SectionProps) {
  const queryClient = useQueryClient();
  const changePassword = useChangePassword();
  const [values, setValues] = useState({ current: '', next: '', repeat: '' });
  const [errors, setErrors] = useState<PasswordErrors>({});

  const save = async () => {
    const found: PasswordErrors = {
      currentPassword: user.passwordSet && values.current === '' ? ru.auth.errors.required : undefined,
      newPassword: passwordError(values.next),
      repeat: repeatError(values.next, values.repeat),
    };
    const invalid = Object.values(found).some((message) => message !== undefined);
    setErrors(invalid ? found : {});
    if (invalid) return;
    try {
      await changePassword.mutateAsync({
        data: { currentPassword: user.passwordSet ? values.current : null, newPassword: values.next },
      });
      setValues({ current: '', next: '', repeat: '' });
      if (!user.passwordSet) await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      onSaved(ru.settings.passwordSaved);
    } catch (cause) {
      const fields = fieldErrors(cause);
      setErrors({ ...fields, form: Object.keys(fields).length > 0 ? undefined : authErrorText(cause) });
    }
  };

  return (
    <Stack gap="xs" data-testid="settings-password">
      <Title order={3}>{ru.settings.password}</Title>
      {!user.passwordSet && (
        <Text size="sm" c="dimmed" data-testid="settings-password-not-set">
          {ru.settings.passwordNotSet}
        </Text>
      )}
      {errors.form !== undefined && (
        <Alert color="red" data-testid="settings-password-error">
          {errors.form}
        </Alert>
      )}
      {user.passwordSet && (
        <PasswordInput
          label={ru.settings.currentPassword}
          autoComplete="current-password"
          value={values.current}
          error={errors.currentPassword}
          onChange={(event) => setValues({ ...values, current: event.currentTarget.value })}
          data-testid="settings-password-current"
        />
      )}
      <PasswordInput
        label={ru.settings.newPassword}
        description={ru.auth.passwordHint}
        autoComplete="new-password"
        maxLength={PASSWORD_MAX}
        value={values.next}
        error={errors.newPassword}
        onChange={(event) => setValues({ ...values, next: event.currentTarget.value })}
        data-testid="settings-password-new"
      />
      <PasswordInput
        label={ru.settings.newPasswordRepeat}
        autoComplete="new-password"
        maxLength={PASSWORD_MAX}
        value={values.repeat}
        error={errors.repeat}
        onChange={(event) => setValues({ ...values, repeat: event.currentTarget.value })}
        data-testid="settings-password-repeat"
      />
      <Button variant="light" loading={changePassword.isPending} onClick={() => void save()} data-testid="settings-password-save">
        {user.passwordSet ? ru.settings.passwordSave : ru.settings.passwordSet}
      </Button>
    </Stack>
  );
}
