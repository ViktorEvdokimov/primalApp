import manifest from './cards.json';

/** Карта снаряжения из файлов игры (web/cards/split_cards.py, qa № 151, 152): оружие, шлем, доспех, предмет, зелье. */
export interface EquipmentCard {
  /** код предмета кузни (`FIRE_05`), зелья (`LAB_01`) или вида базового снаряжения (`DUAL_BLADES`, `HELMET`) */
  code: string;
  level: number;
  name: string;
  url: string;
}

const cards = new Map<string, EquipmentCard>(
  manifest.cards.map((card) => [
    `${card.code}-${card.level}`,
    { code: card.code, level: card.level, name: card.name, url: `/cards/${card.file}` },
  ]),
);

/** Карта этого уровня; карты нет в файлах игры — `undefined`. */
export function equipmentCard(code: string, level: number): EquipmentCard | undefined {
  return cards.get(`${code}-${level}`);
}
