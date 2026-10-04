/**
 * Язык эффектов каталога (`doc/data-model.md` §4.3) для редактора наград: JSON сервера ⇄ модель формы.
 * Вид эффекта определяется ключом (`openQuest`, `if`…), пометка `expansion` — общая для всех видов.
 */

export type JsonEffect = { [key: string]: unknown };

export type ConditionModel =
  | { kind: 'achievement'; achievement: string }
  | { kind: 'chapterIn'; chapters: number[] }
  | { kind: 'questAvailable'; quest: number | null }
  | { kind: 'expansion'; expansion: string }
  | { kind: 'not'; condition: ConditionModel }
  | { kind: 'all' | 'any'; conditions: ConditionModel[] };

export type ConditionKind = ConditionModel['kind'];

type EffectBody =
  | { kind: 'resources'; items: [string, number][] }
  | { kind: 'openQuest'; quest: number | null }
  | { kind: 'expireQuests'; quests: number[] }
  | { kind: 'expireAllQuests' }
  | { kind: 'grantAchievement'; achievement: string }
  | { kind: 'forgeLevelUp' }
  | { kind: 'labLevelUp' }
  | { kind: 'hunterKitUpgrade' }
  | { kind: 'rewardCards'; cards: string[] }
  | { kind: 'message'; text: string }
  | { kind: 'finalBattle'; boss: string }
  | { kind: 'if'; condition: ConditionModel; then: EffectModel[]; otherwise: EffectModel[] };

/** Эффект формы; `expansion` — пометка дополнения (на расчёт не влияет). */
export type EffectModel = EffectBody & { expansion: string | null };

export type EffectKind = EffectModel['kind'];

export const EFFECT_KINDS: EffectKind[] = [
  'resources',
  'openQuest',
  'grantAchievement',
  'rewardCards',
  'message',
  'expireQuests',
  'expireAllQuests',
  'forgeLevelUp',
  'labLevelUp',
  'hunterKitUpgrade',
  'finalBattle',
  'if',
];

export const CONDITION_KINDS: ConditionKind[] = ['achievement', 'chapterIn', 'questAvailable', 'expansion', 'not', 'all', 'any'];

export function newCondition(kind: ConditionKind): ConditionModel {
  switch (kind) {
    case 'achievement':
      return { kind, achievement: '' };
    case 'chapterIn':
      return { kind, chapters: [] };
    case 'questAvailable':
      return { kind, quest: null };
    case 'expansion':
      return { kind, expansion: '' };
    case 'not':
      return { kind, condition: newCondition('achievement') };
    case 'all':
    case 'any':
      return { kind, conditions: [newCondition('achievement')] };
  }
}

export function newEffect(kind: EffectKind): EffectModel {
  const base = { expansion: null };
  switch (kind) {
    case 'resources':
      return { ...base, kind, items: [] };
    case 'openQuest':
      return { ...base, kind, quest: null };
    case 'expireQuests':
      return { ...base, kind, quests: [] };
    case 'grantAchievement':
      return { ...base, kind, achievement: '' };
    case 'rewardCards':
      return { ...base, kind, cards: [] };
    case 'message':
      return { ...base, kind, text: '' };
    case 'finalBattle':
      return { ...base, kind, boss: '' };
    case 'if':
      return { ...base, kind, condition: newCondition('achievement'), then: [], otherwise: [] };
    case 'expireAllQuests':
    case 'forgeLevelUp':
    case 'labLevelUp':
    case 'hunterKitUpgrade':
      return { ...base, kind };
  }
}

const numbers = (value: unknown): number[] => (Array.isArray(value) ? value.map(Number) : []);
const strings = (value: unknown): string[] => (Array.isArray(value) ? value.map(String) : []);

export function conditionFromJson(json: unknown): ConditionModel {
  const node = (json ?? {}) as Record<string, unknown>;
  const [kind, value] = Object.entries(node)[0] ?? ['achievement', ''];
  switch (kind) {
    case 'chapterIn':
      return { kind, chapters: numbers(value) };
    case 'questAvailable':
      return { kind, quest: Number(value) };
    case 'expansion':
      return { kind, expansion: String(value ?? '') };
    case 'not':
      return { kind, condition: conditionFromJson(value) };
    case 'all':
    case 'any':
      return { kind, conditions: (Array.isArray(value) ? value : []).map(conditionFromJson) };
    default:
      return { kind: 'achievement', achievement: String(value ?? '') };
  }
}

export function effectFromJson(json: JsonEffect): EffectModel {
  const expansion = typeof json.expansion === 'string' ? json.expansion : null;
  if ('if' in json) {
    return {
      kind: 'if',
      condition: conditionFromJson(json.if),
      then: effectsFromJson(json.then),
      otherwise: effectsFromJson(json.else),
      expansion,
    };
  }
  const [kind, value] = Object.entries(json).find(([key]) => key !== 'expansion') ?? ['message', ''];
  switch (kind) {
    case 'resources':
      return { kind, items: Object.entries((value ?? {}) as Record<string, number>).map(([code, n]) => [code, Number(n)]), expansion };
    case 'openQuest':
      return { kind, quest: Number(value), expansion };
    case 'expireQuests':
      return { kind, quests: numbers(value), expansion };
    case 'grantAchievement':
      return { kind, achievement: String(value), expansion };
    case 'rewardCards':
      return { kind, cards: strings(value), expansion };
    case 'finalBattle':
      return { kind, boss: String(value), expansion };
    case 'expireAllQuests':
    case 'forgeLevelUp':
    case 'labLevelUp':
    case 'hunterKitUpgrade':
      return { kind, expansion };
    default:
      return { kind: 'message', text: String(value ?? ''), expansion };
  }
}

export function effectsFromJson(json: unknown): EffectModel[] {
  return Array.isArray(json) ? (json as JsonEffect[]).map(effectFromJson) : [];
}

export function conditionToJson(condition: ConditionModel): JsonEffect {
  switch (condition.kind) {
    case 'achievement':
      return { achievement: condition.achievement };
    case 'chapterIn':
      return { chapterIn: condition.chapters };
    case 'questAvailable':
      return { questAvailable: condition.quest };
    case 'expansion':
      return { expansion: condition.expansion };
    case 'not':
      return { not: conditionToJson(condition.condition) };
    case 'all':
    case 'any':
      return { [condition.kind]: condition.conditions.map(conditionToJson) };
  }
}

export function effectToJson(effect: EffectModel): JsonEffect {
  const json = effectBodyToJson(effect);
  return effect.expansion === null ? json : { ...json, expansion: effect.expansion };
}

function effectBodyToJson(effect: EffectModel): JsonEffect {
  switch (effect.kind) {
    case 'resources':
      return { resources: Object.fromEntries(effect.items) };
    case 'openQuest':
      return { openQuest: effect.quest };
    case 'expireQuests':
      return { expireQuests: effect.quests };
    case 'grantAchievement':
      return { grantAchievement: effect.achievement };
    case 'rewardCards':
      return { rewardCards: effect.cards };
    case 'message':
      return { message: effect.text };
    case 'finalBattle':
      return { finalBattle: effect.boss };
    case 'if': {
      const json: JsonEffect = { if: conditionToJson(effect.condition), then: effectsToJson(effect.then) };
      return effect.otherwise.length === 0 ? json : { ...json, else: effectsToJson(effect.otherwise) };
    }
    case 'expireAllQuests':
    case 'forgeLevelUp':
    case 'labLevelUp':
    case 'hunterKitUpgrade':
      return { [effect.kind]: true };
  }
}

export function effectsToJson(effects: EffectModel[]): JsonEffect[] {
  return effects.map(effectToJson);
}

/** Незаполненные поля формы — подсказка до отправки; остальное проверяет сервер. */
export function problems(effects: EffectModel[]): number {
  return effects.reduce((sum, effect) => sum + effectProblems(effect), 0);
}

function effectProblems(effect: EffectModel): number {
  switch (effect.kind) {
    case 'resources':
      return effect.items.length === 0 || effect.items.some(([code, n]) => code === '' || !(n > 0)) ? 1 : 0;
    case 'openQuest':
      return effect.quest === null || !(effect.quest > 0) ? 1 : 0;
    case 'expireQuests':
      return effect.quests.length === 0 ? 1 : 0;
    case 'grantAchievement':
      return effect.achievement === '' ? 1 : 0;
    case 'rewardCards':
      return effect.cards.length === 0 ? 1 : 0;
    case 'message':
      return effect.text.trim() === '' ? 1 : 0;
    case 'finalBattle':
      return effect.boss === '' ? 1 : 0;
    case 'if':
      return conditionProblems(effect.condition) + problems(effect.then) + problems(effect.otherwise);
    default:
      return 0;
  }
}

function conditionProblems(condition: ConditionModel): number {
  switch (condition.kind) {
    case 'achievement':
      return condition.achievement === '' ? 1 : 0;
    case 'chapterIn':
      return condition.chapters.length === 0 ? 1 : 0;
    case 'questAvailable':
      return condition.quest === null || !(condition.quest > 0) ? 1 : 0;
    case 'expansion':
      return condition.expansion === '' ? 1 : 0;
    case 'not':
      return conditionProblems(condition.condition);
    case 'all':
    case 'any':
      return condition.conditions.length === 0 ? 1 : condition.conditions.reduce((s, c) => s + conditionProblems(c), 0);
  }
}
