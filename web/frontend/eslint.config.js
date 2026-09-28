import js from '@eslint/js';
import { defineConfig, globalIgnores } from 'eslint/config';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Правила боя живут в src/domain: чистый TypeScript без React, сети и глобальных объектов браузера
 * (doc/battle.md §1). Хранилище и прочее окружение передаются туда параметрами.
 */
export const domainRestrictions = {
  'no-restricted-imports': [
    'error',
    {
      patterns: [
        {
          group: ['react', 'react/*', 'react-*', 'react-*/*', '@mantine/*', '@tanstack/*'],
          message: 'src/domain — чистый TypeScript: без React и UI-библиотек.',
        },
        {
          group: ['**/api', '**/api/**', '**/features/**', '**/app/**', '**/shared/**'],
          message: 'src/domain не зависит от остального приложения.',
        },
      ],
    },
  ],
  'no-restricted-globals': [
    'error',
    ...['window', 'document', 'localStorage', 'sessionStorage', 'navigator', 'fetch'].map((name) => ({
      name,
      message: 'src/domain не обращается к окружению браузера — передайте зависимость параметром.',
    })),
  ],
};

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'src/api/generated']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat['recommended-latest']],
    plugins: { 'react-refresh': reactRefresh },
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    rules: {
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    files: ['src/domain/**/*.ts'],
    ignores: ['src/domain/**/*.test.ts'],
    rules: domainRestrictions,
  },
  {
    files: ['*.config.{js,ts}', 'src/test/**/*.ts'],
    languageOptions: { globals: globals.node },
  },
]);
