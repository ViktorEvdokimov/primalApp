import { ApiError } from '../../api/errors';
import { ru } from '../../shared/i18n/ru';

/** Правила как на сервере (doc/api.md §3): проверяются до отправки, сервер проверяет их снова. */
export const LOGIN_MIN = 3;
export const LOGIN_MAX = 32;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

const LOGIN = new RegExp(`^[a-z0-9._-]{${LOGIN_MIN},${LOGIN_MAX}}$`);

/** Логин — поле свободного ввода: латиница, цифры, «.», «_», «-», 3–32 символа; регистр не важен. */
export function loginError(login: string): string | undefined {
  if (login.trim() === '') return ru.auth.errors.required;
  return LOGIN.test(login.trim().toLowerCase()) ? undefined : ru.auth.errors.login;
}

export function passwordError(password: string): string | undefined {
  if (password === '') return ru.auth.errors.required;
  return password.length < PASSWORD_MIN || password.length > PASSWORD_MAX ? ru.auth.errors.password : undefined;
}

export function repeatError(password: string, repeat: string): string | undefined {
  return password === repeat ? undefined : ru.auth.errors.passwordMismatch;
}

export function requiredError(value: string): string | undefined {
  return value.trim() === '' ? ru.auth.errors.required : undefined;
}

/** Общее сообщение об ошибке входа, регистрации или смены пароля. */
export function authErrorText(error: unknown): string {
  if (!(error instanceof ApiError)) return ru.errors.unknown;
  switch (error.code) {
    case 'NETWORK_ERROR':
      return ru.errors.network;
    case 'INVALID_CREDENTIALS':
      return error.detail ?? ru.auth.errors.invalidCredentials;
    case 'RATE_LIMITED': {
      const seconds = error.problem?.retryAfter;
      return ru.auth.errors.rateLimited(typeof seconds === 'number' ? seconds : 60);
    }
    case 'VALIDATION_FAILED':
      return ru.auth.errors.fields;
    default:
      return error.detail ?? ru.errors.unknown;
  }
}
