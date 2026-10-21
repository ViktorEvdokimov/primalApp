import type { ForgeBoard, LabPotion } from '../../api/generated/primal.schemas';
import { cardCodes, equipmentCard } from '../../shared/cards/equipmentCards';
import { ru } from '../../shared/i18n/ru';
import { itemType, type ItemType } from '../forge/forge';

/** Вкладки: снаряжение (базовое и всех кузниц) и зелья лаборатории. */
export const EQUIPMENT_TAB = 'EQUIPMENT';
export const POTIONS_TAB = 'POTIONS';
export const EQUIPMENT_TABS = [EQUIPMENT_TAB, POTIONS_TAB];

/**
 * Предмет раздела «Экипировка»: [slot] — вид (ForgeSlot), у зелья `null`; [element] — кузня (у базового снаряжения
 * и зелий `null`); [hunterClass] — класс, для которого оружие (у остального `null` — подходит всем).
 */
export interface EquipmentItem {
  code: string;
  name: string;
  slot: string | null;
  element: string | null;
  hunterClass: string | null;
}

/** Виды по порядку ForgeSlot: оружие классов, шлем, доспех, предмет. */
const SLOTS = Object.keys(ru.forge.slots);

/**
 * Предметы вкладки. Снаряжение — сначала базовое (стартовый набор, в каталогах его нет — берётся по картам), затем
 * кузни по стихиям в порядке каталога, внутри кузни — как на её планшете (оружие классов, шлем, доспех, предметы).
 */
export function tabItems(tab: string, boards: ForgeBoard[], potions: LabPotion[]): EquipmentItem[] {
  if (tab === POTIONS_TAB) {
    return potions.map((potion) => ({
      code: potion.code,
      name: potion.name,
      slot: null,
      element: null,
      hunterClass: null,
    }));
  }
  const forged: EquipmentItem[] = boards.flatMap((board) =>
    board.items.map((item) => ({
      code: item.code,
      name: item.name,
      slot: item.slot,
      element: board.element,
      hunterClass: item.hunterClass,
    })),
  );
  const basic: EquipmentItem[] = SLOTS.filter((slot) => cardCodes.includes(slot)).map((slot) => ({
    code: slot,
    name: equipmentCard(slot, 1)?.name ?? slot,
    slot,
    element: null,
    // класс базового оружия — как у оружия кузниц того же вида
    hunterClass: forged.find((item) => item.slot === slot)?.hunterClass ?? null,
  }));
  return [...basic, ...forged];
}

/** Фильтры: тип (у зелий типа нет) и класс — отбирается только оружие, шлемы, доспехи и предметы подходят всем. */
export function filterItems(
  items: EquipmentItem[],
  type: ItemType | null,
  hunterClass: string | null,
): EquipmentItem[] {
  return items.filter(
    (item) =>
      (type === null || item.slot === null || itemType({ slot: item.slot }) === type) &&
      (hunterClass === null || item.hunterClass === null || item.hunterClass === hunterClass),
  );
}
