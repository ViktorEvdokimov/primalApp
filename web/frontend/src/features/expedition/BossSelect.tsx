import { Image, NativeSelect } from '@mantine/core';
import type { Boss } from '../../api/generated/primal.schemas';
import { iconUrl } from '../../shared/assets';
import { ru } from '../../shared/i18n/ru';
import { bossLabel } from '../battle/bossCatalog';

/** Значение списка для ручного ввода. */
export const MANUAL_BOSS = '';

interface BossSelectProps {
  bosses: Boss[];
  elementNames: ReadonlyMap<string, string>;
  value: string;
  onChange: (code: string) => void;
  /** Пролог и финальный бой: босс задан, выбрать другого нельзя. */
  disabled?: boolean;
  testId?: string;
}

/**
 * Выбор босса: «Ввести данные вручную» первым, затем боссы в порядке каталога, Пробуждённый последним.
 * Нативный список — на телефоне открывается системный выбор.
 */
export function BossSelect({ bosses, elementNames, value, onChange, disabled, testId = 'expedition-boss' }: BossSelectProps) {
  const selected = bosses.find((boss) => boss.code === value);
  const icon = selected?.element ? iconUrl(selected.element) : null;
  return (
    <NativeSelect
      label={ru.expedition.boss}
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      data={[
        { value: MANUAL_BOSS, label: ru.expedition.manual },
        ...bosses.map((boss) => ({ value: boss.code, label: bossLabel(boss, elementNames) })),
      ]}
      leftSection={icon === null ? undefined : <Image src={icon} alt="" w={20} h={20} />}
      disabled={disabled}
      data-testid={testId}
    />
  );
}
