import { defineConfig } from 'orval';

/**
 * Типизированный клиент API из контракта бэкенда (springdoc, профиль dev).
 * Перегенерация: запустить бэкенд с профилем dev и выполнить `npm run api:generate`.
 * Результат хранится в git (src/api/generated); запросы идут через apiFetch (CSRF, 401, problem+json).
 */
export default defineConfig({
  primal: {
    // CI передаёт файл контракта из интеграционного теста бэкенда (OPENAPI=../backend/build/openapi.json)
    input: process.env.OPENAPI ?? 'http://localhost:8080/v3/api-docs',
    output: {
      target: 'src/api/generated/primal.ts',
      mode: 'tags-split',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      override: {
        mutator: {
          path: 'src/api/http.ts',
          name: 'apiFetch',
        },
        fetch: {
          // apiFetch возвращает тело ответа, ошибки — исключением ApiError
          includeHttpResponseReturnType: false,
        },
      },
    },
  },
});
