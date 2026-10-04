import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getGetAdminCatalogQueryKey } from '../../api/generated/admin/admin';
import type {
  AdminBoss,
  AdminCatalog,
  AdminChapter,
  AdminForgeItem,
  AdminLabPotion,
  AdminQuest,
} from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { errorMessage } from '../campaign/useCampaignSheet';

/** Заменить элемент каталога админки в кэше ответом сервера. */
export function useCatalogCache() {
  const queryClient = useQueryClient();
  const key = getGetAdminCatalogQueryKey();
  const update = (patch: (current: AdminCatalog) => AdminCatalog) =>
    queryClient.setQueryData<AdminCatalog>(key, (current) => (current === undefined ? current : patch(current)));
  return {
    quest: (quest: AdminQuest) =>
      update((current) => ({ ...current, quests: current.quests.map((q) => (q.number === quest.number ? quest : q)) })),
    chapter: (chapter: AdminChapter) =>
      update((current) => ({
        ...current,
        chapters: current.chapters.map((c) => (c.chapter === chapter.chapter ? chapter : c)),
      })),
    forge: (item: AdminForgeItem) =>
      update((current) => ({ ...current, forge: current.forge.map((f) => (f.code === item.code ? item : f)) })),
    lab: (potion: AdminLabPotion) =>
      update((current) => ({ ...current, lab: current.lab.map((p) => (p.code === potion.code ? potion : p)) })),
    boss: (boss: AdminBoss) =>
      update((current) => ({ ...current, bosses: current.bosses.map((b) => (b.code === boss.code ? boss : b)) })),
  };
}

/**
 * Сохранение и «Вернуть исходные» одной правки: после ответа сервера — кэш и сообщение, ошибка — в `error`.
 */
export function useSaveFlow<T>(apply: (saved: T) => void) {
  const [error, setError] = useState<string | null>(null);
  const run = (request: Promise<T>, message: string) => {
    setError(null);
    return request.then(
      (saved) => {
        apply(saved);
        notifications.show({ color: 'teal', message });
      },
      (cause: unknown) => setError(errorMessage(cause)),
    );
  };
  return {
    error,
    clearError: () => setError(null),
    save: (request: Promise<T>) => run(request, ru.admin.saved),
    reset: (request: Promise<T>) => run(request, ru.admin.resetDone),
  };
}
