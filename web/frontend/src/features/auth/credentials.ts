import { ApiError } from '../../api/errors';
import { ru } from '../../shared/i18n/ru';

/** Правила как на сервере (doc/api.md §3): проверяются до отправки, сервер проверяет их снова. */
const PHONE_SEPARATORS = /[\s().-]/g;
const PHONE = /^\+?\d{10,15}$/;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

/** Номер телефона — логин: пробелы, скобки, точки и дефисы не важны, цифр 10–15, можно с «+». */
export function phoneError(phone: string): string | undefined {
  if (phone.trim() === '') return ru.auth.errors.required;
  return PHONE.test(phone.replace(PHONE_SEPARATORS, '')) ? undefined : ru.auth.errors.phone;
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
