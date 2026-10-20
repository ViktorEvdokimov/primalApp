import { SegmentedControl } from '@mantine/core';
import type { HunterSheet } from '../../api/generated/primal.schemas';
import { hunterLabel } from './hunterSelection';

interface HunterSwitchProps {
  hunters: HunterSheet[];
  mainHunter: string | null | undefined;
  value: number;
  onChange: (hunterId: number) => void;
  classNames: Map<string, string>;
  /** `hunter`, `forge-hunter`, `lab-hunter` → `…-switch` и `…-option` */
  testIdPrefix: string;
  /** что в `data-class` варианта: код класса или его название */
  classAttribute?: 'code' | 'name';
}

/** Переключатель охотников отряда; основной персонаж выделен жирным. */
export function HunterSwitch({
  hunters,
  mainHunter,
  value,
  onChange,
  classNames,
  testIdPrefix,
  classAttribute = 'code',
}: HunterSwitchProps) {
  return (
    <SegmentedControl
      fullWidth
      orientation={hunters.length > 3 ? 'vertical' : 'horizontal'}
      value={String(value)}
      onChange={(next) => onChange(Number(next))}
      data={hunters.map((item) => {
        const className = classNames.get(item.class) ?? item.class;
        const main = item.class === mainHunter;
        return {
          value: String(item.id),
          label: (
            <span
              data-testid={`${testIdPrefix}-option`}
              data-player={item.playerName}
              data-class={classAttribute === 'name' ? className : item.class}
              data-main={main}
              style={main ? { fontWeight: 700 } : undefined}
            >
              {hunterLabel(item.playerName, className)}
            </span>
          ),
        };
      })}
      data-testid={`${testIdPrefix}-switch`}
    />
  );
}
