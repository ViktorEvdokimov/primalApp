import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QUICK_DAMAGE_DELAY_MS, useDamageInput } from './useDamageInput';

// Сценарии автомата ввода из app/doc/architecture.md §4.1–4.4 (InputMode)

function renderInput() {
  const onCommit = vi.fn<(amount: number) => void>();
  const view = renderHook(() => useDamageInput(onCommit));
  return { ...view, onCommit };
}

describe('Ввод урона', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('Кнопки +1/+5/+10/+50', () => {
    it('копят урон и запускают таймер 2 с', () => {
      // подготовка
      const { result, onCommit } = renderInput();

      // вызов
      act(() => {
        result.current.pressQuick(1);
        result.current.pressQuick(5);
      });

      // проверка
      expect(result.current).toMatchObject({ mode: 'QUICK_BUTTON', pending: 6, text: '6', timerRunning: true });
      expect(onCommit).not.toHaveBeenCalled();
    });

    it('через 2 с без нажатий урон применяется, ввод сбрасывается', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(10));

      // вызов
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS));

      // проверка
      expect(onCommit).toHaveBeenCalledExactlyOnceWith(10);
      expect(result.current).toMatchObject({ mode: 'NONE', pending: 0, text: '', timerRunning: false });
    });

    it('новое нажатие сбрасывает таймер', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(10));
      act(() => vi.advanceTimersByTime(1500));

      // вызов
      act(() => result.current.pressQuick(5));
      act(() => vi.advanceTimersByTime(1500));

      // проверка: с последнего нажатия прошло 1,5 с — урон ещё ждёт
      expect(onCommit).not.toHaveBeenCalled();
      act(() => vi.advanceTimersByTime(500));
      expect(onCommit).toHaveBeenCalledExactlyOnceWith(15);
    });

    it('«Применить урон сейчас» применяет урон, не дожидаясь таймера', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(50));

      // вызов
      act(() => result.current.commit());
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(onCommit).toHaveBeenCalledExactlyOnceWith(50);
    });
  });

  describe('Ручной ввод', () => {
    it('ввод в поле — режим MANUAL без таймера', () => {
      // подготовка
      const { result, onCommit } = renderInput();

      // вызов
      act(() => result.current.changeText('7'));
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(result.current).toMatchObject({ mode: 'MANUAL', pending: 7, timerRunning: false });
      expect(onCommit).not.toHaveBeenCalled();
    });

    it('фокус поля останавливает таймер, сохраняя значение', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(10));

      // вызов
      act(() => result.current.focusField());
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(result.current).toMatchObject({ mode: 'MANUAL', text: '10', pending: 10, timerRunning: false });
      expect(onCommit).not.toHaveBeenCalled();
      act(() => result.current.commit());
      expect(onCommit).toHaveBeenCalledExactlyOnceWith(10);
    });

    it('MANUAL + кнопка — сумма без таймера', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.changeText('7'));

      // вызов
      act(() => result.current.pressQuick(5));
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(result.current).toMatchObject({ mode: 'MANUAL', text: '12', pending: 12, timerRunning: false });
      expect(onCommit).not.toHaveBeenCalled();
    });

    it('отрицательное значение применяется (D-3)', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.changeText('-3'));

      // вызов
      act(() => result.current.commit());

      // проверка
      expect(onCommit).toHaveBeenCalledExactlyOnceWith(-3);
    });

    it.each(['abc', '0', '', '-', '2.5'])('«%s» не применяется, ввод сбрасывается', (text) => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.changeText(text));

      // вызов
      act(() => result.current.commit());

      // проверка
      expect(onCommit).not.toHaveBeenCalled();
      expect(result.current).toMatchObject({ mode: 'NONE', text: '' });
    });

    it('«±» меняет знак введённого урона', () => {
      // подготовка
      const { result } = renderInput();
      act(() => result.current.changeText('5'));

      // вызов и проверка
      act(() => result.current.toggleSign());
      expect(result.current).toMatchObject({ text: '-5', pending: -5, mode: 'MANUAL' });
      act(() => result.current.toggleSign());
      expect(result.current).toMatchObject({ text: '5', pending: 5 });
    });
  });

  describe('Сброс ввода', () => {
    it('«Отмена» очищает ввод и останавливает таймер', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(10));

      // вызов
      act(() => result.current.cancel());
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(result.current).toMatchObject({ mode: 'NONE', text: '', pending: 0, timerRunning: false });
      expect(onCommit).not.toHaveBeenCalled();
    });

    it('«Закончить раунд» забирает накопленный ввод без применения', () => {
      // подготовка
      const { result, onCommit } = renderInput();
      act(() => result.current.pressQuick(5));

      // вызов
      let taken = 0;
      act(() => {
        taken = result.current.take();
      });
      act(() => vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2));

      // проверка
      expect(taken).toBe(5);
      expect(onCommit).not.toHaveBeenCalled();
      expect(result.current.mode).toBe('NONE');
    });

    it('после ухода с экрана таймер не срабатывает', () => {
      // подготовка
      const { result, onCommit, unmount } = renderInput();
      act(() => result.current.pressQuick(5));

      // вызов
      unmount();
      vi.advanceTimersByTime(QUICK_DAMAGE_DELAY_MS * 2);

      // проверка
      expect(onCommit).not.toHaveBeenCalled();
    });
  });
});
