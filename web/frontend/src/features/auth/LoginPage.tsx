import { Alert, Button, PasswordInput, SegmentedControl, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError, fieldErrors } from '../../api/errors';
import { useLogin, useRegister } from '../../api/generated/auth/auth';
import type { MeResponse, SignInResponse } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { safeNext } from '../../shared/routing';
import { authErrorText, PASSWORD_MAX, passwordError, phoneError, repeatError, requiredError } from './credentials';
import { ME_QUERY_KEY } from './useMe';

type Mode = 'login' | 'register';

type Errors = Partial<Record<'phone' | 'password' | 'repeat' | 'displayName', string>>;

/** Ошибки полей без `undefined`: пустой объект — всё верно. */
function compact(errors: Errors): Errors {
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message !== undefined));
}

/**
 * Вход и регистрация по номеру телефона и паролю (doc/api.md §3): номер — логин. Браузер запоминается: гость
 * по ссылке-приглашению, войдя, продолжает с тем же устройством. `?mode=register` открывает регистрацию.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'register' ? 'register' : 'login');
  const [error, setError] = useState<string | null>(null);

  const signedIn = (result: SignInResponse) => {
    const me: MeResponse = { kind: 'USER', user: result.user, device: { id: result.device.id, displayName: null } };
    queryClient.setQueryData(ME_QUERY_KEY, me);
    navigate(next, { replace: true });
  };

  return (
    <Stack gap="md" data-testid="page-login">
      <Title order={2}>{mode === 'login' ? ru.auth.modeLogin : ru.auth.modeRegister}</Title>
      <SegmentedControl
        fullWidth
        value={mode}
        onChange={(value) => {
          setError(null);
          setMode(value === 'register' ? 'register' : 'login');
        }}
        data={[
          { value: 'login', label: ru.auth.modeLogin },
          { value: 'register', label: ru.auth.modeRegister },
        ]}
        data-testid="login-mode"
      />
      <Text c="dimmed" size="sm">
        {ru.auth.intro}
      </Text>
      {error !== null && (
        <Alert color="red" data-testid="login-error">
          {error}
        </Alert>
      )}
      {mode === 'login' ? (
        <LoginForm onSignedIn={signedIn} onError={setError} />
      ) : (
        <RegisterForm onSignedIn={signedIn} onError={setError} />
      )}
    </Stack>
  );
}

interface FormProps {
  onSignedIn: (result: SignInResponse) => void;
  onError: (message: string | null) => void;
}

function LoginForm({ onSignedIn, onError }: FormProps) {
  const login = useLogin();
  const [values, setValues] = useState({ phone: '', password: '' });
  const [errors, setErrors] = useState<Errors>({});

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = compact({ phone: phoneError(values.phone), password: requiredError(values.password) });
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    onError(null);
    try {
      onSignedIn(await login.mutateAsync({ data: { phone: values.phone.trim(), password: values.password } }));
    } catch (cause) {
      setErrors(fieldErrors(cause));
      onError(authErrorText(cause));
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <Stack gap="md">
        <TextInput
          label={ru.auth.phone}
          placeholder={ru.auth.phonePlaceholder}
          type="tel"
          autoComplete="username tel"
          maxLength={24}
          value={values.phone}
          error={errors.phone}
          onChange={(event) => setValues({ ...values, phone: event.currentTarget.value })}
          data-testid="login-phone"
        />
        <PasswordInput
          label={ru.auth.password}
          autoComplete="current-password"
          value={values.password}
          error={errors.password}
          onChange={(event) => setValues({ ...values, password: event.currentTarget.value })}
          data-testid="login-password"
        />
        <Button type="submit" size="md" loading={login.isPending} data-testid="login-submit">
          {ru.auth.signIn}
        </Button>
      </Stack>
    </form>
  );
}

function RegisterForm({ onSignedIn, onError }: FormProps) {
  const register = useRegister();
  const [values, setValues] = useState({ phone: '', password: '', repeat: '', displayName: '' });
  const [errors, setErrors] = useState<Errors>({});
  const set = (field: keyof typeof values) => (event: { currentTarget: { value: string } }) =>
    setValues({ ...values, [field]: event.currentTarget.value });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = compact({
      phone: phoneError(values.phone),
      password: passwordError(values.password),
      repeat: repeatError(values.password, values.repeat),
      displayName: requiredError(values.displayName),
    });
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    onError(null);
    try {
      const result = await register.mutateAsync({
        data: { phone: values.phone.trim(), password: values.password, displayName: values.displayName.trim() },
      });
      onSignedIn(result);
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'PHONE_TAKEN') {
        setErrors({ phone: cause.detail ?? ru.auth.errors.phone });
        onError(null);
        return;
      }
      setErrors(fieldErrors(cause));
      onError(authErrorText(cause));
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <Stack gap="md">
        <TextInput
          label={ru.auth.phone}
          description={ru.auth.phoneHint}
          placeholder={ru.auth.phonePlaceholder}
          type="tel"
          autoComplete="username tel"
          maxLength={24}
          value={values.phone}
          error={errors.phone}
          onChange={set('phone')}
          data-testid="register-phone"
        />
        <PasswordInput
          label={ru.auth.password}
          description={ru.auth.passwordHint}
          autoComplete="new-password"
          maxLength={PASSWORD_MAX}
          value={values.password}
          error={errors.password}
          onChange={set('password')}
          data-testid="register-password"
        />
        <PasswordInput
          label={ru.auth.passwordRepeat}
          autoComplete="new-password"
          maxLength={PASSWORD_MAX}
          value={values.repeat}
          error={errors.repeat}
          onChange={set('repeat')}
          data-testid="register-password-repeat"
        />
        <TextInput
          label={ru.auth.name}
          description={ru.auth.nameHint}
          autoComplete="nickname"
          maxLength={60}
          value={values.displayName}
          error={errors.displayName}
          onChange={set('displayName')}
          data-testid="register-name"
        />
        <Button type="submit" size="md" loading={register.isPending} data-testid="register-submit">
          {ru.auth.register}
        </Button>
      </Stack>
    </form>
  );
}
