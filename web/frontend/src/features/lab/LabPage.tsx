import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Modal,
  NativeSelect,
  SegmentedControl,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { useBrewPotion } from '../../api/generated/campaigns/campaigns';
import { useDictionaries, useLab } from '../../api/generated/catalog/catalog';
import type { CampaignSheet, Dictionaries, HunterSheet, LabPotion, LabUnit } from '../../api/generated/primal.schemas';
import { useLiveUpdates } from '../../api/useLiveUpdates';
import { ru } from '../../shared/i18n/ru';
import { ResourceIcon, ResourceStock } from '../../shared/ui/ResourceStock';
import { useCampaignSheet, withHunter, type SheetActions } from '../campaign/useCampaignSheet';
import { ExchangeDialog, type Lack } from '../exchange/ExchangeDialog';
import { affordable, defaultPlants, lackingPlants, spend, type PlantCode } from './lab';

const CATALOG_STALE_MS = 60 * 60 * 1000;

/**
 * Лаборатория кампании (`behavior.md` §7.2): 6 зелий планшета, цена одинакова на всех уровнях. «Приготовить»
 * списывает 2 растения; растение на выбор и «любое» игрок выбирает в окне подтверждения.
 */
export function LabPage() {
  const campaignId = Number(useParams().id);
  const { sheet, ...actions } = useCampaignSheet(campaignId);
  // Цены может изменить администратор (qa № 138): при каждом открытии — проверка по ETag
  const lab = useLab({ query: { staleTime: 0 } });
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_MS } });
  useLiveUpdates(campaignId);

  const back = (
    <Anchor component={Link} to={`/campaigns/${campaignId}`} size="sm" data-testid="lab-back">
      {ru.progression.backToSheet}
    </Anchor>
  );
  if (sheet.isError || lab.isError) {
    return (
      <Stack gap="md" data-testid="page-campaign-lab">
        {back}
        <Alert color="red" data-testid="lab-error">
          {ru.lab.loadError}
        </Alert>
      </Stack>
    );
  }
  if (sheet.data === undefined || lab.data === undefined || dictionaries.data === undefined) {
    return (
      <Stack gap="md" data-testid="page-campaign-lab">
        {back}
        <Loader />
      </Stack>
    );
  }
  return <Lab sheet={sheet.data} potions={lab.data} dictionaries={dictionaries.data} actions={actions} back={back} />;
}

interface LabProps {
  sheet: CampaignSheet;
  potions: LabPotion[];
  dictionaries: Dictionaries;
  actions: SheetActions;
  back: ReactNode;
}

function Lab({ sheet, potions, dictionaries, actions, back }: LabProps) {
  const [selectedId, setSelectedId] = useState(sheet.hunters[0]?.id);
  const [brewing, setBrewing] = useState<LabPotion | null>(null);
  const [exchanging, setExchanging] = useState<Lack[] | null>(null);
  const hunter = sheet.hunters.find((item) => item.id === selectedId) ?? sheet.hunters[0];
  const names = new Map([...dictionaries.plants, ...dictionaries.hunterClasses].map((item) => [item.code, item.name]));

  return (
    <Stack gap="md" data-testid="page-campaign-lab">
      {back}
      <Group justify="space-between" wrap="nowrap">
        <Title order={2}>{ru.lab.title}</Title>
        <Badge size="lg" variant="light" data-testid="lab-level" data-level={sheet.labLevel}>
          {ru.sheet.lab(sheet.labLevel)}
        </Badge>
      </Group>
      {hunter !== undefined && (
        <>
          <SegmentedControl
            fullWidth
            orientation={sheet.hunters.length > 3 ? 'vertical' : 'horizontal'}
            value={String(hunter.id)}
            onChange={(value) => setSelectedId(Number(value))}
            data={sheet.hunters.map((item) => ({
              value: String(item.id),
              label: (
                <span data-testid="lab-hunter-option" data-player={item.playerName} data-class={item.class}>
                  {item.playerName === names.get(item.class)
                    ? item.playerName
                    : `${item.playerName} · ${names.get(item.class) ?? item.class}`}
                </span>
              ),
            }))}
            data-testid="lab-hunter-switch"
          />
          <ResourceStock
            resources={hunter.resources}
            codes={dictionaries.plants.map((plant) => plant.code)}
            names={names}
            testIdPrefix="lab"
          />
          <Text size="sm" c="dimmed">
            {ru.lab.hint}
          </Text>
          {potions.map((potion) => {
            const possible = defaultPlants(potion.units, hunter.resources) !== null;
            return (
              <Card
                key={potion.code}
                withBorder
                padding="sm"
                data-testid="lab-potion"
                data-code={potion.code}
                data-name={potion.name}
              >
                <Group justify="space-between" wrap="nowrap" align="flex-start">
                  <Stack gap={4} style={{ minWidth: 0 }}>
                    <Text fw={600}>{potion.name}</Text>
                    <Group gap="sm" data-testid="lab-potion-cost">
                      {potion.units.map((unit, index) => (
                        <UnitLabel key={index} unit={unit} names={names} />
                      ))}
                    </Group>
                    {!possible && (
                      <Group gap="xs">
                        <Text size="xs" c="red" data-testid="lab-potion-missing">
                          {ru.lab.missing}
                        </Text>
                        <Button
                          size="compact-xs"
                          variant="light"
                          onClick={() => setExchanging(lackingPlants(potion.units, hunter.resources))}
                          data-testid="lab-potion-exchange"
                        >
                          {ru.exchange.open}
                        </Button>
                      </Group>
                    )}
                  </Stack>
                  <Button
                    size="xs"
                    disabled={!possible}
                    onClick={() => setBrewing(potion)}
                    style={{ flexShrink: 0 }}
                    data-testid="lab-potion-brew"
                  >
                    {ru.lab.brew}
                  </Button>
                </Group>
              </Card>
            );
          })}
          <Modal opened={brewing !== null} onClose={() => setBrewing(null)} title={ru.lab.confirmTitle} centered>
            {brewing !== null && (
              <BrewDialog
                key={brewing.code}
                campaignId={sheet.id}
                potion={brewing}
                level={sheet.labLevel}
                hunter={hunter}
                names={names}
                actions={actions}
                onClose={() => setBrewing(null)}
              />
            )}
          </Modal>
          {/* Растения получают только обменом: преобразование и продажа дают материи и стихии */}
          <ExchangeDialog
            opened={exchanging !== null}
            onClose={() => setExchanging(null)}
            campaignId={sheet.id}
            hunters={sheet.hunters}
            hunterId={hunter.id}
            dictionaries={dictionaries}
            actions={actions}
            lacking={exchanging ?? []}
            modes={['EXCHANGE']}
          />
        </>
      )}
    </Stack>
  );
}

/** «1 Антемон», «1 Антемон / Меллис» или «1 любое растение». */
function UnitLabel({ unit, names }: { unit: LabUnit; names: Map<string, string> }) {
  return (
    <Group gap={4} wrap="nowrap" data-testid="lab-unit" data-options={unit.options.join(',')} data-any={unit.any}>
      <Text size="sm">1</Text>
      {unit.any ? (
        <Text size="sm">{ru.lab.anyPlant}</Text>
      ) : (
        unit.options.map((option, index) => (
          <Group key={option} gap={4} wrap="nowrap">
            {index > 0 && <Text size="sm">/</Text>}
            <ResourceIcon code={option} size={18} />
            <Text size="sm">{names.get(option) ?? option}</Text>
          </Group>
        ))
      )}
    </Group>
  );
}

interface BrewDialogProps {
  campaignId: number;
  potion: LabPotion;
  level: number;
  hunter: HunterSheet;
  names: Map<string, string>;
  actions: SheetActions;
  onClose: () => void;
}

/** Подтверждение: выбор растений там, где на планшете выбор или «любое», и что спишется. */
function BrewDialog({ campaignId, potion, level, hunter, names, actions, onClose }: BrewDialogProps) {
  const brew = useBrewPotion();
  const [plants, setPlants] = useState<PlantCode[]>(
    () => defaultPlants(potion.units, hunter.resources) ?? potion.units.map((unit) => unit.options[0] ?? 'NILLEA'),
  );
  const enough = affordable(plants, hunter.resources);
  const label = (code: string) => `${names.get(code) ?? code} (${hunter.resources[code] ?? 0})`;

  const confirm = () => {
    brew.mutateAsync({ campaignId, hunterId: hunter.id, data: { potion: potion.code, plants } }).then(
      (result) => {
        onClose();
        actions.applied((sheet) =>
          withHunter(sheet, hunter.id, () => result.hunter),
        );
        notifications.show({ color: 'teal', message: ru.lab.brewed(result.name, result.level) });
      },
      (error: unknown) => {
        onClose();
        actions.failed(error);
      },
    );
  };

  return (
    <Stack gap="md" data-testid="lab-confirm">
      <Text>{ru.lab.confirmText(potion.name, level, hunter.playerName)}</Text>
      {potion.units.map((unit, index) =>
        unit.options.length > 1 ? (
          <NativeSelect
            key={index}
            label={unit.any ? ru.lab.choiceAny(index + 1) : ru.lab.choice}
            value={plants[index]}
            data={unit.options.map((option) => ({ value: option, label: label(option) }))}
            onChange={(event) => {
              // значение — одно из options этой единицы цены
              const value = event.currentTarget.value as PlantCode;
              setPlants((current) => current.map((plant, position) => (position === index ? value : plant)));
            }}
            data-testid="lab-choice"
          />
        ) : null,
      )}
      <Text size="sm" c={enough ? 'dimmed' : 'red'} data-testid="lab-confirm-cost">
        {enough
          ? ru.forge.confirmCost(
              spend(plants)
                .map(([code, count]) => `${names.get(code) ?? code} ${count}`)
                .join(', '),
            )
          : ru.lab.notEnoughChoice}
      </Text>
      <Text size="xs" c="dimmed">
        {ru.lab.uniqueReminder}
      </Text>
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose} data-testid="lab-confirm-cancel">
          {ru.forge.cancel}
        </Button>
        <Button loading={brew.isPending} disabled={!enough} onClick={confirm} data-testid="lab-confirm-ok">
          {ru.lab.brew}
        </Button>
      </Group>
    </Stack>
  );
}
