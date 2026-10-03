import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Modal,
  SegmentedControl,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { useCraftEquipment } from '../../api/generated/campaigns/campaigns';
import { useDictionaries, useForge } from '../../api/generated/catalog/catalog';
import type {
  CampaignSheet,
  Dictionaries,
  ForgeBoard,
  ForgeItem,
  HunterSheet,
} from '../../api/generated/primal.schemas';
import { useLiveUpdates } from '../../api/useLiveUpdates';
import { ru } from '../../shared/i18n/ru';
import { ResourceIcon, ResourceStock } from '../../shared/ui/ResourceStock';
import { useCampaignSheet, withHunter, type SheetActions } from '../campaign/useCampaignSheet';
import { ExchangeDialog } from '../exchange/ExchangeDialog';
import { craftCost, itemsFor, missing, type CostUnit } from './forge';

const CATALOG_STALE_MS = 60 * 60 * 1000;

/**
 * Кузница кампании (`behavior.md` §7.4): планшеты открытых кузниц текущего уровня — оружие класса выбранного
 * охотника, шлем, доспех и предметы. «Создать» списывает 1 стихию кузни и материи планшета.
 */
export function ForgePage() {
  const campaignId = Number(useParams().id);
  const { sheet, ...actions } = useCampaignSheet(campaignId);
  const forge = useForge({ query: { staleTime: CATALOG_STALE_MS } });
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_MS } });
  useLiveUpdates(campaignId);

  const back = (
    <Anchor component={Link} to={`/campaigns/${campaignId}`} size="sm" data-testid="forge-back">
      {ru.progression.backToSheet}
    </Anchor>
  );
  if (sheet.isError || forge.isError) {
    return (
      <Stack gap="md" data-testid="page-campaign-forge">
        {back}
        <Alert color="red" data-testid="forge-error">
          {ru.forge.loadError}
        </Alert>
      </Stack>
    );
  }
  if (sheet.data === undefined || forge.data === undefined || dictionaries.data === undefined) {
    return (
      <Stack gap="md" data-testid="page-campaign-forge">
        {back}
        <Loader />
      </Stack>
    );
  }
  return (
    <Forge sheet={sheet.data} boards={forge.data} dictionaries={dictionaries.data} actions={actions} back={back} />
  );
}

interface ForgeProps {
  sheet: CampaignSheet;
  boards: ForgeBoard[];
  dictionaries: Dictionaries;
  actions: SheetActions;
  back: ReactNode;
}

function Forge({ sheet, boards, dictionaries, actions, back }: ForgeProps) {
  const [selectedId, setSelectedId] = useState(sheet.hunters[0]?.id);
  const hunter = sheet.hunters.find((item) => item.id === selectedId) ?? sheet.hunters[0];
  const names = new Map(
    [...dictionaries.elements, ...dictionaries.materials, ...dictionaries.hunterClasses].map((item) => [
      item.code,
      item.name,
    ]),
  );
  const open = boards.filter((board) => sheet.openForges.includes(board.element));

  return (
    <Stack gap="md" data-testid="page-campaign-forge">
      {back}
      <Group justify="space-between" wrap="nowrap">
        <Title order={2}>{ru.forge.title}</Title>
        <Badge size="lg" variant="light" data-testid="forge-level" data-level={sheet.forgeLevel}>
          {ru.sheet.forge(sheet.forgeLevel)}
        </Badge>
      </Group>

      {hunter !== undefined && (
        <SegmentedControl
          fullWidth
          orientation={sheet.hunters.length > 3 ? 'vertical' : 'horizontal'}
          value={String(hunter.id)}
          onChange={(value) => setSelectedId(Number(value))}
          data={sheet.hunters.map((item) => ({
            value: String(item.id),
            label: (
              <span data-testid="forge-hunter-option" data-player={item.playerName} data-class={item.class}>
                {item.playerName === names.get(item.class)
                  ? item.playerName
                  : `${item.playerName} · ${names.get(item.class) ?? item.class}`}
              </span>
            ),
          }))}
          data-testid="forge-hunter-switch"
        />
      )}

      {open.length === 0 ? (
        <Text c="dimmed" data-testid="forge-none">
          {ru.forge.noneOpen}
        </Text>
      ) : (
        <>
          <Group gap="xs" data-testid="forge-jumps">
            {open.map((board) => (
              <Button
                key={board.element}
                size="xs"
                variant="light"
                leftSection={<ResourceIcon code={board.element} size={18} />}
                onClick={() =>
                  document.getElementById(sectionId(board.element))?.scrollIntoView({ behavior: 'smooth' })
                }
                data-testid="forge-jump"
                data-element={board.element}
              >
                {names.get(board.element) ?? board.element}
              </Button>
            ))}
          </Group>
          {hunter !== undefined && (
            <ResourceStock
              resources={hunter.resources}
              codes={[...dictionaries.elements, ...dictionaries.materials].map((item) => item.code)}
              names={names}
              testIdPrefix="forge"
            />
          )}
          {hunter !== undefined &&
            open.map((board) => (
              <BoardSection
                key={board.element}
                campaignId={sheet.id}
                board={board}
                level={sheet.forgeLevel}
                hunter={hunter}
                hunters={sheet.hunters}
                dictionaries={dictionaries}
                names={names}
                actions={actions}
              />
            ))}
        </>
      )}
    </Stack>
  );
}

const sectionId = (element: string) => `forge-${element.toLowerCase()}`;

interface BoardSectionProps {
  campaignId: number;
  board: ForgeBoard;
  level: number;
  hunter: HunterSheet;
  hunters: HunterSheet[];
  dictionaries: Dictionaries;
  names: Map<string, string>;
  actions: SheetActions;
}

function BoardSection({ campaignId, board, level, hunter, hunters, dictionaries, names, actions }: BoardSectionProps) {
  const craft = useCraftEquipment();
  const [confirming, setConfirming] = useState<ForgeItem | null>(null);
  const [exchanging, setExchanging] = useState<CostUnit[] | null>(null);
  const describe = (cost: CostUnit[]) =>
    cost.map((unit) => `${names.get(unit.code) ?? unit.code} ${unit.quantity}`).join(', ');

  const create = (item: ForgeItem) => {
    craft.mutateAsync({ campaignId, hunterId: hunter.id, data: { item: item.code } }).then(
      (result) => {
        setConfirming(null);
        actions.applied((sheet) =>
          withHunter(sheet, hunter.id, () => result.hunter),
        );
        notifications.show({ color: 'teal', message: ru.forge.created(result.name, result.level) });
      },
      (error: unknown) => {
        setConfirming(null);
        actions.failed(error);
      },
    );
  };

  return (
    <Stack gap="xs" id={sectionId(board.element)} data-testid="forge-section" data-element={board.element}>
      <Group gap="xs">
        <ResourceIcon code={board.element} size={28} />
        <Title order={3}>{ru.forge.board(board.element, level)}</Title>
      </Group>
      {itemsFor(board, hunter.class).map((item) => {
        const cost = craftCost(board.element, item, level);
        const lacking = missing(cost, hunter.resources);
        return (
          <Card
            key={item.code}
            withBorder
            padding="sm"
            data-testid="forge-item"
            data-code={item.code}
            data-name={item.name}
          >
            <Group justify="space-between" wrap="nowrap" align="flex-start">
              <Stack gap={4}>
                <Text fw={600}>{item.name}</Text>
                <Text size="xs" c="dimmed" data-testid="forge-item-slot">
                  {ru.forge.slots[item.slot]}
                </Text>
                <Group gap="sm" data-testid="forge-item-cost">
                  {cost.map((unit) => (
                    <Group
                      key={unit.code}
                      gap={4}
                      wrap="nowrap"
                      data-testid="forge-cost"
                      data-code={unit.code}
                      data-quantity={unit.quantity}
                    >
                      <ResourceIcon code={unit.code} size={18} />
                      <Text size="sm">
                        {unit.quantity} {names.get(unit.code) ?? unit.code}
                      </Text>
                    </Group>
                  ))}
                </Group>
                {lacking.length > 0 && (
                  <Group gap="xs">
                    <Text size="xs" c="red" data-testid="forge-item-missing">
                      {ru.forge.missing(describe(lacking))}
                    </Text>
                    <Button size="compact-xs" variant="light" onClick={() => setExchanging(lacking)} data-testid="forge-item-exchange">
                      {ru.exchange.open}
                    </Button>
                  </Group>
                )}
              </Stack>
              <Button
                size="xs"
                disabled={lacking.length > 0}
                onClick={() => setConfirming(item)}
                style={{ flexShrink: 0 }}
                data-testid="forge-item-create"
              >
                {ru.forge.create}
              </Button>
            </Group>
          </Card>
        );
      })}

      <Modal opened={confirming !== null} onClose={() => setConfirming(null)} title={ru.forge.confirmTitle} centered>
        {confirming !== null && (
          <Stack gap="md" data-testid="forge-confirm">
            <Text>{ru.forge.confirmText(confirming.name, level, hunter.playerName)}</Text>
            <Text size="sm" c="dimmed">
              {ru.forge.confirmCost(describe(craftCost(board.element, confirming, level)))}
            </Text>
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setConfirming(null)} data-testid="forge-confirm-cancel">
                {ru.forge.cancel}
              </Button>
              <Button loading={craft.isPending} onClick={() => create(confirming)} data-testid="forge-confirm-ok">
                {ru.forge.create}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
      <ExchangeDialog
        opened={exchanging !== null}
        onClose={() => setExchanging(null)}
        campaignId={campaignId}
        hunters={hunters}
        hunterId={hunter.id}
        dictionaries={dictionaries}
        actions={actions}
        lacking={exchanging ?? []}
      />
    </Stack>
  );
}
