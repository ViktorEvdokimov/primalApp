# Архитектура приложения Primal App

## 1. Модульная структура

```
primalApp/
├── app/                        мобильное приложение (этот документ)
│   ├── shared/                 KMP-модуль: вся логика
│   │   └── commonMain/kotlin/com/primalapp/
│   │       ├── model/          бой: Monster, Hunter, Boss; перечисления (Element, Material, Plant,
│   │       │                   ResourceCode, HunterClass, SkillBranch, Expansion, ForgeSlot)
│   │       ├── model/ext/      правила боя (MonsterExt, HunterExt)
│   │       ├── catalog/        каталог и язык эффектов: Catalog, CatalogJson, Effect, EffectPlanner,
│   │       │                   EffectDescriber, toBattleBoss
│   │       ├── campaign/       кампания: CampaignState, CampaignRules, CampaignOp, превью итогов
│   │       ├── info/           «Инфо»: модели, разбор, разметка текста
│   │       ├── database/       Room: entity, dao, миграции 1–18, старые сиды (для миграций)
│   │       ├── net/            Ktor-клиент API сервера, cookie, DTO
│   │       ├── sync/           AppCore, SettingsStore, справочники, CampaignStore, SyncEngine
│   │       ├── viewmodel/      BattleViewModel и ViewModel экранов
│   │       └── domain/         сравнение названий достижений
│   ├── androidApp/             Compose UI, App Links, assets (каталог и «Инфо» по умолчанию)
│   └── iosApp/                 заготовка SwiftUI
├── web/                        сайт: backend (Spring Boot), frontend (React), doc
└── test/                       UI-тесты Appium (pytest)
```

---

## 2. Слои приложения (MVVM)

```
Compose UI (androidApp/ui)            — «глупый» UI: рисует состояние, передаёт действия
        │ collectAsState()
        ▼
ViewModel (shared/viewmodel)          — AppViewModel (навигация, бой), BattleViewModel, ViewModel экранов
        │ CampaignOp / запросы
        ▼
Домен (shared/catalog, campaign)      — CampaignRules + язык эффектов: чистые функции над CampaignState
        │
        ▼
Данные и синхронизация (shared/sync)  — CampaignStore (Room, документы JSON), справочники, SettingsStore,
        │                               SyncEngine ⇄ PrimalApi (Ktor) ⇄ сервер web/backend
        ▼
Room (shared/database)                — campaign_store, kv_store, cache_blob (версия 18)
```

- Действие игрока в кампании: ViewModel создаёт `CampaignOp` → `CampaignStore.perform` применяет его правилами к
  документу и сохраняет; у кампании с сервера действие попадает в журнал, `SyncEngine` отправляет снимок
  (`doc/sync.md` §5). Экраны наблюдают документ через `Flow` и обновляются сами — в том числе после изменений других
  участников.
- Бой — как раньше: `BattleViewModel` + `MonsterExt` (разделы 3–4); бой кампании начинается и заканчивается через
  `AppViewModel` (отметка о начале, итог).
- Корень зависимостей — `AppCore` (один на процесс, `PrimalApplication`): БД, настройки, справочники, хранилище
  кампаний, API, синхронизация раз в минуту и при возврате приложения на экран.

---

## 3. Диаграмма состояний (State Machine)

```
  ┌─────────────┐
  │ PRE_BATTLE  │ ←── начальное состояние
  └──────┬──────┘
         │ startBattle(count, wound, stance)
         ▼
  ┌──────────┐
  │ PHASE_I  │ ←── display: BattleScreen
  └────┬─────┘
       │
       ├── takeDamage → health <= healthForStanceChange? ──Да──► PHASE_II
       │                                                        │
       ├── takeDamage → health = 0 ────────────────────────────► VICTORY
       │
       ├── endRound → round > 10 ──────────────────────────────► DEFEAT
       │
       └── endRound → round <= 10 ───Остаётся в──► PHASE_I

  ┌──────────┐
  │ PHASE_II │ ←── display: BattleScreen + PhaseChangeDialog
  └────┬─────┘
       │
       ├── takeDamage → health <= healthForStanceChange? ──Да──► PHASE_III
       │                                                        │
       ├── takeDamage → health = 0 ────────────────────────────► VICTORY
       │
       ├── endRound → round > 10 ──────────────────────────────► DEFEAT
       │
       └── endRound → round <= 10 ───Остаётся в──► PHASE_II

  ┌───────────┐
  │ PHASE_III │ ←── display: BattleScreen + PhaseChangeDialog
  └────┬──────┘
       │
       ├── takeDamage → health = 0 ───────────► VICTORY
       │
       ├── endRound → round > 10 ─────────────► DEFEAT
       │
       └── endRound → round <= 10 ───Остаётся в──► PHASE_III

  ┌──────────┐      ┌──────────┐
  │ VICTORY  │      │  DEFEAT  │
  └────┬─────┘      └────┬─────┘
       │                  │
       └─── resetBattle() ─┘
                    │
                    ▼
             ┌─────────────┐
             │ PRE_BATTLE  │
             └─────────────┘
```

---

## 4. Поток данных при нанесении урона

### 4.1 Режим QUICK_BUTTON: кнопки (+1/+5/+10/+50) с таймером

```
Пользователь нажимает "+10"
        │
        ▼
MainActivity: onClick { viewModel.onQuickButtonPress(10) }
        │
        ▼
BattleViewModel.onQuickButtonPress(10):
  1. inputMode == NONE → переключаем в QUICK_BUTTON
  2. timerJob?.cancel()
  3. pendingDamage = 0 + 10 = 10
  4. damageInputText = "10"
  5. timerJob = launch { delay(2000); commitDamage() }
        │
        │ ... пользователь нажимает "+1" в течение 2 сек ...
        │
Пользователь нажимает "+1"
        │
        ▼
BattleViewModel.onQuickButtonPress(1):
  1. inputMode == QUICK_BUTTON → остаёмся в QUICK_BUTTON
  2. timerJob?.cancel()                    ← сброс таймера
  3. pendingDamage = 10 + 1 = 11
  4. damageInputText = "11"
  5. timerJob = launch { delay(2000); commitDamage() }  ← новый таймер
        │
        │ ... 2 секунды без нажатий ...
        │
        ▼
BattleViewModel.commitDamage():
  1. timerJob?.cancel()
  2. lastSnapshot = MonsterSnapshot(...)    ← снимок для undo
  3. applyDamage(monster, 11)
        │
        ▼
Monster.takeDamage(11):
  → DamageResult(wounds=2, remaining=3, phaseChanged=true)   ← цикл ран остановлен на смене стойки (R-2)
        │
        ▼
commitDamage() (продолжение):
  - phase = PHASE_II, showPhaseChangeDialog = true (3 урона ждут подтверждения)
  - поля диалога: из selectedBoss.getStance(1), если стойка известна (phaseChangeFromBossData), иначе пустые
  - inputMode = NONE, damageInputText = "", canUndo = true
  - _state.update { ... }
        │
        ▼
MainActivity: state.collectAsState() → рекомпозиция
  - BattleScreen (фаза II) + PhaseChangeDialog
  - Кнопка «Отменить предыдущее действие» активна
```

### 4.2 Режим MANUAL: ручной ввод + OK

```
Пользователь вводит "15" в поле
        │
        ▼
MainActivity: onValueChange { viewModel.onDamageInputChanged("15") }
        │
        ▼
BattleViewModel.onDamageInputChanged("15"):
  1. timerJob?.cancel()                    ← таймер не нужен
  2. inputMode = MANUAL
  3. pendingDamage = 15
  4. damageInputText = "15"
        │
Пользователь нажимает Enter или OK
        │
        ▼
viewModel.onOkPress() → commitDamage()
  → мгновенное применение, без таймера
```

### 4.3 Смешанный сценарий: MANUAL → кнопки (+N)

```
Пользователь ввёл "10" (inputMode = MANUAL)
        │
Пользователь нажимает "+50"
        │
        ▼
BattleViewModel.onQuickButtonPress(50):
  1. inputMode == MANUAL → остаёмся в MANUAL
  2. pendingDamage = 10 + 50 = 60
  3. damageInputText = "60"
  4. Таймер НЕ запускается
        │
Пользователь нажимает OK → мгновенное применение
```

### 4.4 Смешанный сценарий: QUICK_BUTTON → поле ввода

```
Пользователь нажал "+10" (inputMode = QUICK_BUTTON, таймер 2с)
        │
Пользователь кликает в поле ввода (поле получает фокус)
        │
        ▼
MainActivity: onFocusChanged { viewModel.onInputFieldFocused() }
        │
        ▼
BattleViewModel.onInputFieldFocused():
  1. inputMode == QUICK_BUTTON? Да
  2. timerJob?.cancel()                    ← таймер полностью остановлен
  3. inputMode = MANUAL
  4. damageInputText = "10" (сохранено)
        │
Урон теперь применится только по OK/Enter, значение не сбросится
```

### 4.5 Отмена последнего действия

```
Пользователь нажимает «Отменить предыдущее действие»
        │
        ▼
BattleViewModel.onUndoPress():
  1. Восстанавливает monster из lastSnapshot:
     - currentHealth, accumulatedDamage, currentPhase
     - isDefeated, rage
  2. lastSnapshot = null, canUndo = false
  3. _state.update { ... }
        │
        ▼
MainActivity: рекомпозиция — состояние монстра откачено
```

---

## 5. Граф зависимостей

| Модуль | Зависимости |
|--------|-------------|
| `:shared` | kotlinx-coroutines 1.8.1, kotlinx-datetime 0.6.1, kotlinx-serialization-json 1.7.3, Room 2.7.0 (KSP 2.0.21-1.0.28), Ktor client 2.3.12 (`ktor-client-okhttp` — Android, `ktor-client-darwin` — iOS); тесты — kotlin-test, coroutines-test, ktor-client-mock, Robolectric, room-testing |
| `:androidApp` | `:shared`, Compose BOM 2024.09.00 (Material3), activity-compose 1.9.1, ZXing core 3.5.3 (QR-код) |
| `iosApp` | `Shared.framework` |

Плагины: Kotlin 2.0.21 (multiplatform, android, compose, serialization), AGP 8.13.2, KSP. Версии — в
`gradle/libs.versions.toml`.

---

## 6. Основные проектные решения

| Решение | Причина |
|---------|---------|
| MVVM, а не MVC/MVP | Чёткое разделение UI и логики, поддержка KMP |
| StateFlow, а не LiveData | KMP-совместимость (LiveData — Android-only) |
| Room KMP 2.7.0 для хранения | Единый слой данных в shared/commonMain через expect/actual для Room.Builder; персистентность кампаний доступна на Android и iOS |
| Кампания — документ JSON, а не таблицы | Состояние кампании совпадает со снимком сервера: синхронизация отправляет и получает его целиком, без отдельных таблиц для охотников, заданий и т. п. |
| Правила кампании в приложении (local-first) | Кампании работают без сети; сервер получает готовое состояние, правила совпадают с сервером (перенос сервисов `web/backend`) |
| Журнал действий и повтор при конфликте | Правки разных участников складываются, а не затирают друг друга; «первая принятая победа закрывает главу» соблюдается и после работы без сети |
| Ktor client | KMP-клиент HTTP (OkHttp/Darwin), поток SSE без отдельной библиотеки |
| Каталог и «Инфо» — файлом в assets | Справочники доступны без сети с первого запуска; онлайн обновляются с сервера по ETag |
| expect/actual для PlatformContext | Room.databaseBuilder требует платформенный контекст (Android Context vs iOS NSFileManager путь) |
| expect/actual для currentTimeMillis() | System.currentTimeMillis() недоступен в KMP commonMain; заменён на Kotlin-обёртку |
| Функции-расширения, а не методы классов | Идиоматичный Kotlin, разделение модели и логики |
| `var` поля в data class, а не `copy()` | Производительность (мутабельные поля при частых обновлениях) |
| `DamageResult` как возвращаемый тип | Полная информация о результате для UI |
| `FightPhase` enum, а не sealed class | Простота, 7 фиксированных состояний |
| Version Catalog (`libs.versions.toml`) | Единая точка управления версиями, рекомендовано Google |
| `CoroutineScope(SupervisorJob() + Dispatchers.Main)` | Отказоустойчивость: падение одной корутины не убивает остальные |
