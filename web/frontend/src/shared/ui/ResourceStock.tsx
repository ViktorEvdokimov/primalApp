import { Group, Image, Text } from '@mantine/core';
import { iconUrl } from '../assets';
import { ru } from '../i18n/ru';

/** Иконка стихии, материи или растения по коду каталога; нет иконки — ничего. */
export function ResourceIcon({ code, size = 20 }: { code: string; size?: number }) {
  const url = iconUrl(code);
  return url === null ? null : <Image src={url} alt="" w={size} h={size} fit="contain" />;
}

interface ResourceStockProps {
  /** Запас охотника: код → количество (нулевые сервер не передаёт). */
  resources: Record<string, number>;
  /** Какие ресурсы показывать и в каком порядке. */
  codes: string[];
  names: Map<string, string>;
  /** Префикс data-testid: `forge` → `forge-stock`, `forge-stock-item`. */
  testIdPrefix: string;
}

/** «Запас:» — ненулевые ресурсы охотника с иконками (кузница, лаборатория). */
export function ResourceStock({ resources, codes, names, testIdPrefix }: ResourceStockProps) {
  const owned = codes.filter((code) => (resources[code] ?? 0) > 0);
  return (
    <Group gap="xs" data-testid={`${testIdPrefix}-stock`}>
      <Text size="sm" c="dimmed">
        {ru.forge.stock}
      </Text>
      {owned.length === 0 ? (
        <Text size="sm" c="dimmed">
          {ru.forge.stockEmpty}
        </Text>
      ) : (
        owned.map((code) => (
          <Group key={code} gap={4} wrap="nowrap" data-testid={`${testIdPrefix}-stock-item`} data-code={code}>
            <ResourceIcon code={code} size={18} />
            <Text size="sm">
              {names.get(code) ?? code} {resources[code]}
            </Text>
          </Group>
        ))
      )}
    </Group>
  );
}
