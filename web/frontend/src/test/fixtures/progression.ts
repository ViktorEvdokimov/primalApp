import type { BattleSetup, ResultPreview, TransitionPreview } from '../../api/generated/primal.schemas';
import { createLocalBattle, type BattlePurpose, type LocalBattle } from '../../domain/battle';
import { questItem } from './campaign';

/** Подготовка к бою кампании 12 в главе 1: задания 1 и 2 открыты, бой ещё не выбран. */
export function setupFixture(overrides: Partial<BattleSetup> = {}): BattleSetup {
  return {
    campaignId: 12,
    chapter: 1,
    progressSeq: 1,
    purpose: 'FREE',
    difficulty: 1,
    hunterCount: 2,
    openQuests: [questItem(1), questItem(2)],
    boss: null,
    stances: [],
    forcedBoss: null,
    activeBattles: [],
    ...overrides,
  };
}

/** Пролог: Вираксен без выбора, сложность 0. */
export function prologueSetupFixture(): BattleSetup {
  const viraxen = { code: 'VIRAXEN', name: 'Вираксен', element: 'FIRE' };
  return setupFixture({ chapter: 0, progressSeq: 0, purpose: 'PROLOGUE', difficulty: 0, openQuests: [], boss: viraxen, forcedBoss: viraxen });
}

/** Законченный бой кампании 12 в браузере; результат не отправлен. */
export function finishedCampaignBattle(
  result: 'VICTORY' | 'DEFEAT' = 'VICTORY',
  purpose: BattlePurpose = 'QUEST',
): LocalBattle {
  const battle = createLocalBattle({
    id: '0b8f6c1e-8a51-4a3e-9d7c-2f0f6a3f9b11',
    mode: 'CAMPAIGN',
    campaign: {
      id: 12,
      name: 'SheetTest',
      chapter: purpose === 'PROLOGUE' ? 0 : 2,
      progressSeq: 1,
      purpose,
      questNumber: purpose === 'QUEST' ? 1 : null,
      startMarked: true,
    },
    boss: purpose === 'PROLOGUE'
      ? { code: 'VIRAXEN', name: 'Вираксен', element: 'FIRE' }
      : { code: 'KOROVON', name: 'Коровон', element: 'CORAL' },
    difficulty: 1,
    stances: [],
    params: { hunterCount: 2, toughnessPerHunter: 4, stanceChange: { mode: 'HEALTH', atHealth: 7 } },
    now: '2026-09-27T18:35:00.000Z',
  });
  return {
    ...battle,
    state: {
      ...battle.state,
      status: result,
      defeatReason: result === 'DEFEAT' ? 'SURRENDER' : null,
      round: 4,
    },
    finishedAt: '2026-09-27T19:05:00.000Z',
  };
}

export function resultPreviewFixture(overrides: Partial<ResultPreview> = {}): ResultPreview {
  return {
    result: 'VICTORY',
    purpose: 'QUEST',
    quest: { number: 1, name: 'Вой в долине' },
    rewards: {
      trophy: { code: 'KOROVON', name: 'Коровон', element: 'CORAL' },
      perHunter: { CORAL: 2, BONES: 2 },
      achievements: [],
      openQuests: [4],
      rewardCards: [],
      messages: [],
    },
    rules: [{ kind: 'QUEST', description: 'Если текущая глава 1 или 2, то добавить задание 4', result: 'Добавлено задание 4.' }],
    next: 'CHAPTER_TRANSITION',
    otherActiveBattles: [],
    dismissConsequences: [
      'Задание 1 «Вой в долине» не будет отмечено выполненным.',
      'Трофей «Коровон» не будет получен.',
      'Награды можно внести вручную на листе кампании.',
    ],
    ...overrides,
  };
}

/** Переход в главу 7 с решением «Тренироваться у Волтьяра»; {@code selected} — ответ из запроса. */
export function transitionFixture(selected: string | null = null): TransitionPreview {
  return {
    fromChapter: 6,
    toChapter: 7,
    version: 44,
    decisions: [
      {
        code: 'TRAIN_WITH_VOLTYAR',
        question: 'Хотите ли вы тренироваться в лагере у Волтьяра?',
        options: [
          { code: 'YES', label: 'Да' },
          { code: 'NO', label: 'Нет' },
        ],
        selected,
      },
    ],
    decisionsComplete: selected !== null,
    perHunter: {},
    openQuests: [],
    expireQuests: [
      { number: 7, wasOpen: true },
      { number: 9, wasOpen: false },
    ],
    achievements: selected === 'YES' ? [{ code: 'GOLOS_VOLTYARA', name: 'Голос Волтьяра' }] : [],
    forgeLevelUp: false,
    labLevelUp: false,
    hunterKitUpgrade: true,
    rewardCards: [],
    messages: [],
    finalBattle: null,
    rules: [],
    rejectConsequences: [
      'Глава останется 6. После следующей победы переход в главу 7 будет предложен снова.',
      'Не истечёт время задания 7.',
      'Главу, задания и достижения можно изменить вручную на листе кампании.',
    ],
  };
}
