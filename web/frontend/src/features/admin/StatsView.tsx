import { Alert, Button, Card, Group, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import { useGetAdminStats } from '../../api/generated/admin/admin';
import type { Counter } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

/**
 * Статистика (qa № 140): учётные записи, кампании и сыгранные бои кампаний — всего, за 30 и за 7 дней.
 * Экспедиция идёт только в браузере — её бои сервер не видит.
 */
export function StatsView() {
  const stats = useGetAdminStats({ query: { retry: false, staleTime: 0 } });

  if (stats.isError) {
    return <Alert color="red">{ru.admin.statsError}</Alert>;
  }
  if (stats.data === undefined) {
    return <Loader />;
  }
  const { accounts, campaigns, battles, generatedAt } = stats.data;

  return (
    <Stack gap="md" data-testid="admin-stats">
      <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="sm">
        <Tile label={ru.admin.statsAccounts} value={accounts.total} testId="admin-stats-accounts" />
        <Tile label={ru.admin.statsCampaigns} value={campaigns.created.total} testId="admin-stats-campaigns" />
        <Tile label={ru.admin.statsBattles} value={battles.played.total} testId="admin-stats-battles" />
      </SimpleGrid>
      <Table withTableBorder data-testid="admin-stats-table">
        <Table.Thead>
          <Table.Tr>
            <Table.Th />
            <Table.Th ta="right">{ru.admin.statsTotal}</Table.Th>
            <Table.Th ta="right">{ru.admin.stats30}</Table.Th>
            <Table.Th ta="right">{ru.admin.stats7}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          <Row label={ru.admin.statsAccountsRow} counter={accounts} testId="admin-stats-row-accounts" />
          <Row label={ru.admin.statsCampaignsRow} counter={campaigns.created} testId="admin-stats-row-campaigns" />
          <Row label={ru.admin.statsBattlesRow} counter={battles.played} testId="admin-stats-row-battles" />
        </Table.Tbody>
      </Table>
      <Stack gap={2}>
        <Text size="sm" data-testid="admin-stats-campaign-status">
          {ru.admin.statsCampaignStatus(campaigns.active, campaigns.completed)}
        </Text>
        <Text size="sm" data-testid="admin-stats-battle-results">
          {ru.admin.statsBattleResults(battles.victories, battles.defeats, battles.inProgress)}
        </Text>
      </Stack>
      <Text size="xs" c="dimmed">
        {ru.admin.statsHint}
      </Text>
      <Group justify="space-between">
        <Text size="xs" c="dimmed">
          {ru.admin.statsAt(new Date(generatedAt).toLocaleString('ru-RU'))}
        </Text>
        <Button size="xs" variant="light" loading={stats.isFetching} onClick={() => void stats.refetch()} data-testid="admin-stats-refresh">
          {ru.admin.statsRefresh}
        </Button>
      </Group>
    </Stack>
  );
}

function Tile({ label, value, testId }: { label: string; value: number; testId: string }) {
  return (
    <Card withBorder padding="sm" data-testid={testId} data-value={value}>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Title order={2}>{value.toLocaleString('ru-RU')}</Title>
    </Card>
  );
}

function Row({ label, counter, testId }: { label: string; counter: Counter; testId: string }) {
  return (
    <Table.Tr data-testid={testId}>
      <Table.Td>{label}</Table.Td>
      <Table.Td ta="right">{counter.total}</Table.Td>
      <Table.Td ta="right">{counter.last30Days}</Table.Td>
      <Table.Td ta="right">{counter.last7Days}</Table.Td>
    </Table.Tr>
  );
}
