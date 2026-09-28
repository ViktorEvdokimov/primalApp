import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { loginUrl } from '../../shared/routing';
import { useMe } from './useMe';

/** Кампании и настройки — только после входа (пользователь или гость по ссылке); иначе — на вход с возвратом. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, isLoading } = useMe();
  const location = useLocation();
  if (isLoading) {
    return (
      <Center py="xl" data-testid="auth-checking">
        <Loader />
      </Center>
    );
  }
  if (me === null) {
    return <Navigate to={loginUrl(location.pathname + location.search)} replace />;
  }
  return children;
}
