import { SegmentedControl, Stack } from '@mantine/core';
import { useState } from 'react';
import type { Dictionaries, HunterSheet } from '../../api/generated/primal.schemas';
import { InventoryPanel } from './InventoryPanel';
import { ResourcesGrid } from './ResourcesGrid';
import { SkillTree } from './SkillTree';
import type { SheetActions } from './useCampaignSheet';

interface HuntersTabProps {
  campaignId: number;
  hunters: HunterSheet[];
  dictionaries: Dictionaries;
  actions: SheetActions;
}

/** Охотники отряда: переключатель, древо навыков, ресурсы и инвентарь выбранного охотника. */
export function HuntersTab({ campaignId, hunters, dictionaries, actions }: HuntersTabProps) {
  const [selectedId, setSelectedId] = useState(hunters[0]?.id);
  const hunter = hunters.find((item) => item.id === selectedId) ?? hunters[0];
  const classNames = new Map(dictionaries.hunterClasses.map((item) => [item.code, item.name]));
  if (hunter === undefined) return null;

  return (
    <Stack gap="lg">
      <SegmentedControl
        fullWidth
        orientation={hunters.length > 3 ? 'vertical' : 'horizontal'}
        value={String(hunter.id)}
        onChange={(value) => setSelectedId(Number(value))}
        data={hunters.map((item) => ({
          value: String(item.id),
          label: (
            <span data-testid="hunter-option" data-player={item.playerName} data-class={classNames.get(item.class) ?? item.class}>
              {item.playerName === classNames.get(item.class)
                ? item.playerName
                : `${item.playerName} · ${classNames.get(item.class) ?? item.class}`}
            </span>
          ),
        }))}
        data-testid="hunter-switch"
      />
      <SkillTree campaignId={campaignId} hunter={hunter} branches={dictionaries.skillBranches} actions={actions} />
      {/* key: при смене охотника накопленные +/− прежнего уходят на сервер сразу */}
      <ResourcesGrid
        key={hunter.id}
        campaignId={campaignId}
        hunter={hunter}
        materials={dictionaries.materials}
        plants={dictionaries.plants}
        elements={dictionaries.elements}
        actions={actions}
      />
      <InventoryPanel campaignId={campaignId} hunter={hunter} hunters={hunters} dictionaries={dictionaries} actions={actions} />
    </Stack>
  );
}
