import { describe, expect, it } from 'vitest';
import { effectsFromJson, effectsToJson, newEffect, problems, type JsonEffect } from './effects';

describe('Язык эффектов ⇄ модель формы', () => {
  it('задание 1 «Память пустыни»: ресурсы и «если глава 1 или 2 — задание 4, иначе 6» — туда и обратно без потерь', () => {
    // подготовка
    const json: JsonEffect[] = [
      { resources: { BONES: 2, ZLATIA: 2, NILLEA: 2 } },
      { if: { chapterIn: [1, 2] }, then: [{ openQuest: 4 }], else: [{ openQuest: 6 }] },
    ];

    // вызов
    const model = effectsFromJson(json);

    // проверка
    expect(model[0]).toEqual({ kind: 'resources', items: [['BONES', 2], ['ZLATIA', 2], ['NILLEA', 2]], expansion: null });
    expect(model[1]?.kind).toBe('if');
    expect(effectsToJson(model)).toEqual(json);
  });

  it('вложенные условия, флаги, карты наград, сообщение и пометка дополнения', () => {
    // подготовка
    const json: JsonEffect[] = [
      {
        if: { all: [{ achievement: 'ZATISHE' }, { not: { questAvailable: 18 } }] },
        then: [{ if: { any: [{ achievement: 'GERBARIY' }] }, then: [{ hunterKitUpgrade: true }] }],
      },
      { openQuest: 36, expansion: 'FEATHER' },
      { expireQuests: [1, 3] },
      { expireAllQuests: true },
      { forgeLevelUp: true },
      { labLevelUp: true },
      { rewardCards: ['1', '7'] },
      { message: 'Получите награду 25' },
      { finalBattle: 'AWAKENED' },
      { grantAchievement: 'OLEDENENIE' },
    ];

    // вызов и проверка
    expect(effectsToJson(effectsFromJson(json))).toEqual(json);
  });

  it('незаполненные поля считаются: пустой ресурс, задание без номера, условие без достижения', () => {
    // подготовка
    const model = [newEffect('resources'), newEffect('openQuest'), newEffect('if'), newEffect('forgeLevelUp')];

    // вызов и проверка: ресурсов нет, номера нет, достижение условия не выбрано; флаг полей не требует
    expect(problems(model)).toBe(3);
    expect(problems(effectsFromJson([{ openQuest: 4 }]))).toBe(0);
  });
});
