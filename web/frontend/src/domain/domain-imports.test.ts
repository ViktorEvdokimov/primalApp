import { describe, expect, it } from 'vitest';

/** Исходники src/domain без тестов: путь от этой папки → текст файла. */
const sources = import.meta.glob<string>(['./**/*.ts', '!./**/*.test.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const IMPORT = /(?:import|export)\s[^'"]*?from\s*['"]([^'"]+)['"]|import\s*\(?\s*['"]([^'"]+)['"]/g;

/** Путь импорта относительно src/domain; `null` — внешний пакет. */
function resolve(file: string, specifier: string): string | null {
  if (!specifier.startsWith('.')) return null;
  const parts = file.split('/').slice(0, -1);
  for (const segment of specifier.split('/')) {
    if (segment === '..') parts.pop();
    else if (segment !== '.') parts.push(segment);
  }
  return parts.join('/');
}

/** Все импорты исходников: «файл: путь импорта». */
function imports(): { file: string; specifier: string }[] {
  return Object.entries(sources).flatMap(([file, source]) =>
    [...source.matchAll(IMPORT)].map((match) => ({ file, specifier: match[1] ?? match[2] ?? '' })),
  );
}

describe('Границы src/domain', () => {
  it('импорты исходников находятся', () => {
    // вызов
    const found = imports();

    // проверка
    expect(found).toContainEqual({ file: './battle/engine.ts', specifier: './types' });
    expect(found).toContainEqual({ file: './battle/index.ts', specifier: './engine' });
  });

  it('код src/domain импортирует только файлы src/domain', () => {
    // вызов
    const outside = imports()
      .filter(({ file, specifier }) => {
        const target = resolve(file, specifier);
        return target === null || !target.startsWith('.');
      })
      .map(({ file, specifier }) => `${file}: ${specifier}`);

    // проверка
    expect(outside).toEqual([]);
  });

  it('путь, выходящий за src/domain, считается внешним', () => {
    // вызов и проверка
    expect(resolve('./battle/engine.ts', '../../api/http')).toBe('api/http');
    expect(resolve('./battle/engine.ts', 'react')).toBeNull();
    expect(resolve('./battle/engine.ts', '../campaign/rules')).toBe('./campaign/rules');
  });
});
