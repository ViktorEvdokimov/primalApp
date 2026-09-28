import { Alert, Button, Card, Chip, Group, Stack, Text, TextInput, Title } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../api/errors';
import { getListCampaignsQueryKey, useCreateCampaign } from '../../api/generated/campaigns/campaigns';
import { useDictionaries } from '../../api/generated/catalog/catalog';
import type { HunterRequestClass } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';

const MIN_HUNTERS = 2;
const MAX_HUNTERS = 5;

/** Классы по порядку справочника: 4 базовых и 4 из дополнений. */
const CLASSES: HunterRequestClass[] = ['DAREON', 'MIRA', 'TOREG', 'LIONAR', 'KARA', 'HELEREN', 'DRUSK', 'ZARAIA'];

function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) return ru.errors.unknown;
  if (error.code === 'NETWORK_ERROR') return ru.errors.network;
  return error.detail ?? ru.errors.unknown;
}

/** Новая кампания: название и отряд из 2–5 охотников разных классов (doc/api.md §5.1). */
export function CampaignCreatePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const dictionaries = useDictionaries({ query: { staleTime: 60 * 60 * 1000 } });
  const createCampaign = useCreateCampaign();
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<HunterRequestClass[]>([]);
  const [playerNames, setPlayerNames] = useState<Partial<Record<HunterRequestClass, string>>>({});
  const [error, setError] = useState<string | null>(null);

  const classNames = new Map((dictionaries.data?.hunterClasses ?? []).map((item) => [item.code, item.name]));
  const className = (code: HunterRequestClass) => classNames.get(code) ?? code;
  const canSubmit = name.trim() !== '' && selected.length >= MIN_HUNTERS && selected.length <= MAX_HUNTERS;

  const toggle = (code: HunterRequestClass) => {
    setSelected((current) => (current.includes(code) ? current.filter((item) => item !== code) : [...current, code]));
  };

  const submit = async () => {
    setError(null);
    try {
      const sheet = await createCampaign.mutateAsync({
        data: {
          name: name.trim(),
          hunters: selected.map((code) => ({ class: code, playerName: (playerNames[code] ?? '').trim() })),
        },
      });
      await queryClient.invalidateQueries({ queryKey: getListCampaignsQueryKey() });
      // После создания — сразу подготовка пролога: Вираксен, сложность 0 (battle.md §9, п. 9)
      navigate(`/campaigns/${sheet.id}/battle/new`);
    } catch (cause) {
      setError(errorText(cause));
    }
  };

  return (
    <Stack gap="md" data-testid="page-campaign-new">
      <Title order={2}>{ru.pages.campaignNew}</Title>
      {error !== null && (
        <Alert color="red" data-testid="campaign-new-error">
          {error}
        </Alert>
      )}
      <TextInput
        label={ru.campaigns.name}
        placeholder={ru.campaigns.namePlaceholder}
        maxLength={100}
        required
        value={name}
        onChange={(event) => setName(event.currentTarget.value)}
        data-testid="campaign-new-name"
      />
      <Stack gap={4}>
        <Group justify="space-between">
          <Text fw={500}>{ru.campaigns.squad}</Text>
          <Text size="sm" c={selected.length >= MIN_HUNTERS ? 'dimmed' : 'orange'} data-testid="campaign-new-count">
            {ru.campaigns.squadCount(selected.length)}
          </Text>
        </Group>
        <Text size="sm" c="dimmed">
          {ru.campaigns.squadHint}
        </Text>
        <Group gap="xs">
          {CLASSES.map((code) => (
            <Chip
              key={code}
              checked={selected.includes(code)}
              disabled={!selected.includes(code) && selected.length >= MAX_HUNTERS}
              onChange={() => toggle(code)}
              data-testid={`campaign-new-class-${code}`}
            >
              {className(code)}
            </Chip>
          ))}
        </Group>
      </Stack>
      {selected.length > 0 && (
        <Card withBorder padding="sm">
          <Stack gap="xs">
            {selected.map((code, index) => (
              <TextInput
                key={code}
                label={`${index + 1}. ${ru.campaigns.playerName(className(code))}`}
                description={ru.campaigns.playerNameHint}
                placeholder={className(code)}
                maxLength={60}
                value={playerNames[code] ?? ''}
                onChange={(event) => {
                  const value = event.currentTarget.value;
                  setPlayerNames((current) => ({ ...current, [code]: value }));
                }}
                data-testid={`campaign-new-player-${code}`}
              />
            ))}
          </Stack>
        </Card>
      )}
      <Button size="md" disabled={!canSubmit} loading={createCampaign.isPending} onClick={() => void submit()} data-testid="campaign-new-submit">
        {ru.campaigns.submit}
      </Button>
      <Button component={Link} to="/campaigns" variant="subtle" data-testid="campaign-new-back">
        {ru.campaigns.cancel}
      </Button>
    </Stack>
  );
}
