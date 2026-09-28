import { Alert, Anchor, Button, Group, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { useRequestCode, useUpdateMe, useVerifyCode } from '../../api/generated/auth/auth';
import type { CodeResponse, MeResponse } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { safeNext } from '../../shared/routing';
import { CODE_LENGTH, CodeInput } from './CodeInput';
import { ME_QUERY_KEY } from './useMe';

type Step = 'email' | 'code' | 'name';

/** Повторно запросить код можно через минуту; время сервера сверяется с часами браузера не больше чем на минуту. */
const MAX_RESEND_DELAY_MS = 60_000;

function problemNumber(error: ApiError, field: string): number {
  const value = error.problem?.[field];
  return typeof value === 'number' ? value : 0;
}

function errorText(error: unknown, step: Step): string {
  if (!(error instanceof ApiError)) return ru.errors.unknown;
  switch (error.code) {
    case 'NETWORK_ERROR':
      return ru.errors.network;
    case 'VALIDATION_FAILED':
      return step === 'email' ? ru.auth.errors.email : ru.auth.errors.codeFormat;
    case 'INVALID_CODE':
      return ru.auth.errors.invalidCode(problemNumber(error, 'attemptsLeft'));
    case 'CODE_EXPIRED':
      return ru.auth.errors.codeExpired;
    case 'RATE_LIMITED':
      return ru.auth.errors.rateLimited(problemNumber(error, 'retryAfter'));
    default:
      return error.detail ?? ru.errors.unknown;
  }
}

/** Обратный отсчёт до повторной отправки кода; запускается из обработчика — без устаревшего «сейчас». */
function useCountdown() {
  const [until, setUntil] = useState(0);
  const [now, setNow] = useState(0);
  useEffect(() => {
    if (until <= now) return;
    const timer = setTimeout(() => setNow(Date.now()), Math.min(1000, until - now));
    return () => clearTimeout(timer);
  }, [until, now]);
  const start = useCallback((milliseconds: number) => {
    const current = Date.now();
    setNow(current);
    setUntil(current + milliseconds);
  }, []);
  return { secondsLeft: Math.max(0, Math.ceil((until - now) / 1000)), start };
}

/** Вход и регистрация по коду из письма (doc/api.md §3): почта → код → имя для нового пользователя. */
export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const queryClient = useQueryClient();
  const requestCode = useRequestCode();
  const verifyCode = useVerifyCode();
  const updateMe = useUpdateMe();

  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [challenge, setChallenge] = useState<CodeResponse | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { secondsLeft, start: startCountdown } = useCountdown();

  const sendCode = async () => {
    setError(null);
    try {
      const response = await requestCode.mutateAsync({ data: { email: email.trim() } });
      const delay = Math.min(MAX_RESEND_DELAY_MS, Math.max(0, Date.parse(response.resendAfter) - Date.now()));
      setChallenge(response);
      setCode('');
      startCountdown(delay);
      setStep('code');
    } catch (cause) {
      setError(errorText(cause, step));
      if (cause instanceof ApiError && cause.code === 'RATE_LIMITED') {
        startCountdown(problemNumber(cause, 'retryAfter') * 1000);
      }
    }
  };

  const submitCode = async (value: string) => {
    if (challenge === null) return;
    if (!new RegExp(`^\\d{${CODE_LENGTH}}$`).test(value)) {
      setError(ru.auth.errors.codeFormat);
      return;
    }
    setError(null);
    try {
      const result = await verifyCode.mutateAsync({ data: { challengeId: challenge.challengeId, code: value } });
      const me: MeResponse = { kind: 'USER', user: result.user, device: { id: result.device.id, displayName: null } };
      queryClient.setQueryData(ME_QUERY_KEY, me);
      if (result.isNewUser) setStep('name');
      else navigate(next, { replace: true });
    } catch (cause) {
      setError(errorText(cause, 'code'));
      setCode('');
      if (cause instanceof ApiError && cause.code === 'CODE_EXPIRED') startCountdown(0);
    }
  };

  const saveName = async () => {
    try {
      const me = await updateMe.mutateAsync({ data: { displayName: name } });
      queryClient.setQueryData(ME_QUERY_KEY, me);
      navigate(next, { replace: true });
    } catch (cause) {
      setError(errorText(cause, 'name'));
    }
  };

  return (
    <Stack gap="md" data-testid="page-login">
      <Title order={2}>{step === 'name' ? ru.auth.nameTitle : ru.pages.login}</Title>
      {error !== null && (
        <Alert color="red" data-testid="login-error">
          {error}
        </Alert>
      )}

      {step === 'email' && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void sendCode();
          }}
        >
          <Stack gap="md">
            <Text c="dimmed">{ru.auth.intro}</Text>
            <TextInput
              label={ru.auth.email}
              placeholder={ru.auth.emailPlaceholder}
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.currentTarget.value)}
              data-testid="login-email"
            />
            <Button type="submit" size="md" loading={requestCode.isPending} data-testid="login-get-code">
              {ru.auth.getCode}
            </Button>
          </Stack>
        </form>
      )}

      {step === 'code' && (
        <Stack gap="md">
          <Text data-testid="login-code-sent">{ru.auth.codeSent(email.trim())}</Text>
          <CodeInput
            value={code}
            onChange={setCode}
            onComplete={(value) => void submitCode(value)}
            error={error !== null}
            disabled={verifyCode.isPending}
          />
          <Button size="md" loading={verifyCode.isPending} onClick={() => void submitCode(code)} data-testid="login-submit">
            {ru.auth.signIn}
          </Button>
          <Group justify="space-between">
            <Anchor
              component="button"
              type="button"
              onClick={() => {
                setError(null);
                setStep('email');
              }}
              data-testid="login-change-email"
            >
              {ru.auth.changeEmail}
            </Anchor>
            <Button
              variant="subtle"
              disabled={secondsLeft > 0}
              loading={requestCode.isPending}
              onClick={() => void sendCode()}
              data-testid="login-resend"
            >
              {ru.auth.resend}
            </Button>
          </Group>
          {secondsLeft > 0 && (
            <Text size="sm" c="dimmed" data-testid="login-resend-timer">
              {ru.auth.resendIn(secondsLeft)}
            </Text>
          )}
        </Stack>
      )}

      {step === 'name' && (
        <Stack gap="md">
          <Text c="dimmed">{ru.auth.nameHint}</Text>
          <TextInput
            label={ru.auth.name}
            maxLength={60}
            autoComplete="nickname"
            value={name}
            onChange={(event) => setName(event.currentTarget.value)}
            data-testid="login-name"
          />
          <Group grow>
            <Button variant="default" onClick={() => navigate(next, { replace: true })} data-testid="login-name-skip">
              {ru.auth.skip}
            </Button>
            <Button loading={updateMe.isPending} onClick={() => void saveName()} data-testid="login-name-save">
              {ru.auth.save}
            </Button>
          </Group>
        </Stack>
      )}
    </Stack>
  );
}
