// @vitest-environment node
import { ESLint } from 'eslint';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const projectRoot = fileURLToPath(new URL('../..', import.meta.url));

async function lintDomainFile(code: string) {
  const eslint = new ESLint({ cwd: projectRoot });
  const [result] = await eslint.lintText(code, { filePath: `${projectRoot}/src/domain/battle/violation.ts` });
  return result!.messages.map((message) => message.ruleId);
}

describe('Границы src/domain (правила боя — чистый TypeScript)', () => {
  it('импорт React запрещён', async () => {
    // подготовка
    const code = "import { useState } from 'react';\nexport const x = useState;\n";

    // вызов
    const rules = await lintDomainFile(code);

    // проверка
    expect(rules).toContain('no-restricted-imports');
  });

  it('импорт API-клиента и других частей приложения запрещён', async () => {
    // подготовка
    const code = "import { apiFetch } from '../../api/http';\nexport const x = apiFetch;\n";

    // вызов
    const rules = await lintDomainFile(code);

    // проверка
    expect(rules).toContain('no-restricted-imports');
  });

  it('обращение к window и localStorage запрещено', async () => {
    // подготовка
    const code = "export const x = () => localStorage.getItem('battle') ?? window.name;\n";

    // вызов
    const rules = await lintDomainFile(code);

    // проверка
    expect(rules.filter((rule) => rule === 'no-restricted-globals')).toHaveLength(2);
  });

  it('чистый TypeScript проходит без замечаний', async () => {
    // подготовка
    const code = 'export function wounds(damage: number, toughness: number): number {\n  return Math.floor(damage / toughness);\n}\n';

    // вызов
    const rules = await lintDomainFile(code);

    // проверка
    expect(rules).toEqual([]);
  });
});
