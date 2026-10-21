import {
  Alert,
  Chip,
  Group,
  Image,
  Loader,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Tabs,
  Text,
  UnstyledButton,
} from '@mantine/core';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useDictionaries, useForge, useLab } from '../../api/generated/catalog/catalog';
import { CardDialog } from '../../shared/cards/CardDialog';
import { equipmentCard, type EquipmentCard } from '../../shared/cards/equipmentCards';
import { ru } from '../../shared/i18n/ru';
import { ResourceIcon } from '../../shared/ui/ResourceStock';
import { ITEM_TYPES } from '../forge/forge';
import { Frame } from './InfoPage';
import { EQUIPMENT_TAB, EQUIPMENT_TABS, filterItems, POTIONS_TAB, tabItems } from './equipment';

const CATALOG_STALE_MS = 60 * 60 * 1000;
const LEVELS = [1, 2, 3];

/**
 * «Инфо» → «Экипировка» (qa № 154): все карты из файлов игры — вкладки «Снаряжение» (базовое и всех кузниц) и
 * «Зелья»; фильтры по типу, классу (только оружие) и уровню. Вкладка и фильтры — в адресе
 * (`?type=WEAPON&class=KARA&level=2`).
 */
export function EquipmentPage() {
  const forge = useForge({ query: { staleTime: CATALOG_STALE_MS } });
  const lab = useLab({ query: { staleTime: CATALOG_STALE_MS } });
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_MS } });
  const [params, setParams] = useSearchParams();
  const [described, setDescribed] = useState<EquipmentCard | null>(null);

  if ((forge.isError && forge.data === undefined) || (lab.isError && lab.data === undefined)) {
    return (
      <Frame title={ru.equipment.title}>
        <Alert color="red">{ru.equipment.loadError}</Alert>
      </Frame>
    );
  }
  if (forge.data === undefined || lab.data === undefined || dictionaries.data === undefined) {
    return (
      <Frame title={ru.equipment.title}>
        <Loader />
      </Frame>
    );
  }

  const names = new Map(
    [...dictionaries.data.elements, ...dictionaries.data.hunterClasses].map((item) => [item.code, item.name]),
  );
  const classes = dictionaries.data.hunterClasses;
  const tab = EQUIPMENT_TABS.find((item) => item === params.get('tab')) ?? EQUIPMENT_TAB;
  const potions = tab === POTIONS_TAB;
  const type = ITEM_TYPES.find((item) => item === params.get('type')) ?? null;
  const hunterClass = classes.find((item) => item.code === params.get('class'))?.code ?? null;
  const level = LEVELS.find((item) => String(item) === params.get('level')) ?? 1;
  // Базовое снаряжение — только 1-го уровня: на 2-м и 3-м его карт нет
  const items = filterItems(tabItems(tab, forge.data, lab.data), potions ? null : type, potions ? null : hunterClass)
    .map((item) => ({ item, card: equipmentCard(item.code, level) }))
    .filter(({ card }) => card !== undefined);
  const update = (key: string, value: string | null) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value === null) next.delete(key);
        else next.set(key, value);
        return next;
      },
      { replace: true },
    );

  return (
    <Frame title={ru.equipment.title}>
      <Tabs value={tab} onChange={(value) => update('tab', value === EQUIPMENT_TAB ? null : value)}>
        <Tabs.List>
          {EQUIPMENT_TABS.map((code) => (
            <Tabs.Tab key={code} value={code} data-testid="equipment-tab" data-tab={code}>
              {code === POTIONS_TAB ? ru.equipment.potions : ru.equipment.equipment}
            </Tabs.Tab>
          ))}
        </Tabs.List>
      </Tabs>
      {!potions && (
        // У зелий нет ни типа, ни класса — эти фильтры на их вкладке не показываются
        <>
          <SegmentedControl
            fullWidth
            size="xs"
            value={type ?? 'ALL'}
            onChange={(next) => update('type', next === 'ALL' ? null : next)}
            aria-label={ru.equipment.type}
            data={['ALL', ...ITEM_TYPES].map((value) => ({
              value,
              label: (
                <span data-testid="equipment-type-option" data-type={value}>
                  {ru.forge.types[value]}
                </span>
              ),
            }))}
            data-testid="equipment-type"
          />
          <Chip.Group
            multiple={false}
            value={hunterClass ?? 'ALL'}
            onChange={(next) => update('class', next === 'ALL' ? null : next)}
          >
            <Group gap={6} data-testid="equipment-class">
              {[{ code: 'ALL', name: ru.equipment.allClasses }, ...classes].map((item) => (
                <Chip key={item.code} value={item.code} size="xs">
                  <span data-testid="equipment-class-option" data-class={item.code}>
                    {item.name}
                  </span>
                </Chip>
              ))}
            </Group>
          </Chip.Group>
        </>
      )}
      <SegmentedControl
        fullWidth
        size="xs"
        value={String(level)}
        onChange={(next) => update('level', next === '1' ? null : next)}
        aria-label={ru.equipment.level}
        data={LEVELS.map((value) => ({
          value: String(value),
          label: (
            <span data-testid="equipment-level-option" data-level={value}>
              {ru.equipment.levelOption(value)}
            </span>
          ),
        }))}
        data-testid="equipment-level"
      />
      {!potions && level > 1 && (
        <Text size="xs" c="dimmed" data-testid="equipment-basic-level">
          {ru.equipment.basicLevel}
        </Text>
      )}
      {items.length === 0 && (
        <Text c="dimmed" data-testid="equipment-nothing">
          {ru.equipment.nothing}
        </Text>
      )}
      <SimpleGrid cols={{ base: 2, xs: 3, md: 4 }} spacing="sm" verticalSpacing="md">
        {items.map(({ item, card }) => {
          const title = ru.cards.title(item.name, level);
          const kind = item.slot === null ? ru.equipment.potion : (ru.forge.slots[item.slot] ?? item.slot);
          const source =
            item.slot === null ? null : item.element === null ? ru.equipment.basic : names.get(item.element);
          return (
            <Stack key={item.code} gap={4} data-testid="equipment-item" data-code={item.code} data-name={item.name}>
              <UnstyledButton onClick={() => setDescribed(card ?? null)} aria-label={title}>
                <Image src={card?.url} alt={title} loading="lazy" radius="sm" data-testid="equipment-image" />
              </UnstyledButton>
              <Group gap={4} wrap="nowrap" align="flex-start">
                {item.element !== null && <ResourceIcon code={item.element} size={14} />}
                <Text size="xs" c="dimmed" data-testid="equipment-caption">
                  {source === null || source === undefined ? kind : `${source} · ${kind}`}
                </Text>
              </Group>
            </Stack>
          );
        })}
      </SimpleGrid>
      <CardDialog card={described} onClose={() => setDescribed(null)} />
    </Frame>
  );
}
