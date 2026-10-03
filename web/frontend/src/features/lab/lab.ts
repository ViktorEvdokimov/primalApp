import type { LabUnit } from '../../api/generated/primal.schemas';

/** Код растения: NILLEA, MELLIS… */
export type PlantCode = LabUnit['options'][number];

/** Хватает ли запаса на выбранные растения (одно растение может встретиться дважды). */
export function affordable(plants: PlantCode[], resources: Record<string, number>): boolean {
  const needed = new Map<string, number>();
  plants.forEach((plant) => needed.set(plant, (needed.get(plant) ?? 0) + 1));
  return [...needed].every(([plant, count]) => (resources[plant] ?? 0) >= count);
}

/**
 * Растения по умолчанию: первое по порядку планшета сочетание, на которое хватает запаса (для «Эвока» —
 * антемон, а если его нет — меллис, как в примере правил). Не хватает ни на одно — `null`.
 */
export function defaultPlants(units: LabUnit[], resources: Record<string, number>): PlantCode[] | null {
  const search = (index: number, chosen: PlantCode[]): PlantCode[] | null => {
    if (index === units.length) return affordable(chosen, resources) ? chosen : null;
    for (const option of units[index]?.options ?? []) {
      const found = search(index + 1, [...chosen, option]);
      if (found !== null) return found;
    }
    return null;
  };
  return search(0, []);
}

/** Сколько растений каждого вида спишется: для подписи «Тармарет 1, Меллис 1». */
export function spend(plants: PlantCode[]): [PlantCode, number][] {
  const counts = new Map<PlantCode, number>();
  plants.forEach((plant) => counts.set(plant, (counts.get(plant) ?? 0) + 1));
  return [...counts];
}

/**
 * Чего не хватает на зелье для подсказки обмена: по первому варианту каждой позиции планшета;
 * «любое растение» не указывается — подойдёт любое.
 */
export function lackingPlants(units: LabUnit[], resources: Record<string, number>): { code: string; quantity: number }[] {
  const needed = new Map<string, number>();
  units
    .filter((unit) => !unit.any)
    .forEach((unit) => {
      const first = unit.options[0];
      if (first !== undefined) needed.set(first, (needed.get(first) ?? 0) + 1);
    });
  return [...needed]
    .map(([code, count]) => ({ code, quantity: count - (resources[code] ?? 0) }))
    .filter((unit) => unit.quantity > 0);
}
