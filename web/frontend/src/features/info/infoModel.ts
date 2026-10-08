import type { InfoEntry, InfoSectionViewCode } from '../../api/generated/primal.schemas';

/** Адрес раздела «Инфо»: /info/keywords… */
export const SECTION_SLUGS: Record<InfoSectionViewCode, string> = {
  KEYWORDS: 'keywords',
  REACTIONS: 'reactions',
  TOKENS: 'tokens',
};

export function sectionBySlug(slug: string | undefined): InfoSectionViewCode | null {
  const found = Object.entries(SECTION_SLUGS).find(([, value]) => value === slug);
  return found === undefined ? null : (found[0] as InfoSectionViewCode);
}

export const entryAnchor = (id: number) => `entry-${id}`;

/**
 * Разделы-карточки: картинка видна сразу, без раскрытия (символы реакций — qa № 143, жетоны окружения —
 * qa № 144). Ключевые слова — раскрывающийся список: статей много, текст длинный.
 */
export const CARD_SECTIONS: ReadonlySet<InfoSectionViewCode> = new Set(['REACTIONS', 'TOKENS']);

export const entryHref = (entry: InfoEntry) => `/info/${SECTION_SLUGS[entry.section]}#${entryAnchor(entry.id)}`;

/**
 * Название для сравнения: без регистра и оформления, «ё» как «е», без пустых скобок значков «( )» — так «Защита /
 * жетон защиты ( )» и ссылка «Защита» сходятся.
 */
export function titleKey(title: string): string {
  return title
    .replaceAll('**', '')
    .replace(/ё/g, 'е')
    .replace(/Ё/g, 'Е')
    .replace(/\(\s*[^\p{L}\p{N}(]*\s*\)/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

/**
 * Статья по ссылке «См. также «…»»: точное название; иначе — первая часть названия (до « / » или « (»):
 * «Защита» → «Защита / жетон защиты»; иначе — название, начинающееся со ссылки: «Выносливость» →
 * «Выносливость (выработка выносливости)».
 */
export function resolver(entries: InfoEntry[]): (reference: string) => InfoEntry | null {
  // Символы реакций без названия — на них не ссылаются
  const keyed = entries.flatMap((entry) => (entry.title === null ? [] : [{ entry, key: titleKey(entry.title) }]));
  return (reference) => {
    const wanted = titleKey(reference);
    if (wanted === '') return null;
    return (
      keyed.find((item) => item.key === wanted)?.entry ??
      keyed.find((item) => item.key.split(/ \/ | \(/)[0]?.trim() === wanted)?.entry ??
      keyed.find((item) => item.key.startsWith(wanted))?.entry ??
      null
    );
  };
}

/** Кусок абзаца: текст, жирный текст или ссылка на статью. */
export type Segment =
  { kind: 'text'; text: string } | { kind: 'bold'; text: string } | { kind: 'link'; text: string; entry: InfoEntry };

/** «(См. также «A» , «B (уточнение) » .)» — до «.)»: в названиях бывают свои скобки. */
const SEE_ALSO = /См\. также(.*?)\.\s*\)/g;
const QUOTED = /(\*\*)?«([^»]+)»(\*\*)?/g;
const BOLD = /\*\*(.+?)\*\*/g;

function plain(text: string): Segment[] {
  const segments: Segment[] = [];
  let last = 0;
  for (const match of text.matchAll(BOLD)) {
    if (match.index > last) segments.push({ kind: 'text', text: text.slice(last, match.index) });
    segments.push({ kind: 'bold', text: match[1] ?? '' });
    last = match.index + match[0].length;
  }
  if (last < text.length) segments.push({ kind: 'text', text: text.slice(last) });
  return segments;
}

/**
 * Абзац — на куски: в «См. также» названия в «ёлочках» становятся ссылками (если статья нашлась), остальное —
 * текст и **жирный**.
 */
export function segments(paragraph: string, resolve: (reference: string) => InfoEntry | null): Segment[] {
  const result: Segment[] = [];
  let last = 0;
  for (const seeAlso of paragraph.matchAll(SEE_ALSO)) {
    const listStart = seeAlso.index + seeAlso[0].indexOf(seeAlso[1] ?? '');
    const list = seeAlso[1] ?? '';
    for (const quoted of list.matchAll(QUOTED)) {
      const entry = resolve(quoted[2] ?? '');
      if (entry === null) continue;
      const start = listStart + quoted.index;
      result.push(...plain(paragraph.slice(last, start)));
      result.push({ kind: 'link', text: `«${(quoted[2] ?? '').trim()}»`, entry });
      last = start + quoted[0].length;
    }
  }
  result.push(...plain(paragraph.slice(last)));
  return result;
}

/** Абзацы текста статьи: разделены пустой строкой. */
export function paragraphs(body: string): string[] {
  return body
    .split(/\n\s*\n/)
    .map((paragraph) => paragraph.replace(/\s*\n\s*/g, ' ').trim())
    .filter((paragraph) => paragraph !== '');
}

/**
 * Подпись статьи в списках (поиск, администрирование): название, а у символа реакции без названия — начало
 * описания (qa № 143).
 */
export function entryLabel(entry: InfoEntry, max = 60): string {
  if (entry.title !== null) return entry.title;
  const text = paragraphs(entry.body)[0]?.replaceAll('**', '') ?? '';
  if (text === '') return `№ ${entry.id}`;
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/** Поиск: все слова запроса есть в названии или тексте (без регистра, «ё» как «е»). */
export function matches(entry: InfoEntry, query: string): boolean {
  const words = titleKey(query)
    .split(' ')
    .filter((word) => word !== '');
  if (words.length === 0) return true;
  const haystack = `${titleKey(entry.title ?? '')} ${titleKey(entry.body)}`;
  return words.every((word) => haystack.includes(word));
}
