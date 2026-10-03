import type { Dictionaries } from '../../api/generated/primal.schemas';

/** Тип ресурса для правила обмена «1 к 1 внутри типа». */
export type ResourceKind = 'ELEMENT' | 'MATERIAL' | 'PLANT';

/** Тип каждого кода ресурса по справочнику. */
export function kindsOf(dictionaries: Dictionaries): Map<string, ResourceKind> {
  return new Map<string, ResourceKind>([
    ...dictionaries.elements.map((item) => [item.code, 'ELEMENT'] as const),
    ...dictionaries.materials.map((item) => [item.code, 'MATERIAL'] as const),
    ...dictionaries.plants.map((item) => [item.code, 'PLANT'] as const),
  ]);
}
