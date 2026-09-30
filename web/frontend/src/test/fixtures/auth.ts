import type { MeResponse } from '../../api/generated/primal.schemas';

/** Вошедший пользователь для тестов экранов. */
export const userMe: MeResponse = {
  kind: 'USER',
  user: { id: 7, phone: '+79123456789', displayName: 'Алиса', passwordSet: true },
  device: { id: 'a41f0000-0000-4000-8000-000000000001', displayName: null },
};

/** Гость по ссылке-приглашению. */
export const guestMe: MeResponse = {
  kind: 'GUEST',
  user: null,
  device: { id: 'c9d00000-0000-4000-8000-000000000002', displayName: 'Вадим' },
};
