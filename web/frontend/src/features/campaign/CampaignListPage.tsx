import { Alert, Anchor, Badge, Button, Card, Group, Loader, Modal, Stack, Text, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { getListCampaignsQueryKey, useDeleteCampaign, useListCampaigns } from '../../api/generated/campaigns/campaigns';
import { useDictionaries } from '../../api/generated/catalog/catalog';
import type { CampaignSummary } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useMe } from '../auth/useMe';

/** Список кампаний: свои и открытые по ссылкам (doc/api.md §5.1). */
export function CampaignListPage() {
  const { me } = useMe();
  const queryClient = useQueryClient();
  const campaigns = useListCampaigns();
  const dictionaries = useDictionaries({ query: { staleTime: 60 * 60 * 1000 } });
  const deleteCampaign = useDeleteCampaign();
  const [toDelete, setToDelete] = useState<CampaignSummary | null>(null);
  const classNames = new Map((dictionaries.data?.hunterClasses ?? []).map((item) => [item.code, item.name]));
  const isUser = me?.kind === 'USER';

  const confirmDelete = async () => {
    if (toDelete === null) return;
    await deleteCampaign.mutateAsync({ id: toDelete.id });
    setToDelete(null);
    await queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
  };

  return (
    <Stack gap="md" data-testid="page-campaigns">
      <Anchor component={Link} to="/" size="sm" data-testid="campaigns-to-menu">
        {ru.campaigns.toMenu}
      </Anchor>
      <Group justify="space-between">
        <Title order={2}>{ru.pages.campaigns}</Title>
        {isUser && (
          <Button component={Link} to="/campaigns/new" data-testid="campaigns-create">
            {ru.campaigns.create}
          </Button>
        )}
      </Group>
      {!isUser && me !== null && <Text c="dimmed">{ru.campaigns.guestCannotCreate}</Text>}

      {campaigns.isPending && <Loader />}
      {campaigns.isError && (
        <Alert color="red" data-testid="campaigns-error">
          <Group justify="space-between">
            <Text size="sm">{ru.campaigns.loadError}</Text>
            <Button size="xs" variant="light" onClick={() => void campaigns.refetch()}>
              {ru.campaigns.retry}
            </Button>
          </Group>
        </Alert>
      )}
      {campaigns.data?.length === 0 && (
        <Text c="dimmed" data-testid="campaigns-empty">
          {ru.campaigns.empty}
        </Text>
      )}

      {campaigns.data?.map((campaign) => (
        <Card key={campaign.id} withBorder padding="md" data-testid="campaign-item">
          <Stack gap={6}>
            <Group justify="space-between" wrap="nowrap">
              <Text fw={600} data-testid="campaign-item-name">
                {campaign.name}
              </Text>
              <Badge variant="light" data-testid="campaign-item-chapter">
                {ru.campaigns.chapter(campaign.chapter)}
              </Badge>
            </Group>
            <Text size="sm" c="dimmed" data-testid="campaign-item-hunters">
              {campaign.hunters.map((hunter) => `${classNames.get(hunter.class) ?? hunter.class} — ${hunter.playerName}`).join(', ')}
            </Text>
            {campaign.access === 'LINK' && (
              <Group gap="xs">
                <Badge variant="outline" size="sm" style={{ textTransform: 'none' }} data-testid="campaign-item-by-link">
                  {ru.sharing.byLink}
                </Badge>
                <Text size="sm" c="dimmed">
                  {ru.campaigns.owner(campaign.ownerName)}
                </Text>
              </Group>
            )}
            {campaign.pendingTransition && (
              <Badge color="orange" variant="light">
                {ru.campaigns.pendingTransition}
              </Badge>
            )}
            <Group justify="flex-end" gap="xs">
              {campaign.access === 'OWNER' && (
                <Button size="xs" variant="subtle" color="red" onClick={() => setToDelete(campaign)} data-testid="campaign-item-delete">
                  {ru.campaigns.delete}
                </Button>
              )}
              <Button size="xs" component={Link} to={`/campaigns/${campaign.id}`} data-testid="campaign-item-open">
                {ru.campaigns.open}
              </Button>
            </Group>
          </Stack>
        </Card>
      ))}

      <Modal opened={toDelete !== null} onClose={() => setToDelete(null)} title={ru.campaigns.deleteTitle} centered>
        <Stack gap="md">
          <Text>{toDelete === null ? '' : ru.campaigns.deleteText(toDelete.name)}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setToDelete(null)} data-testid="campaign-delete-cancel">
              {ru.campaigns.cancel}
            </Button>
            <Button color="red" loading={deleteCampaign.isPending} onClick={() => void confirmDelete()} data-testid="campaign-delete-confirm">
              {ru.campaigns.delete}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
