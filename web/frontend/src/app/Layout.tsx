import { AppShell, Container } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router';
import { setUnauthorizedHandler } from '../api/http';
import { ME_QUERY_KEY } from '../features/auth/useMe';
import { OfflineBanner } from '../shared/ui/OfflineBanner';

/** Общий каркас страниц: интерфейс рассчитан в первую очередь на телефон у игрового стола. */
export function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    // Устройство отозвано или устарело посреди работы: «кто я» сбрасывается, дальше — вход
    setUnauthorizedHandler((url) => {
      queryClient.setQueryData(ME_QUERY_KEY, null);
      navigate(url, { replace: true });
    });
  }, [navigate, queryClient]);

  return (
    <AppShell padding="md">
      <AppShell.Main>
        <Container size="sm" px={0}>
          <OfflineBanner />
          <Outlet />
        </Container>
      </AppShell.Main>
    </AppShell>
  );
}
