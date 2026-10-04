import { Center, Loader } from '@mantine/core';
import { lazy, Suspense } from 'react';
import { ru } from '../../shared/i18n/ru';
import { PageStub } from '../../shared/ui/PageStub';
import { useMe } from '../auth/useMe';

// Код редактора загружается отдельным файлом и только администратору: остальным он не отдаётся вовсе
const AdminPage = lazy(() => import('./AdminPage'));

/** `/admin` — только администратору; остальным та же «Страница не найдена», что и для несуществующего адреса. */
export function RequireAdmin() {
  const { me, isLoading } = useMe();
  if (isLoading) {
    return (
      <Center py="xl" data-testid="auth-checking">
        <Loader />
      </Center>
    );
  }
  if (me?.user?.admin !== true) {
    return <PageStub title={ru.pages.notFound} testId="page-not-found" />;
  }
  return (
    <Suspense
      fallback={
        <Center py="xl">
          <Loader />
        </Center>
      }
    >
      <AdminPage />
    </Suspense>
  );
}
