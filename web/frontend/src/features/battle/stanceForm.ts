import { invalidValueMessages, type StanceChange, type StanceDef } from '../../domain/battle';

/**
 * Поля стойки как их вводит игрок: подготовка боя (стойка I) и окно «Смена стойки!».
 * Прочность — за охотника; пустая прочность — стойка без порога раны.
 */
export interface StanceFormValues {
  toughness: string;
  mode: StanceChange['mode'];
  atHealth: string;
}

export interface StanceFormErrors {
  toughness?: string;
  atHealth?: string;
}

export type ParsedStance =
  | { ok: true; toughnessPerHunter: number | null; stanceChange: StanceChange }
  | { ok: false; errors: StanceFormErrors };

/** Ручной ввод на подготовке — значения app: прочность 4 за охотника, смена стойки при 7. */
export const MANUAL_STANCE: StanceFormValues = { toughness: '4', mode: 'HEALTH', atHealth: '7' };

/** Пустые поля — стойки нет в каталоге. Как в app: пусто — нет порога раны и смена по запросу. */
export const EMPTY_STANCE: StanceFormValues = { toughness: '', mode: 'ON_DEMAND', atHealth: '' };

export function stanceFormFrom(def: StanceDef | null): StanceFormValues {
  if (def === null) return EMPTY_STANCE;
  return {
    toughness: def.toughnessPerHunter === null ? '' : String(def.toughnessPerHunter),
    mode: def.stanceChange.mode,
    atHealth: def.stanceChange.mode === 'HEALTH' ? String(def.stanceChange.atHealth) : '',
  };
}

export function parseStanceForm(values: StanceFormValues): ParsedStance {
  const errors: StanceFormErrors = {};
  let toughnessPerHunter: number | null = null;
  if (values.toughness.trim() !== '') {
    const toughness = parseWholeNumber(values.toughness);
    if (toughness === null || toughness < 1) errors.toughness = invalidValueMessages.toughness;
    else toughnessPerHunter = toughness;
  }

  let stanceChange: StanceChange = { mode: 'ON_DEMAND' };
  if (values.mode === 'HEALTH') {
    const atHealth = parseWholeNumber(values.atHealth);
    if (atHealth === null || atHealth < 1 || atHealth > 9) errors.atHealth = invalidValueMessages.atHealth;
    else stanceChange = { mode: 'HEALTH', atHealth };
  } else if (values.mode === 'FINAL') {
    stanceChange = { mode: 'FINAL' };
  }

  if (errors.toughness !== undefined || errors.atHealth !== undefined) return { ok: false, errors };
  return { ok: true, toughnessPerHunter, stanceChange };
}

/** Число охотников — целое больше 0, верхней границы нет (D-12). */
export function parseHunterCount(text: string): number | null {
  const count = parseWholeNumber(text);
  return count !== null && count > 0 ? count : null;
}

/** Целое неотрицательное число без знаков и дробей; иначе `null`. */
export function parseWholeNumber(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return Number.isSafeInteger(value) ? value : null;
}
