/** Картинки из мобильного приложения (app/androidApp/src/main/res). */
const icons = import.meta.glob<string>('./icons/*.png', { eager: true, import: 'default' });
const reactionDecks = import.meta.glob<string>('./reaction-deck/*.png', { eager: true, import: 'default' });

/** Иконка стихии, материи или растения по коду каталога: FIRE, BONES, MELLIS. */
export function iconUrl(code: string): string | null {
  return icons[`./icons/${code.toLowerCase()}.png`] ?? null;
}

/** Колода карт реакций уровня 0–3. */
export function reactionDeckUrl(level: number): string {
  const clamped = Math.min(3, Math.max(0, Math.trunc(level)));
  const url = reactionDecks[`./reaction-deck/level-${clamped}.png`];
  if (url === undefined) throw new Error(`Нет картинки колоды реакций уровня ${clamped}`);
  return url;
}
