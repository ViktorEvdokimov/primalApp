import { describe, expect, it } from 'vitest';
import { protectionEntry, reactionEntry, staminaEntry, stoneEntry } from '../../test/fixtures/info';
import { entryLabel, matches, paragraphs, resolver, segments, titleKey } from './infoModel';

const resolve = resolver([protectionEntry, staminaEntry, stoneEntry]);

describe('Ссылки «См. также»', () => {
  it('название сравнивается без регистра, «ё», оформления и пустых скобок значков', () => {
    // вызов и проверка
    expect(titleKey('**Защита / жетон защиты ( )**')).toBe('защита / жетон защиты');
    expect(titleKey('Счёт')).toBe('счет');
  });

  it('статья: точное название, первая часть до « / » или « (», начало названия; неизвестное — нет', () => {
    // вызов и проверка
    expect(resolve('Защита / жетон защиты')?.id).toBe(1);
    expect(resolve('Защита')?.id).toBe(1);
    expect(resolve('Выносливость')?.id).toBe(2);
    expect(resolve('Неизвестное слово')).toBeNull();
  });

  it('в «См. также» найденные названия — ссылки, ненайденные и остальной текст — текст, **жирный** — жирный', () => {
    // вызов
    const parts = segments('Сбросьте **карту**. (См. также **«Защита»** , «Неизвестное слово» .)', resolve);

    // проверка
    expect(parts).toEqual([
      { kind: 'text', text: 'Сбросьте ' },
      { kind: 'bold', text: 'карту' },
      { kind: 'text', text: '. (См. также ' },
      { kind: 'link', text: '«Защита»', entry: protectionEntry },
      { kind: 'text', text: ' , «Неизвестное слово» .)' },
    ]);
  });

  it('название со скобками внутри ссылки: «Выносливость (выработка выносливости) »', () => {
    // вызов
    const parts = segments('(См. также «Выносливость (выработка выносливости) » , «Камень» .)', resolve);

    // проверка
    expect(parts.filter((part) => part.kind === 'link').map((part) => part.kind === 'link' && part.entry.id)).toEqual([
      2, 3,
    ]);
  });

  it('«ёлочки» вне «См. также» — просто текст', () => {
    // вызов и проверка
    expect(segments('Слово «Защита» в тексте.', resolve)).toEqual([{ kind: 'text', text: 'Слово «Защита» в тексте.' }]);
  });
});

describe('Текст и поиск', () => {
  it('абзацы — через пустую строку, переносы внутри абзаца — пробел', () => {
    // вызов и проверка
    expect(paragraphs('Первый\nабзац.\n\n  Второй.  \n\n')).toEqual(['Первый абзац.', 'Второй.']);
  });

  it('поиск: все слова в названии или тексте, «ё» как «е»', () => {
    // вызов и проверка
    expect(matches(staminaEntry, 'сбросьте выносливость')).toBe(true);
    expect(matches(protectionEntry, 'жетоны складываются')).toBe(true);
    expect(matches(protectionEntry, 'камень')).toBe(false);
    expect(matches(stoneEntry, '')).toBe(true);
  });
});

describe('Символ реакции без названия', () => {
  it('подпись — начало описания без разметки; на него не ссылаются', () => {
    // вызов и проверка
    expect(entryLabel(reactionEntry)).toBe('Монстр разворачивается к охотнику. (См. также «Защита» .)');
    expect(entryLabel(reactionEntry, 20)).toBe('Монстр разворачивае…');
    expect(entryLabel(stoneEntry)).toBe('Камень');
    expect(resolver([reactionEntry])('Монстр')).toBeNull();
    expect(matches(reactionEntry, 'разворачивается')).toBe(true);
  });
});
