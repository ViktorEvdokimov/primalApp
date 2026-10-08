import type { InfoEntry, InfoView } from '../../api/generated/primal.schemas';

/** Статьи «Инфо» для тестов: текст свой, в той же разметке, что статьи из правил. */
export const protectionEntry: InfoEntry = {
  id: 1,
  section: 'KEYWORDS',
  title: 'Защита / жетон защиты ( )',
  body: 'Жетон снимает одну единицу урона.\n\n**Примечание.** Жетоны складываются.',
  imageUrl: null,
};

export const staminaEntry: InfoEntry = {
  id: 2,
  section: 'KEYWORDS',
  title: 'Выносливость (выработка выносливости)',
  body: 'Сбросьте карту, чтобы получить выносливость.\n\n(См. также **«Защита»** , «Неизвестное слово» .)',
  imageUrl: '/api/v1/info/images/0b8f0000-0000-4000-8000-000000000001',
};

export const stoneEntry: InfoEntry = {
  id: 3,
  section: 'TOKENS',
  title: 'Камень',
  body: 'Препятствие на поле. (См. также «Выносливость» .)',
  imageUrl: null,
};

/** Символ реакции: в правилах только картинка и описание (qa № 143). */
export const reactionEntry: InfoEntry = {
  id: 4,
  section: 'REACTIONS',
  title: null,
  body: 'Монстр **разворачивается** к охотнику. (См. также «Защита» .)',
  imageUrl: '/api/v1/info/images/0b8f0000-0000-4000-8000-000000000004',
};

export function infoFixture(): InfoView {
  return {
    sections: [
      { code: 'KEYWORDS', title: 'Ключевые слова', titled: true, entries: [staminaEntry, protectionEntry] },
      { code: 'REACTIONS', title: 'Символы реакций монстров', titled: false, entries: [] },
      { code: 'TOKENS', title: 'Жетоны окружения', titled: true, entries: [stoneEntry] },
    ],
    version: 'v1',
  };
}
