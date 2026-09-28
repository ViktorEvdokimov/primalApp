import { useCallback, useEffect, useRef, useState } from 'react';
import { useAdjustResources } from '../../api/generated/campaigns/campaigns';
import { withHunter, type SheetActions } from './useCampaignSheet';

type Changes = Record<string, number>;

/** Нажатия +/− копятся столько, прежде чем уйти одним запросом. */
export const RESOURCE_DEBOUNCE_MS = 400;

function add(changes: Changes, code: string, delta: number): Changes {
  return { ...changes, [code]: (changes[code] ?? 0) + delta };
}

function nonZero(changes: Changes): Changes {
  return Object.fromEntries(Object.entries(changes).filter(([, delta]) => delta !== 0));
}

/**
 * Ресурсы охотника с отложенной отправкой: нажатия +/− сразу видны, а на сервер уходят пачкой
 * (`POST …/resources/adjust`, всё или ничего) через {@link RESOURCE_DEBOUNCE_MS} мс после последнего нажатия.
 * Следующая пачка ждёт ответа на предыдущую, поэтому значения не «прыгают». При уходе с листа или смене
 * охотника накопленное отправляется сразу.
 */
export function useResourceChanges(campaignId: number, hunterId: number, stored: Record<string, number>, actions: SheetActions) {
  const { mutateAsync } = useAdjustResources();
  const pending = useRef<Changes>({});
  const sending = useRef<Changes | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  // Снимок очередей для рендера: сами очереди — в ref, их читают обработчики и таймер
  const [view, setView] = useState<{ pending: Changes; sending: Changes | null }>({ pending: {}, sending: null });
  const rerender = useCallback(() => setView({ pending: pending.current, sending: sending.current }), []);

  const flushRef = useRef<() => Promise<void>>(() => Promise.resolve());
  const flush = useCallback(async (): Promise<void> => {
    clearTimeout(timer.current);
    timer.current = undefined;
    if (sending.current !== null) return; // отправится после ответа на текущую пачку
    const changes = nonZero(pending.current);
    pending.current = {};
    if (Object.keys(changes).length === 0) {
      rerender();
      return;
    }
    sending.current = changes;
    rerender();
    try {
      const response = await mutateAsync({ campaignId, hunterId, data: { changes } });
      actions.applied((sheet) => withHunter(sheet, hunterId, (hunter) => ({ ...hunter, resources: response.resources })));
    } catch (error) {
      actions.failed(error);
    } finally {
      sending.current = null;
      rerender();
    }
    if (Object.keys(nonZero(pending.current)).length > 0) await flushRef.current();
  }, [mutateAsync, campaignId, hunterId, actions, rerender]);

  useEffect(() => {
    flushRef.current = flush;
  }, [flush]);
  useEffect(() => () => void flushRef.current(), []);

  const change = useCallback(
    (code: string, delta: number) => {
      pending.current = add(pending.current, code, delta);
      rerender();
      clearTimeout(timer.current);
      timer.current = setTimeout(() => void flushRef.current(), RESOURCE_DEBOUNCE_MS);
    },
    [rerender],
  );

  const value = (code: string) => (stored[code] ?? 0) + (view.sending?.[code] ?? 0) + (view.pending[code] ?? 0);

  /** Есть изменения, ещё не подтверждённые сервером. */
  const saving = view.sending !== null || Object.keys(nonZero(view.pending)).length > 0;

  return { value, change, saving };
}
