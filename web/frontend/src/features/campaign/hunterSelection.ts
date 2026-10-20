import { useState } from 'react';
import type { HunterSheet } from '../../api/generated/primal.schemas';

/** «Алиса · Дареон»; имя игрока совпадает с названием класса — только оно. */
export function hunterLabel(playerName: string, className: string): string {
  return playerName === className ? playerName : `${playerName} · ${className}`;
}

/**
 * Выбранный охотник листа, кузницы или лаборатории: пока игрок сам не выбрал — основной персонаж
 * (`mainHunter`, qa № 150), без него — первый в отряде.
 */
export function useHunterSelection(hunters: HunterSheet[], mainHunter: string | null | undefined) {
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const hunter =
    hunters.find((item) => item.id === selectedId) ?? hunters.find((item) => item.class === mainHunter) ?? hunters[0];
  return { hunter, select: setSelectedId };
}
