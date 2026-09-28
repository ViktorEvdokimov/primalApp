# Primal Web

Веб-версия помощника для настольной игры «Primal. Пробуждение» — клиент-серверный аналог мобильного
приложения из `../app`.

- Экспедиция — бой в браузере, без входа.
- Кампания — пролог и 11 глав, охотники, задания, награды; вход по коду из письма, совместная игра по ссылке.
- Устанавливается на телефон как приложение; экспедиция работает без сети.

| Папка | Что там |
|-------|---------|
| `backend/` | Java 25, Spring Boot 4, PostgreSQL, Flyway |
| `frontend/` | React 19, TypeScript, Vite; движок боя — `src/domain/battle` |
| `e2e/` | E2E-тесты: Python, pytest, Playwright, Allure |
| `deploy/` | Резервное копирование и восстановление базы на сервере |
| `doc/` | Архитектура, бой, модель данных, API, запуск, решения |

Документация: [поведение сайта](doc/behavior.md), [архитектура](doc/architecture.md), [бой](doc/battle.md), [модель данных](doc/data-model.md),
[API](doc/api.md), [запуск](doc/setup.md), [решения](doc/qa.md). План работ — [implementationTasks.md](implementationTasks.md).

Быстрый запуск на своём компьютере (подробнее — [setup.md](doc/setup.md)):

```powershell
Copy-Item .env.example .env
docker compose --profile local up -d --build
```

Сайт — http://localhost:8088, письма с кодами входа — http://localhost:8025.
