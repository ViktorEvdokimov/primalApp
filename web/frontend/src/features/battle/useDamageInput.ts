import { useCallback, useEffect, useRef, useState } from 'react';

/** Режимы ввода урона — автомат InputMode из app (doc/battle.md §8, app/doc/behavior.md §2.2). */
export type InputMode = 'NONE' | 'QUICK_BUTTON' | 'MANUAL';

/** Урон кнопок +N применяется, если 2 секунды не нажимать кнопки. */
export const QUICK_DAMAGE_DELAY_MS = 2000;

export interface DamageInputState {
  mode: InputMode;
  text: string; // значение поля «Ввести урон»
  pending: number; // накопленный ввод
  timerRunning: boolean;
}

const EMPTY: DamageInputState = { mode: 'NONE', text: '', pending: 0, timerRunning: false };

/** Число из поля: целое со знаком; иначе 0, как `toIntOrNull() ?: 0` в app. */
function parseDamage(text: string): number {
  const trimmed = text.trim();
  return /^-?\d+$/.test(trimmed) ? Number(trimmed) : 0;
}

/**
 * Ввод урона. `onCommit` получает урон при OK, Enter, «Применить урон сейчас» и по таймеру кнопок +N;
 * 0 не применяется. «Закончить раунд» забирает накопленный ввод через `take`.
 */
export function useDamageInput(onCommit: (amount: number) => void) {
  const [state, setState] = useState<DamageInputState>(EMPTY);
  const current = useRef(state);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commitRef = useRef(onCommit);

  useEffect(() => {
    commitRef.current = onCommit;
  }, [onCommit]);

  const update = useCallback((next: DamageInputState) => {
    current.current = next;
    setState(next);
  }, []);

  const stopTimer = useCallback(() => {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }, []);

  useEffect(() => stopTimer, [stopTimer]);

  /** Сбрасывает ввод и возвращает накопленный урон. */
  const take = useCallback((): number => {
    stopTimer();
    const amount = current.current.pending;
    update(EMPTY);
    return amount;
  }, [stopTimer, update]);

  const commit = useCallback(() => {
    const amount = take();
    if (amount !== 0) commitRef.current(amount);
  }, [take]);

  /** Кнопки +1/+5/+10/+50: в режиме MANUAL прибавляют без таймера, иначе (пере)запускают таймер 2 с. */
  const pressQuick = useCallback(
    (amount: number) => {
      const before = current.current;
      const pending = before.pending + amount;
      stopTimer();
      if (before.mode === 'MANUAL') {
        update({ mode: 'MANUAL', text: String(pending), pending, timerRunning: false });
        return;
      }
      update({ mode: 'QUICK_BUTTON', text: String(pending), pending, timerRunning: true });
      timer.current = setTimeout(commit, QUICK_DAMAGE_DELAY_MS);
    },
    [commit, stopTimer, update],
  );

  /** Ввод в поле: режим MANUAL, таймер останавливается. */
  const changeText = useCallback(
    (text: string) => {
      stopTimer();
      update({ mode: 'MANUAL', text, pending: parseDamage(text), timerRunning: false });
    },
    [stopTimer, update],
  );

  /** Фокус поля во время таймера: таймер останавливается, значение сохраняется. */
  const focusField = useCallback(() => {
    if (current.current.mode !== 'QUICK_BUTTON') return;
    stopTimer();
    update({ ...current.current, mode: 'MANUAL', timerRunning: false });
  }, [stopTimer, update]);

  /** «±»: на цифровой клавиатуре телефона нет минуса, а отрицательный урон нужен (D-3). */
  const toggleSign = useCallback(() => {
    const text = current.current.text.trim();
    const next = text.startsWith('-') ? text.slice(1) : `-${text}`;
    changeText(next);
  }, [changeText]);

  const cancel = useCallback(() => {
    take();
  }, [take]);

  return { ...state, pressQuick, changeText, focusField, toggleSign, commit, cancel, take };
}
