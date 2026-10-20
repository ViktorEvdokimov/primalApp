import { Checkbox, Stack } from '@mantine/core';
import { useSetMainHunter } from '../../api/generated/campaigns/campaigns';
import type { Dictionaries, HunterSheet, MainHunterRequestHunterClass } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { HunterSwitch } from './HunterSwitch';
import { useHunterSelection } from './hunterSelection';
import { InventoryPanel } from './InventoryPanel';
import { ResourcesGrid } from './ResourcesGrid';
import { SkillTree } from './SkillTree';
import type { SheetActions } from './useCampaignSheet';

interface HuntersTabProps {
  campaignId: number;
  hunters: HunterSheet[];
  /** основной персонаж участника — выбран по умолчанию и выделен жирным */
  mainHunter: string | null | undefined;
  dictionaries: Dictionaries;
  actions: SheetActions;
}

/**
 * Охотники отряда: переключатель (основной персонаж — по умолчанию и жирным), «Основной персонаж», древо навыков,
 * ресурсы и инвентарь выбранного охотника.
 */
export function HuntersTab({ campaignId, hunters, mainHunter, dictionaries, actions }: HuntersTabProps) {
  const { hunter, select } = useHunterSelection(hunters, mainHunter);
  const setMain = useSetMainHunter();
  const classNames = new Map(dictionaries.hunterClasses.map((item) => [item.code, item.name]));
  if (hunter === undefined) return null;
  const isMain = hunter.class === mainHunter;

  const toggleMain = (checked: boolean) => {
    const hunterClass = checked ? (hunter.class as MainHunterRequestHunterClass) : null;
    setMain
      .mutateAsync({ campaignId, data: { hunterClass } })
      .then((result) => actions.mainHunterChanged(result.mainHunter), actions.failed);
  };

  return (
    <Stack gap="lg">
      <Stack gap="xs">
        <HunterSwitch
          hunters={hunters}
          mainHunter={mainHunter}
          value={hunter.id}
          onChange={select}
          classNames={classNames}
          testIdPrefix="hunter"
          classAttribute="name"
        />
        <Checkbox
          label={ru.sheet.mainHunter}
          description={ru.sheet.mainHunterHint}
          checked={isMain}
          disabled={setMain.isPending}
          onChange={(event) => toggleMain(event.currentTarget.checked)}
          data-testid="main-hunter"
        />
      </Stack>
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
