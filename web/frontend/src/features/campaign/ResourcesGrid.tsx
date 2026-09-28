import { ActionIcon, Group, Image, SimpleGrid, Stack, Text } from '@mantine/core';
import type { HunterSheet, Named } from '../../api/generated/primal.schemas';
import { iconUrl } from '../../shared/assets';
import { ru } from '../../shared/i18n/ru';
import type { SheetActions } from './useCampaignSheet';
import { useResourceChanges } from './useResourceChanges';

interface ResourcesGridProps {
  campaignId: number;
  hunter: HunterSheet;
  materials: Named[];
  plants: Named[];
  elements: Named[];
  actions: SheetActions;
}

/**
 * Ресурсы охотника — 6 материй, 6 растений и 9 стихий с иконками и кнопками +/− по 1, минимум 0
 * (behavior.md app §7). Нулевые ресурсы сервер не передаёт.
 */
export function ResourcesGrid({ campaignId, hunter, materials, plants, elements, actions }: ResourcesGridProps) {
  const resources = useResourceChanges(campaignId, hunter.id, hunter.resources, actions);

  const section = (title: string, items: Named[], testId: string) => (
    <Stack gap={6} data-testid={testId}>
      <Text fw={600}>{title}</Text>
      <SimpleGrid cols={{ base: 1, xs: 2, sm: 3 }} spacing="xs" verticalSpacing={6}>
        {items.map((item) => {
          const value = resources.value(item.code);
          const icon = iconUrl(item.code);
          return (
            <Group key={item.code} gap="xs" wrap="nowrap" data-testid="resource" data-code={item.code} data-name={item.name}>
              {icon && <Image src={icon} alt="" w={24} h={24} fit="contain" />}
              <Text size="sm" style={{ flex: 1 }}>
                {item.name}
              </Text>
              <ActionIcon
                variant="default"
                aria-label={ru.sheet.decrement(item.name)}
                disabled={value <= 0}
                onClick={() => resources.change(item.code, -1)}
                data-testid="resource-decrement"
              >
                −
              </ActionIcon>
              <Text w={28} ta="center" fw={600} data-testid="resource-value">
                {value}
              </Text>
              <ActionIcon
                variant="default"
                aria-label={ru.sheet.increment(item.name)}
                onClick={() => resources.change(item.code, 1)}
                data-testid="resource-increment"
              >
                +
              </ActionIcon>
            </Group>
          );
        })}
      </SimpleGrid>
    </Stack>
  );

  return (
    <Stack gap="md" data-testid="resources" data-saving={resources.saving}>
      {section(ru.sheet.materials, materials, 'resources-materials')}
      {section(ru.sheet.plants, plants, 'resources-plants')}
      {section(ru.sheet.elements, elements, 'resources-elements')}
    </Stack>
  );
}
