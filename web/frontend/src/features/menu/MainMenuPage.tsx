import { Alert, Button, Group, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router';
import { ru } from '../../shared/i18n/ru';
import { displayNameOf, useMe } from '../auth/useMe';
import { useActiveBattle } from '../battle/useActiveBattle';
import { PendingResultBanner } from '../progression/PendingResultBanner';

/**
 * Главное меню (аналог MainMenuScreen в app). «Вернуться к бою» — если в браузере есть незаконченный бой;
 * без входа — «Войти», после входа — «Настройки». «Кампании» без входа ведут на вход.
 */
export function MainMenuPage() {
  const active = useActiveBattle();
  const { me, isLoading } = useMe();
  return (
    <Stack gap="lg" data-testid="main-menu">
      <div>
        <Title order={1}>{ru.app.title}</Title>
        <Text c="dimmed">{ru.app.subtitle}</Text>
      </div>
      {active.restoreProblem !== null && (
        <Alert
          color="yellow"
          withCloseButton
          onClose={active.dismissRestoreProblem}
          data-testid="menu-restore-problem"
        >
          {active.restoreProblem === 'CORRUPTED' ? ru.battle.restoreCorrupted : ru.battle.restoreUnsupported}
        </Alert>
      )}
      <PendingResultBanner battle={active.battle} />
      <Stack gap="sm">
        {active.hasUnfinished && (
          <Button component={Link} to="/battle" size="lg" color="green" data-testid="menu-resume-battle">
            {ru.menu.resumeBattle}
          </Button>
        )}
        <Button component={Link} to="/expedition/new" size="lg" data-testid="menu-expedition">
          {ru.menu.expedition}
        </Button>
        <Button component={Link} to="/campaigns" size="lg" variant="light" data-testid="menu-campaigns">
          {ru.menu.campaigns}
        </Button>
        {me?.user?.admin === true && (
          <Button component={Link} to="/admin" size="lg" variant="default" data-testid="menu-admin">
            {ru.admin.menu}
          </Button>
        )}
      </Stack>
      {!isLoading && (
        <Group justify="space-between">
          {me === null ? (
            <Button component={Link} to="/login" variant="subtle" data-testid="menu-login">
              {ru.menu.login}
            </Button>
          ) : (
            <>
              <Text size="sm" c="dimmed" data-testid="menu-user">
                {displayNameOf(me)}
              </Text>
              <Button component={Link} to="/settings" variant="subtle" data-testid="menu-settings">
                {ru.menu.settings}
              </Button>
            </>
          )}
        </Group>
      )}
    </Stack>
  );
}
