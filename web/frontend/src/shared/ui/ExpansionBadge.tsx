import { Badge } from '@mantine/core';
import { ru } from '../i18n/ru';

/** Дополнение задания: «Кошмар», «Перо», «Яд», «Лёд»; у базовой игры — ничего. */
export function ExpansionBadge({ expansion }: { expansion: string | null }) {
  if (expansion === null) return null;
  return (
    <Badge size="xs" variant="light" color="grape" data-testid="quest-expansion" data-expansion={expansion}>
      {ru.expansions[expansion] ?? expansion}
    </Badge>
  );
}
