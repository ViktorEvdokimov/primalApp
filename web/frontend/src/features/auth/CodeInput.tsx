import { PinInput } from '@mantine/core';
import { ru } from '../../shared/i18n/ru';

export const CODE_LENGTH = 6;

interface CodeInputProps {
  value: string;
  onChange: (value: string) => void;
  /** Все 6 цифр введены — можно проверять код. */
  onComplete: (value: string) => void;
  error: boolean;
  disabled: boolean;
}

/**
 * Шесть ячеек кода: цифровая клавиатура, вставка кода целиком и автоподстановка из письма или SMS
 * (`autocomplete="one-time-code"`).
 */
export function CodeInput({ value, onChange, onComplete, error, disabled }: CodeInputProps) {
  return (
    <PinInput
      length={CODE_LENGTH}
      type="number"
      oneTimeCode
      autoFocus
      size="lg"
      value={value}
      onChange={onChange}
      onComplete={onComplete}
      error={error}
      disabled={disabled}
      aria-label={ru.auth.code}
      getInputProps={(index) => ({ 'data-testid': `login-code-${index}` })}
      data-testid="login-code"
    />
  );
}
