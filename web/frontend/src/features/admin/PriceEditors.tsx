import { ActionIcon, Button, Card, Checkbox, Group, MultiSelect, NumberInput, Select, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useResetForgeCost, useResetLabCost, useUpdateForgeCost, useUpdateLabCost } from '../../api/generated/admin/admin';
import type { AdminCatalog, AdminForgeItem, AdminLabPotion, Dictionaries, LabUnit } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { useCatalogCache, useSaveFlow } from './adminHooks';
import { Actions, EditedBadge } from './adminShared';

type Option = { value: string; label: string };

/** Материи уровня цены: материя → количество; пустая строка — материя не выбрана. */
type Level = [string, number][];

const levelsOf = (item: AdminForgeItem): Level[] =>
  item.costs.map((cost) => Object.entries(cost.materials).map(([code, quantity]) => [code, quantity] as [string, number]));

const levelProblems = (levels: Level[]) =>
  levels.reduce((sum, level) => sum + (level.length === 0 || level.some(([code, n]) => code === '' || !(n > 0)) ? 1 : 0), 0);

/** Цены кузни (qa № 138): планшет стихии, у каждого предмета — материи на 3 уровня (кроме них — 1 стихия кузни). */
export function ForgeEditor({ catalog, dictionaries }: { catalog: AdminCatalog; dictionaries: Dictionaries }) {
  const elements = dictionaries.elements.filter((element) => catalog.forge.some((item) => item.element === element.code));
  const [element, setElement] = useState(elements[0]?.code ?? 'FIRE');
  const materials = dictionaries.materials.map((item) => ({ value: item.code, label: item.name }));
  return (
    <Stack gap="md">
      <Select
        label={ru.admin.forgeElement}
        value={element}
        data={elements.map((item) => ({ value: item.code, label: item.name }))}
        onChange={(value) => value !== null && setElement(value)}
        allowDeselect={false}
        data-testid="admin-forge-element"
      />
      <Text size="xs" c="dimmed">
        {ru.admin.forgeHint}
      </Text>
      {catalog.forge
        .filter((item) => item.element === element)
        .map((item) => (
          <ForgeItemForm key={`${item.code}-${String(item.edited)}`} item={item} materials={materials} />
        ))}
    </Stack>
  );
}

function ForgeItemForm({ item, materials }: { item: AdminForgeItem; materials: Option[] }) {
  const cache = useCatalogCache();
  const update = useUpdateForgeCost();
  const reset = useResetForgeCost();
  const flow = useSaveFlow(cache.forge);
  const [levels, setLevels] = useState<Level[]>(() => levelsOf(item));
  const dirty = JSON.stringify(levels) !== JSON.stringify(levelsOf(item));
  const setLevel = (index: number, level: Level) => setLevels(levels.map((current, i) => (i === index ? level : current)));

  return (
    <Card withBorder padding="sm" data-testid="admin-forge-item" data-code={item.code}>
      <Stack gap="xs">
        <Group gap="xs">
          <Text fw={600}>{item.name}</Text>
          <Text size="xs" c="dimmed">
            {ru.forge.slots[item.slot]}
          </Text>
          <EditedBadge edited={item.edited} />
        </Group>
        <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="xs">
          {levels.map((level, index) => (
            <Stack key={index} gap={4} data-testid="admin-forge-level" data-level={index + 1}>
              <Text size="xs" fw={600}>
                {ru.admin.level(index + 1)}
              </Text>
              {level.map(([code, quantity], row) => (
                <Group key={row} gap={4} wrap="nowrap">
                  <Select
                    size="xs"
                    aria-label={ru.admin.material}
                    placeholder={ru.admin.material}
                    value={code || null}
                    data={materials.filter((m) => m.value === code || !level.some(([other]) => other === m.value))}
                    onChange={(value) => setLevel(index, level.map((entry, i) => (i === row ? [value ?? '', entry[1]] : entry)))}
                    style={{ flex: 1 }}
                    data-testid="admin-forge-material"
                  />
                  <NumberInput
                    size="xs"
                    aria-label={ru.admin.quantity}
                    min={1}
                    max={4}
                    w={56}
                    value={quantity}
                    onChange={(value) => setLevel(index, level.map((entry, i) => (i === row ? [entry[0], Number(value)] : entry)))}
                    data-testid="admin-forge-quantity"
                  />
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    aria-label={ru.admin.remove}
                    onClick={() => setLevel(index, level.filter((_, i) => i !== row))}
                  >
                    ×
                  </ActionIcon>
                </Group>
              ))}
              <Button
                size="compact-xs"
                variant="subtle"
                style={{ alignSelf: 'flex-start' }}
                onClick={() => setLevel(index, [...level, ['', 1]])}
                data-testid="admin-forge-add"
              >
                + {ru.admin.material}
              </Button>
            </Stack>
          ))}
        </SimpleGrid>
        <Actions
          error={flow.error}
          missing={levelProblems(levels)}
          dirty={dirty}
          edited={item.edited}
          saving={update.isPending}
          onSave={() =>
            void flow.save(
              update.mutateAsync({ code: item.code, data: { costs: levels.map((level) => Object.fromEntries(level)) } }),
            )
          }
          onDiscard={() => {
            setLevels(levelsOf(item));
            flow.clearError();
          }}
          onReset={() => flow.reset(reset.mutateAsync({ code: item.code }))}
        />
      </Stack>
    </Card>
  );
}

const unitProblems = (units: LabUnit[]) =>
  (units.length === 0 ? 1 : 0) + units.filter((unit) => !unit.any && unit.options.length === 0).length;

/** Цены лаборатории (qa № 138): растения зелья по одному — конкретное, на выбор или любое. */
export function LabEditor({ catalog, dictionaries }: { catalog: AdminCatalog; dictionaries: Dictionaries }) {
  const plants = dictionaries.plants.map((item) => ({ value: item.code, label: item.name }));
  return (
    <Stack gap="md">
      <Title order={4}>{ru.lab.title}</Title>
      <Text size="xs" c="dimmed">
        {ru.lab.hint}
      </Text>
      {catalog.lab.map((potion) => (
        <LabPotionForm key={`${potion.code}-${String(potion.edited)}`} potion={potion} plants={plants} />
      ))}
    </Stack>
  );
}

function LabPotionForm({ potion, plants }: { potion: AdminLabPotion; plants: Option[] }) {
  const cache = useCatalogCache();
  const update = useUpdateLabCost();
  const reset = useResetLabCost();
  const flow = useSaveFlow(cache.lab);
  const [units, setUnits] = useState<LabUnit[]>(potion.units);
  const dirty = JSON.stringify(units) !== JSON.stringify(potion.units);
  const setUnit = (index: number, unit: LabUnit) => setUnits(units.map((current, i) => (i === index ? unit : current)));

  return (
    <Card withBorder padding="sm" data-testid="admin-lab-potion" data-code={potion.code}>
      <Stack gap="xs">
        <Group gap="xs">
          <Text fw={600}>{potion.name}</Text>
          <EditedBadge edited={potion.edited} />
        </Group>
        {units.map((unit, index) => (
          <Group key={index} gap="xs" wrap="nowrap" data-testid="admin-lab-unit">
            <Text size="sm" w={80}>
              {ru.admin.plant(index + 1)}
            </Text>
            <Checkbox
              label={ru.lab.anyPlant}
              checked={unit.any}
              onChange={(event) =>
                setUnit(index, { any: event.currentTarget.checked, options: event.currentTarget.checked ? [] : unit.options })
              }
              data-testid="admin-lab-any"
            />
            <MultiSelect
              size="xs"
              aria-label={ru.admin.plants}
              placeholder={unit.any ? '' : ru.admin.plantsChoice}
              disabled={unit.any}
              value={unit.options}
              data={plants}
              onChange={(value) => setUnit(index, { any: false, options: value as LabUnit['options'] })}
              style={{ flex: 1 }}
              data-testid="admin-lab-options"
            />
            <ActionIcon
              variant="subtle"
              color="red"
              aria-label={ru.admin.remove}
              disabled={units.length === 1}
              onClick={() => setUnits(units.filter((_, i) => i !== index))}
            >
              ×
            </ActionIcon>
          </Group>
        ))}
        <Button
          size="compact-xs"
          variant="subtle"
          style={{ alignSelf: 'flex-start' }}
          disabled={units.length >= 4}
          onClick={() => setUnits([...units, { any: true, options: [] }])}
          data-testid="admin-lab-add"
        >
          + {ru.admin.addPlant}
        </Button>
        <Actions
          error={flow.error}
          missing={unitProblems(units)}
          dirty={dirty}
          edited={potion.edited}
          saving={update.isPending}
          onSave={() => void flow.save(update.mutateAsync({ code: potion.code, data: { units } }))}
          onDiscard={() => {
            setUnits(potion.units);
            flow.clearError();
          }}
          onReset={() => flow.reset(reset.mutateAsync({ code: potion.code }))}
        />
      </Stack>
    </Card>
  );
}
