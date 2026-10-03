import type { Boss, Dictionaries, ForgeBoard, ForgeItem, LabPotion, LabUnit } from '../../api/generated/primal.schemas';

/** Ответы API каталога для тестов — фрагмент настоящего каталога (backend/src/main/resources/catalog). */
export const bossesFixture: Boss[] = [
  {
    code: 'KOROVON',
    name: 'Коровон',
    element: 'CORAL',
    expansion: null,
    sortOrder: 1,
    difficulties: {
      '0': [
        { stance: 1, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
        { stance: 2, toughnessPerHunter: null, stanceChange: { mode: 'ON_DEMAND', atHealth: null } },
        { stance: 3, toughnessPerHunter: 4, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '1': [
        { stance: 1, toughnessPerHunter: 6, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
        { stance: 2, toughnessPerHunter: null, stanceChange: { mode: 'ON_DEMAND', atHealth: null } },
        { stance: 3, toughnessPerHunter: 8, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '2': [
        { stance: 1, toughnessPerHunter: 13, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
        { stance: 2, toughnessPerHunter: null, stanceChange: { mode: 'ON_DEMAND', atHealth: null } },
        { stance: 3, toughnessPerHunter: 16, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '3': [
        { stance: 1, toughnessPerHunter: 20, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
        { stance: 2, toughnessPerHunter: null, stanceChange: { mode: 'ON_DEMAND', atHealth: null } },
        { stance: 3, toughnessPerHunter: 25, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
    },
  },
  {
    code: 'VIRAXEN',
    name: 'Вираксен',
    element: 'FIRE',
    expansion: null,
    sortOrder: 13,
    difficulties: {
      '0': [
        { stance: 1, toughnessPerHunter: 2, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        { stance: 2, toughnessPerHunter: 3, stanceChange: { mode: 'HEALTH', atHealth: 3 } },
        { stance: 3, toughnessPerHunter: 4, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '1': [
        { stance: 1, toughnessPerHunter: 5, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        { stance: 2, toughnessPerHunter: 7, stanceChange: { mode: 'HEALTH', atHealth: 3 } },
        { stance: 3, toughnessPerHunter: 10, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '2': [
        { stance: 1, toughnessPerHunter: 10, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        { stance: 2, toughnessPerHunter: 15, stanceChange: { mode: 'HEALTH', atHealth: 3 } },
        { stance: 3, toughnessPerHunter: 20, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
      '3': [
        { stance: 1, toughnessPerHunter: 18, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
        { stance: 2, toughnessPerHunter: 24, stanceChange: { mode: 'HEALTH', atHealth: 3 } },
        { stance: 3, toughnessPerHunter: 30, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
    },
  },
  {
    code: 'AWAKENED',
    name: 'Пробуждённый',
    element: null,
    expansion: null,
    sortOrder: 23,
    difficulties: {
      '3': [
        { stance: 1, toughnessPerHunter: 30, stanceChange: { mode: 'HEALTH', atHealth: 8 } },
        { stance: 2, toughnessPerHunter: 40, stanceChange: { mode: 'HEALTH', atHealth: 6 } },
        { stance: 3, toughnessPerHunter: 50, stanceChange: { mode: 'HEALTH', atHealth: 4 } },
        { stance: 4, toughnessPerHunter: 60, stanceChange: { mode: 'HEALTH', atHealth: 2 } },
        { stance: 5, toughnessPerHunter: 60, stanceChange: { mode: 'FINAL', atHealth: null } },
      ],
    },
  },
];

export const dictionariesFixture: Dictionaries = {
  elements: [
    { code: 'FIRE', name: 'Огонь', expansion: null },
    { code: 'HORN', name: 'Рог', expansion: null },
    { code: 'CORAL', name: 'Коралл', expansion: null },
    { code: 'CRYSTAL', name: 'Кристалл', expansion: null },
    { code: 'LIGHTNING', name: 'Молния', expansion: null },
    { code: 'METAL', name: 'Металл', expansion: null },
    { code: 'FEATHER', name: 'Перо', expansion: 'FEATHER' },
    { code: 'POISON', name: 'Яд', expansion: 'POISON' },
    { code: 'ICE', name: 'Лёд', expansion: 'ICE' },
  ],
  materials: [
    { code: 'SCALES', name: 'Чешуя', expansion: null },
    { code: 'BONES', name: 'Кости', expansion: null },
    { code: 'BLOOD', name: 'Кровь', expansion: null },
    { code: 'ZIMIA', name: 'Зимия', expansion: null },
    { code: 'IRIDIA', name: 'Иридия', expansion: null },
    { code: 'ZLATIA', name: 'Златия', expansion: null },
  ],
  plants: [
    { code: 'NILLEA', name: 'Ниллея', expansion: null },
    { code: 'TARMARET', name: 'Тармарет', expansion: null },
    { code: 'ALBALACEA', name: 'Альбалацея', expansion: null },
    { code: 'MELLIS', name: 'Меллис', expansion: null },
    { code: 'ANTHEMON', name: 'Антемон', expansion: null },
    { code: 'SELICORNIA', name: 'Селикорния', expansion: null },
  ],
  hunterClasses: [
    { code: 'DAREON', name: 'Дареон', expansion: null },
    { code: 'MIRA', name: 'Мира', expansion: null },
    { code: 'TOREG', name: 'Торег', expansion: null },
    { code: 'LIONAR', name: 'Льонар', expansion: null },
    { code: 'KARA', name: 'Кара', expansion: null },
    { code: 'HELEREN', name: 'Хелерен', expansion: null },
    { code: 'DRUSK', name: 'Друск', expansion: null },
    { code: 'ZARAIA', name: 'Зарайа', expansion: null },
  ],
  skillBranches: [
    { code: 'A', name: 'А', expansion: null },
    { code: 'B', name: 'Б', expansion: null },
    { code: 'V', name: 'В', expansion: null },
    { code: 'G', name: 'Г', expansion: null },
    { code: 'D', name: 'Д', expansion: null },
  ],
  difficultyByChapter: [
    { chapters: [0], difficulty: 0 },
    { chapters: [1, 2, 3], difficulty: 1 },
    { chapters: [4, 5, 6, 7], difficulty: 2 },
    { chapters: [8, 9, 10, 11], difficulty: 3 },
  ],
};

type Costs = [Record<string, number>, Record<string, number>, Record<string, number>];

function forgeItem(code: string, name: string, slot: ForgeItem['slot'], hunterClass: ForgeItem['hunterClass'], costs: Costs): ForgeItem {
  return { code, name, slot, hunterClass, costs: costs.map((materials, index) => ({ level: index + 1, materials })) };
}

/** Планшеты кузни: огонь целиком, как в каталоге; коралл — меч Дареона и шлем. */
export const forgeFixture: ForgeBoard[] = [
  {
    element: 'FIRE',
    items: [
      forgeItem('FIRE_01', 'Язык пламени', 'GREATSWORD', 'DAREON', [{ BONES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }, { BONES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_02', 'Лук-испепелитель', 'GREATBOW', 'MIRA', [{ SCALES: 1, BLOOD: 1 }, { BLOOD: 2 }, { BONES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_03', 'Молот пламени', 'HAMMER', 'TOREG', [{ BLOOD: 2 }, { SCALES: 1, BLOOD: 1 }, { BLOOD: 1, IRIDIA: 1 }]),
      forgeItem('FIRE_04', 'Вулканический щит', 'SWORD_AND_SHIELD', 'LIONAR', [{ BLOOD: 1, IRIDIA: 1 }, { BLOOD: 1, IRIDIA: 1 }, { BONES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_05', 'Крылья дракона', 'DUAL_BLADES', 'KARA', [{ SCALES: 1, BLOOD: 1 }, { SCALES: 1, IRIDIA: 1 }, { BLOOD: 1, IRIDIA: 1 }]),
      forgeItem('FIRE_06', 'Игнис', 'GUN', 'HELEREN', [{ BLOOD: 1, IRIDIA: 1 }, { BONES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_07', 'Магматическое копьё', 'SPEAR', 'ZARAIA', [{ SCALES: 1, BLOOD: 1 }, { SCALES: 2 }, { BLOOD: 2 }]),
      forgeItem('FIRE_08', 'Лавовый барабан', 'DRUM', 'DRUSK', [{ SCALES: 2 }, { SCALES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_09', 'Чешуйчатый шлем', 'HELMET', null, [{ SCALES: 1, BLOOD: 1 }, { BONES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_10', 'Чешуйчатый доспех', 'ARMOR', null, [{ SCALES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }]),
      forgeItem('FIRE_11', 'Перчатка Волтьяра', 'ITEM', null, [{ SCALES: 1, BLOOD: 1 }, { SCALES: 1, BLOOD: 1 }, { SCALES: 2 }]),
      forgeItem('FIRE_12', 'Лавовый щит', 'ITEM', null, [{ SCALES: 2 }, { BLOOD: 2 }, { BONES: 1, BLOOD: 1 }]),
    ],
  },
  {
    element: 'CORAL',
    items: [
      forgeItem('CORAL_01', 'Кровавый риф', 'GREATSWORD', 'DAREON', [{ SCALES: 1, ZIMIA: 1 }, { ZIMIA: 2 }, { ZIMIA: 2 }]),
      forgeItem('CORAL_09', 'Рифовый шлем', 'HELMET', null, [{ SCALES: 1, ZIMIA: 1 }, { ZIMIA: 2 }, { BONES: 1, ZIMIA: 1 }]),
    ],
  },
];

type PlantCode = LabUnit['options'][number];
const ALL_PLANTS: PlantCode[] = ['NILLEA', 'TARMARET', 'ALBALACEA', 'MELLIS', 'ANTHEMON', 'SELICORNIA'];
const one = (...options: PlantCode[]): LabUnit => ({ options, any: false });
const any: LabUnit = { options: ALL_PLANTS, any: true };

/** Планшет лаборатории — как в каталоге. */
export const labFixture: LabPotion[] = [
  { code: 'LAB_01', name: 'Алемор', units: [any, any] },
  { code: 'LAB_02', name: 'Имперум', units: [one('ANTHEMON'), one('NILLEA')] },
  { code: 'LAB_03', name: 'Ирден', units: [one('TARMARET'), one('ALBALACEA', 'SELICORNIA')] },
  { code: 'LAB_04', name: 'Хатрокс', units: [one('NILLEA'), one('SELICORNIA')] },
  { code: 'LAB_05', name: 'Эвок', units: [one('TARMARET'), one('ANTHEMON', 'MELLIS')] },
  { code: 'LAB_06', name: 'Видья', units: [one('ALBALACEA'), one('MELLIS')] },
];

