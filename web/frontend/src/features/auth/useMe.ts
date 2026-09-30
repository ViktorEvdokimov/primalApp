import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/errors';
import { getGetMeQueryKey, getMe } from '../../api/generated/auth/auth';
import type { MeResponse } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

export const ME_QUERY_KEY = getGetMeQueryKey();

/** «Кто я»; `null` — входа нет (сервер ответил 401). */
async function fetchMe(): Promise<MeResponse | null> {
  try {
    return await getMe();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

/** Текущий пользователь или гость. Экраны без входа тоже его читают: меню показывает «Войти» или «Настройки». */
export function useMe() {
  const query = useQuery({ queryKey: ME_QUERY_KEY, queryFn: fetchMe, staleTime: 60_000, retry: false });
  return {
    me: query.data ?? null,
    isLoading: query.isPending,
    isError: query.isError,
    refetch: query.refetch,
  };
}

/** Подпись без своего имени: «Игрок» или «Гость» — номер телефона другим не показывается. */
export function defaultNameOf(me: MeResponse): string {
  return me.user === null ? ru.settings.guestName : ru.settings.playerName;
}

/** Имя для показа: своё имя (аккаунта или гостевого устройства), иначе подпись по умолчанию. */
export function displayNameOf(me: MeResponse): string {
  return (me.user === null ? me.device.displayName : me.user.displayName) ?? defaultNameOf(me);
}
