import type { ForgeBoard, ForgeItem, HunterSheet } from '../../api/generated/primal.schemas';

/** Ресурс цены: код стихии или материи и количество. */
export interface CostUnit {
  code: string;
  quantity: number;
}

/** Цена создания на уровне кузни: 1 стихия кузни и материи планшета (правила, «Кузня»). */
export function craftCost(element: string, item: ForgeItem, level: number): CostUnit[] {
  const materials = item.costs.find((cost) => cost.level === level)?.materials ?? {};
  return [{ code: element, quantity: 1 }, ...Object.entries(materials).map(([code, quantity]) => ({ code, quantity }))];
}

/** Чего не хватает охотнику для цены; пусто — хватает всего. */
export function missing(cost: CostUnit[], resources: HunterSheet['resources']): CostUnit[] {
  return cost
    .map(({ code, quantity }) => ({ code, quantity: quantity - (resources[code] ?? 0) }))
    .filter((unit) => unit.quantity > 0);
}

/** Предметы планшета для охотника: оружие только его класса, шлем, доспех и предметы — всем. */
export function itemsFor(board: ForgeBoard, hunterClass: string): ForgeItem[] {
  return board.items.filter((item) => item.hunterClass === null || item.hunterClass === hunterClass);
}
