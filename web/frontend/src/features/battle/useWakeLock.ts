import { useEffect } from 'react';

/**
 * Экран не гаснет, пока идёт бой (Screen Wake Lock API). Браузер снимает блокировку, когда вкладка
 * уходит в фон, поэтому при возврате она запрашивается снова. Нет API или отказ (режим энергосбережения) —
 * экран гаснет как обычно.
 */
export function useWakeLock(enabled: boolean) {
  useEffect(() => {
    if (!enabled || !('wakeLock' in navigator)) return;
    let sentinel: WakeLockSentinel | null = null;
    let released = false;

    const request = async () => {
      try {
        const lock = await navigator.wakeLock.request('screen');
        if (released) void lock.release();
        else sentinel = lock;
      } catch {
        // блокировку не дали — не страшно
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') void request();
    };

    void request();
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      released = true;
      document.removeEventListener('visibilitychange', onVisibilityChange);
      void sentinel?.release();
    };
  }, [enabled]);
}
