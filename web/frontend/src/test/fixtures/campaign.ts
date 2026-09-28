import type { Achievement, CampaignSheet, HunterSheet, Quest } from '../../api/generated/primal.schemas';

const noRewards = { resources: {}, openQuests: [], achievements: [], rewardCards: [], messages: [], rules: [] };

/** Часть каталога заданий: базовые задания и одно из дополнения. */
export const questsFixture: Quest[] = [
  { number: 1, name: 'Вой в долине', boss: { code: 'ZHAR_PTITSA', name: 'Жар-птица', element: 'FIRE' }, expansion: null, victory: noRewards, defeat: noRewards },
  { number: 2, name: 'Каменный сон', boss: { code: 'GROMOVOLK', name: 'Громоволк', element: 'LIGHTNING' }, expansion: null, victory: noRewards, defeat: noRewards },
  { number: 3, name: 'Старые кости', boss: { code: 'GROMOVOLK', name: 'Громоволк', element: 'LIGHTNING' }, expansion: null, victory: noRewards, defeat: noRewards },
  { number: 4, name: 'Пепел', boss: { code: 'ZHAR_PTITSA', name: 'Жар-птица', element: 'FIRE' }, expansion: null, victory: noRewards, defeat: noRewards },
  { number: 5, name: 'Тихая вода', boss: { code: 'GROMOVOLK', name: 'Громоволк', element: 'LIGHTNING' }, expansion: null, victory: noRewards, defeat: noRewards },
  { number: 40, name: 'Перья бури', boss: { code: 'ZHAR_PTITSA', name: 'Жар-птица', element: 'FIRE' }, expansion: 'FEATHER', victory: noRewards, defeat: noRewards },
];

export const achievementsFixture: Achievement[] = [
  { code: 'ZATISHE', name: 'Затишье' },
  { code: 'GERBARIY', name: 'Гербарий' },
  { code: 'GOLOS_VOLTYARA', name: 'Голос Волтьяра' },
];

export function hunterFixture(overrides: Partial<HunterSheet> = {}): HunterSheet {
  return {
    id: 31,
    class: 'DAREON',
    playerName: 'Боец',
    position: 1,
    skills: [],
    unlockableSkills: (['A', 'B', 'V', 'G', 'D'] as const).map((branch) => ({ branch, tier: 1 })),
    resources: {},
    ...overrides,
  };
}

/** Задание листа по каталогу {@link questsFixture}. */
export function questItem(number: number, closedInChapter: number | null = null) {
  const item = questsFixture.find((candidate) => candidate.number === number);
  if (item === undefined) throw new Error(`Нет задания ${number} в questsFixture`);
  return { number, name: item.name, boss: item.boss, closedInChapter };
}

/** Лист новой кампании Алисы: пролог, Дареон «Боец» и Мира. */
export function sheetFixture(overrides: Partial<CampaignSheet> = {}): CampaignSheet {
  return {
    id: 12,
    name: 'SheetTest',
    version: 3,
    chapter: 0,
    status: 'ACTIVE',
    access: 'OWNER',
    ownerName: 'Алиса',
    difficulty: 0,
    forgeLevel: 1,
    labLevel: 1,
    finalBoss: null,
    notes: '',
    hunters: [hunterFixture(), hunterFixture({ id: 32, class: 'MIRA', playerName: 'Мира', position: 2 })],
    quests: { open: [], completed: [], expired: [] },
    achievements: [],
    trophies: [],
    activeBattles: [],
    recentBattles: [],
    pendingTransition: false,
    ...overrides,
  };
}
