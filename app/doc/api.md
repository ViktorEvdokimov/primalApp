# API-справочник Primal App

## 1. Модели данных (Data Layer)

### 1.1 Monster

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/model/Monster.kt:3`

```kotlin
data class Monster(
    val name: String,                       // Название монстра
    val maxPhases: Int = 3,                 // Макс. количество фаз
    var currentPhase: Int = 1,              // Текущая фаза (1, 2, 3)
    var currentHealth: Int = 10,            // Текущее здоровье
    var accumulatedDamage: Int = 0,         // Накопленный (неприменённый) урон
    var damageForWound: Int? = 4,           // Урон, требуемый для 1 раны (null = нет порога раны)
    var healthForStanceChange: Int = 7,     // Порог здоровья для смены стойки
    var rage: Int = 0,                      // Текущая ярость (в начале боя выставляется = числу охотников)
    var isHardened: Boolean = false,        // «Затвердевший»: остаток урона после раны сгорает
    var isResilient: Boolean = false,       // «Устойчивость стойки»: урон не переносится на новую стойку (R-3)
    var isDefeated: Boolean = false         // Флаг победы
)
```

**Computed-свойства:**
| Свойство | Тип | Описание |
|----------|-----|----------|
| `isLastPhase` | `Boolean` | `currentPhase >= maxPhases` |

**Константы:**
| Константа | Значение |
|-----------|----------|
| `DEFAULT_HEALTH` | `10` |
| `DEFAULT_PHASES` | `3` |

---

### 1.2 Hunter

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/model/Hunter.kt:3`

```kotlin
data class Hunter(
    val name: String,                       // Имя охотника
    val maxHealth: Int = 20,                // Максимальное здоровье
    var currentHealth: Int = maxHealth,     // Текущее здоровье
    var isUnconscious: Boolean = false      // Флаг потери сознания
)
```

**Computed-свойства:**
| Свойство | Тип | Описание |
|----------|-----|----------|
| `isAlive` | `Boolean` | `currentHealth > 0` |
| `healthPercentage` | `Float` | `currentHealth / maxHealth` (0.0 .. 1.0) |

**Константы:**
| Константа | Значение |
|-----------|----------|
| `DEFAULT_MAX_HEALTH` | `20` |

---

### 1.3 DamageResult

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/model/ext/MonsterExt.kt:5`

```kotlin
data class DamageResult(
    val woundsInflicted: Int,               // Количество нанесённых ран
    val remainingDamage: Int,               // Остаток урона после ран
    val phaseChanged: Boolean,              // Произошла ли смена стойки
    val newPhase: Int,                      // Новая фаза (1–3)
    val message: String                     // Текстовое описание результата
)
```

---

## 2. Функции-расширения (Domain Layer)

### 2.1 MonsterExt

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/model/ext/MonsterExt.kt`

#### `takeDamage(amount: Int): DamageResult`
**Строка:** `13`

Наносит урон монстру с полным циклом обработки ран, смены фаз и hardened-статуса.

**Алгоритм:**
1. Если `isDefeated` → возврат `"Монстр уже побеждён."`
2. Если `amount < 0` → `accumulatedDamage -= min(|amount|, accumulatedDamage)`, раны и здоровье не меняются («Накопленный урон уменьшен на X.» / «Накопленного урона нет.»; D-3)
3. `accumulatedDamage += amount`
4. Если `damageForWound == null` → раны не наносятся, возврат `DamageResult(wounds=0, remaining=accumulatedDamage, ...)` (накопление без раны)
5. Цикл `while (accumulatedDamage >= damageForWound)`:
   - `accumulatedDamage -= damageForWound`, `currentHealth -= 1`
   - Если `currentHealth <= 0` → `isDefeated = true`, возврат
   - Если `health <= healthForStanceChange && phase < maxPhases` → `currentPhase++` и **выход из цикла**: остаток переносится на новую стойку (R-2)
6. Если `isHardened && wounds > 0` → `accumulatedDamage = 0` (сгорание)
7. Возврат `DamageResult`

**Пример:**
```
monster(currentHealth=10, damageForWound=4).takeDamage(9)
→ DamageResult(wounds=2, remaining=1, phaseChanged=false)

monster(currentHealth=10, damageForWound=4, isHardened=true).takeDamage(9)
→ DamageResult(wounds=2, remaining=1, phaseChanged=false)  // remaining — сгоревший остаток, accumulatedDamage = 0

monster(currentHealth=9, damageForWound=4, healthForStanceChange=7).takeDamage(13)
→ DamageResult(wounds=2, remaining=5, phaseChanged=true)   // 5 урона — на новую стойку
```

---

#### `healWound(): Boolean`

Заживление раны (R-4): `currentHealth += 1`, если монстр не побеждён и здоровье ниже `DEFAULT_HEALTH` (10). Жетоны урона не меняются. Возвращает `true`, если рана заживлена.

---

#### `addRage(amount: Int): Int`
**Строка:** `65`

`rage += amount`, возвращает новое значение.

---

#### `removeRage(amount: Int): Int`
**Строка:** `70`

`rage = max(rage - amount, 0)`, возвращает новое значение.

---

#### `addRagePerHunter(hunterCount: Int, multiplier: Int = 1): Int`
**Строка:** `75`

`rage += hunterCount * multiplier`, возвращает новое значение.

---

#### `endRound(hunterCount: Int): Int`
**Строка:** `80`

Вызывает `addRagePerHunter(hunterCount)` (добавляет +1 ярости за каждого охотника). Возвращает новое значение ярости.

---

#### `toggleHardened(): Boolean`

`isHardened = !isHardened`. Возвращает новое значение.

---

#### `toggleResilient(): Boolean`

`isResilient = !isResilient`. Возвращает новое значение (R-3).

---

#### `resetPhase(damageForWound: Int?, healthForStanceChange: Int?)`
**Строка:** `127`

Сбрасывает параметры фазы:
- `this.damageForWound = damageForWound` (null = нет порога раны)
- `this.healthForStanceChange = healthForStanceChange`
- `if (this.isResilient) this.accumulatedDamage = 0` — при «Устойчивости стойки» урон не переносится; иначе остаток переносится на новую стойку (по правилам «Перенесите на неё все жетоны урона с предыдущей карты стойки»). «Затвердевший» на перенос не влияет (R-3)

Вызывается при подтверждении смены стойки.

---

### 2.2 HunterExt

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/model/ext/HunterExt.kt`

#### `takeDamage(amount: Int): Boolean`
**Строка:** `5`

`currentHealth = max(currentHealth - amount, 0)`. Если `currentHealth <= 0` → `isUnconscious = true`. Возвращает `isUnconscious`.

---

#### `heal(amount: Int): Int`
**Строка:** `13`

`currentHealth = min(currentHealth + amount, maxHealth)`. Если `currentHealth > 0` → `isUnconscious = false`. Возвращает новое здоровье.

---

#### `revive(): Hunter`
**Строка:** `21`

`currentHealth = maxHealth`, `isUnconscious = false`. Возвращает `this`.

---

#### `isCritical(): Boolean`
**Строка:** `27`

`currentHealth > 0 && currentHealth <= maxHealth / 4` (≤ 25% здоровья).

---

### 2.3 AchievementNames (сравнение названий достижений, задача 42.1)

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/domain/AchievementNames.kt`

| Функция | Описание |
|---------|----------|
| `normalizeAchievementName(name: String): String` | `trim`, схлопывание пробелов, `lowercase`, «ё» → «е» |
| `achievementMatches(first: String, second: String): Boolean` | Равенство нормализованных названий |
| `Iterable<String>.containsAchievement(name: String): Boolean` | Есть ли в коллекции достижение, равнозначное `name` |

Используются `CampaignViewModel` во всех проверках условий по достижениям (главы, задания, условные достижения) и при выдаче достижений (`grantAchievement` — равнозначное уже полученное не сохраняется повторно).

---

## 3. ViewModel (Presentation Layer)

### 3.1 FightPhase

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:23`

```kotlin
enum class FightPhase {
    PRE_BATTLE,    // Начальное состояние
    SETUP,         // Зарезервировано
    PHASE_I,       // Фаза I — бой
    PHASE_II,      // Фаза II — бой
    PHASE_III,     // Фаза III — бой
    VICTORY,       // Победа
    DEFEAT         // Поражение (раунды истекли)
}
```

---

### 3.2 BattleScreenState

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:47`

```kotlin
data class BattleScreenState(
    val phase: FightPhase = FightPhase.PRE_BATTLE,
    val monster: Monster = Monster(name = "Вираксен"),
    val hunters: List<Hunter> = emptyList(),
    val hunterCount: Int = 0,
    val currentRound: Int = 1,
    val maxRounds: Int = 10,
    val pendingDamage: Int = 0,
    val isTimerRunning: Boolean = false,
    val lastDamageResult: DamageResult? = null,
    val message: String = "",
    val showPhaseChangeDialog: Boolean = false,
    val pendingDamageForWound: String = "",
    val pendingHealthForStanceChange: String = "",
    val damageInputText: String = "",
    val inputMode: InputMode = InputMode.NONE,
    val canUndo: Boolean = false,
    val showRageSurgeDialog: Boolean = false,
    val selectedBoss: Boss? = null,
    val selectedDifficulty: Int = 0,
    val highlightedParams: Set<BattleParam> = emptySet(),
    val phaseChangeFromBossData: Boolean = false, // поля диалога «Смена стойки!» заполнены из базы боссов
    val surrendered: Boolean = false,          // поражение из-за «Сдаться» (D-14)
    val statusInfo: MonsterStatusInfo? = null  // открытое описание статуса (кнопка «i», R-3)
)

enum class MonsterStatusInfo(val title: String, val description: String) { HARDENED, RESILIENT }
```

**Новые поля (задачи 37.x):**

| Поле | Тип | Описание |
|------|-----|----------|
| `highlightedParams` | `Set<BattleParam>` | Параметры, подсвечиваемые красным/жирным 1 с после изменения (37.1) |

**Параметры подсветки:**

```kotlin
enum class BattleParam {
    PHASE, ROUND, HEALTH, RAGE, ACCUMULATED_DAMAGE,
    DAMAGE_FOR_WOUND, HEALTH_FOR_STANCE_CHANGE, HARDENED, RESILIENT
}
```

**События вибрации (37.2):**

```kotlin
enum class BattleVibrationEvent { SHORT, DOUBLE }
```
- `BattleViewModel.vibrationEvents: SharedFlow<BattleVibrationEvent>` — `SHORT` при нажатии кнопки/управления и при применении урона; `DOUBLE` (вместо `SHORT`) при нанесении раны (`DamageResult.woundsInflicted > 0`). Android: `Vibrator` + `VIBRATE` (манифест), сбор в `BattleScreen`.
| `canUndo` | `Boolean` | Доступна ли отмена последнего действия |

### 3.2a InputMode

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:30`

```kotlin
enum class InputMode {
    NONE,          // Начальное состояние / после сброса
    MANUAL,        // Пользователь вводит урон с клавиатуры
    QUICK_BUTTON   // Пользователь нажал кнопку (+1/+5/+10/+50) — активен таймер
}
```

### 3.2b MonsterSnapshot

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:35`

```kotlin
data class MonsterSnapshot(
    val currentHealth: Int,
    val accumulatedDamage: Int,
    val currentPhase: Int,
    val isDefeated: Boolean,
    val rage: Int
)
```

Снимок состояния монстра перед нанесением урона. Используется для отмены последнего действия (`onUndoPress`).

---

### 3.3 BattleViewModel

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:57`

```kotlin
class BattleViewModel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
)
```

**Свойства:**
| Имя | Тип | Описание |
|-----|-----|----------|
| `state` | `StateFlow<BattleScreenState>` | Наблюдаемое состояние (read-only) |
| `timerJob` | `Job?` | Текущая корутина таймера авто-применения урона |
| `lastSnapshot` | `MonsterSnapshot?` | Снимок монстра перед последним `commitDamage` |
| `lastAppliedDamage` | `Int` | Размер последнего применённого урона |

---

#### `startBattle(hunterCount: Int, damageForWound: Int?, healthForStanceChange: Int?)`
**Строка:** `88`

Создаёт охотников и монстра, переводит фазу в `PHASE_I`. Начальная ярость монстра = числу охотников (`startBattleWithHunters` → `rage = hunters.size`; задача 35.2).

**Параметры:**
- `hunterCount` — количество охотников (> 0; верхней границы нет — D-12, qa 94)
- `damageForWound` — урон для одной раны (`null` = нет порога раны, урон накапливается)
- `healthForStanceChange` — порог здоровья для смены стойки

---

#### `onDamageInputChanged(text: String)`
**Строка:** `101`

Обработчик изменения текста в поле ввода урона. Переводит режим в `MANUAL`, отменяет таймер, парсит число в `pendingDamage`.

---

#### `onInputFieldFocused()`
**Строка:** `115`

Обработчик получения фокуса полем ввода. Если текущий режим `QUICK_BUTTON` — отменяет таймер и переключает режим в `MANUAL`, сохраняя накопленное значение. Предотвращает сброс введённого урона по таймеру при клике на поле ввода.

---

#### `onQuickButtonPress(amount: Int)`
**Строка:** `98`

Обработчик нажатия кнопок быстрого добавления (+1/+5/+10/+50).

**Логика:**
- Если режим `MANUAL`: накопление урона **без** запуска таймера
- Если режим `NONE` или `QUICK_BUTTON`: накопление + запуск/сброс таймера на 2 секунды

---

#### `onOkPress()`
**Строка:** `125`

Мгновенное применение накопленного урона (вызов `commitDamage()`).

---

#### `onCancelPress()`
**Строка:** `129`

Отмена таймера, сброс `pendingDamage` в 0, очистка поля ввода, переход в режим `NONE`.

---

#### `onUndoPress()`
**Строка:** `138`

Отмена последнего действия (стек до 10 `ActionSnapshot`, типы `DAMAGE`, `RAGE`, `PHASE_CHANGE`, `ROUND_END`, `HEAL`). Восстанавливает монстра (здоровье, накопленный урон, фазу, флаг поражения, ярость, прочность, порог), `FightPhase` и номер раунда `ActionSnapshot.round` (D-4).

---

#### `commitDamage()`
**Строка:** `162`

Применяет накопленный урон. Сохраняет снимок для отмены. Вызывает `applyDamage(...)` → `monster.takeDamage(pendingDamage)`; при смене стойки открывает диалог (`showPhaseChangeDialog`) с полями из стойки босса (`selectedBoss.getStance(phase − 1)`, флаг `phaseChangeFromBossData`) или пустыми, если стойки нет в базе (R-2). Победа — `monster.isDefeated` или «побеждён» в сообщении (D-18). Сбрасывает `inputMode` в `NONE`, очищает поле ввода.

---

#### `healWound()`

«Заживить рану (+1 здоровья)» (R-4): снимок `ActionType.HEAL`, `monster.healWound()`. Если здоровье уже максимальное — сообщение «Здоровье уже максимальное.».

---

#### `confirmPhaseChange(damageForWound: Int?, healthForStanceChange: Int?, bossHealth: Int = 0)`
**Строка:** `379`

Подтверждает смену стойки: вызывает `monster.resetPhase(...)`, закрывает диалог. `damageForWound == null` — стойка без порога раны. Перенесённый `accumulatedDamage` сразу пересчитывается в раны по прочности новой стойки (в т.ч. при переходе со стойки без порога); если он снова доводит до порога, открывается диалог следующей стойки.

---

#### `onManualStanceChange()`

Кнопка «Сменить стойку» (стойка «по запросу»): `currentPhase++` и диалог «Смена стойки!» с полями из базы боссов (если стойка известна).

---

#### `confirmRageSurge()`
**Строка:** `303`

Сбрасывает ярость до значения `hunterCount`, закрывает диалог «Выплеск ярости». Сообщение: «Выплеск ярости! Каждый охотник получил урон, равный силе монстра. Ярость сброшена до N» (R-8). Вызывается при достижении условия `rage >= hunterCount * 3`.

---

#### `addRage(amount: Int)`
**Строка:** `314`

`monster.rage += amount`. После изменения проверяет условие всплеска ярости (`rage >= hunterCount * 3`).

---

#### `removeRage(amount: Int)`
**Строка:** `326`

`monster.rage = max(rage - amount, 0)`

---

#### `addRagePerHunter()`
**Строка:** `338`

`monster.rage += hunterCount`. Проверяет всплеск ярости.

---

#### `addRagePerHunterMinusOne()`
**Строка:** `350`

`monster.rage += max(hunterCount - 1, 0)`. Проверяет всплеск ярости.

---

#### `toggleHardened()`
**Строка:** `243`

Переключает `monster.isHardened` («Затвердевший»).

---

#### `toggleResilient()`

Переключает `monster.isResilient` («Устойчивость стойки», R-3).

---

#### `onStatusInfoRequested(info: MonsterStatusInfo)` / `onStatusInfoDismissed()`

Открывает/закрывает описание статуса по кнопке «i» (`BattleScreenState.statusInfo`).

---

#### `endRound()`
**Строка:** `367`

Завершает раунд:
1. Если `pendingDamage > 0` — применяет урон через `applyDamage` (как `commitDamage`); при победе раунд не завершается, при смене стойки открывается диалог
2. `monster.endRound(hunterCount)` — добавляет ярость за охотников
3. `currentRound += 1`
4. Если `currentRound > maxRounds` → `DEFEAT`
5. Сбрасывает `inputMode` в `NONE`, `canUndo = actionHistory.isNotEmpty()` (снимок `ROUND_END` хранит номер раунда — D-4)
6. Проверяет всплеск ярости (если не DEFEAT)

---

#### `resetBattle()`
**Строка:** `283`

Сбрасывает бой: `timerJob?.cancel()`, `lastSnapshot = null`, `_state = BattleScreenState()`.

---

## 4. ViewModel экранов (`shared/.../viewmodel/`)

### 4.1 AppViewModel и навигация

`AppViewModel(core: AppCore)` — корневая ViewModel (живёт в `PrimalApplication`): стек экранов, связь с сервером,
текущий бой, ссылки на сайт.

| Член | Назначение |
|------|-----------|
| `stack: StateFlow<List<Screen>>`, `navigate`, `replace`, `back`, `backTo`, `toMainMenu` | Навигация. `Screen`: `MainMenu`, `ExpeditionSetup`, `Battle`, `CampaignList`, `CampaignCreate`, `CampaignSheet(localId)`, `Forge`, `Lab`, `QuestSelect`, `CampaignBattleSetup(localId, questNumber)`, `Outcome`, `Transition`, `Info(section, entryId)`, `Settings`, `Join(token)` |
| `connection: StateFlow<ConnectionState>` | Режим, сервер, имя аккаунта, `SyncStatus`; `label` — «Онлайн · Алиса», «Нет связи с сервером», «Офлайн-режим» |
| `messages: SharedFlow<String>` | Уведомления (snackbar): ошибки правил, итоги синхронизации |
| `battleViewModel: BattleViewModel` | Единственный бой приложения |
| `campaignBattle: StateFlow<CampaignBattleContext?>` | Бой кампании: `localId`, `battleId` (UUID), цель, босс, сложность, снимок главы и `progressSeq`, время старта |
| `startExpedition(...)` | Экспедиция: бой без кампании |
| `startCampaignBattle(context, hunters, dfw, hsc, boss)` | Отметка о начале (`CampaignOp.StartBattle`) и бой |
| `finishCampaignBattle()`, `abandonCampaignBattle()` | Итог принят / бой брошен (`CampaignOp.AbandonBattle`) |
| `openLink(url)` | App Links: `/s/<токен>` → `Join`, `/campaigns/<id>` → лист копии кампании, `/info` → «Инфо» |

### 4.2 Экраны

| ViewModel | Экран | Основное |
|-----------|-------|----------|
| `BattleSetupViewModel(app, localId?, questNumber?)` | Подготовка к бою | `BattleSetupState`: боссы каталога, префилл стойки I, сложность по главе, предупреждения «Уже идёт бой» и о замене боя; `onStart`, `onConfirmActiveWarning`, `onConfirmReplace` |
| `CampaignListViewModel` | Кампании | Список кампаний устройства и аккаунта; `onUpload`, `onDeleteConfirmed` (`LOCAL` — сразу, `OWNER` — `pendingDelete`) |
| `CampaignCreateViewModel` | Новая кампания | Отряд 2–5, `onCreate` → `CampaignRules.create` + `CampaignStore.create(upload = вошёл)` |
| `CampaignSheetViewModel` | Лист кампании | Вкладки, навыки, ресурсы, инвентарь, имя игрока, задания («Выполнено», «Отмена», редактор), достижения, заметки (0,8 с), глава, история, «Поделиться» (`ShareState`), поток событий сервера (SSE) |
| `ForgeViewModel`, `LabViewModel`, `ExchangeViewModel` | Кузница, лаборатория, «Обменять ресурсы» | Цены и нехватка, выбор растений, обмен/преобразование/продажа |
| `QuestSelectViewModel` | Выбор задания | Открытые задания с боссами |
| `OutcomeViewModel` | Итог боя | `BattleReport` из боя, обновление кампании с сервера, `previewResult`, «Принять» / «Редактировать» (`OverridesForm`) / «Отклонить», держатели карт наград |
| `TransitionViewModel` | Переход главы | Решения главы, `previewTransition`, «Принять» / «Отклонить» |
| `InfoViewModel` | Инфо | Разделы, поиск, фильтр, раскрытие, ссылки «См. также», картинки |
| `SettingsViewModel` | Настройки | Режим, сервер, вход/регистрация, имя, дополнения, выход |
| `JoinViewModel` | Приглашение | `GET /share/{token}`, «Присоединиться» |

Экраны одной кампании наследуют `CampaignScopedViewModel`: наблюдают документ кампании (`CampaignStore.observe`) и
выполняют действия `perform(op)` с сообщением об ошибке правил.

---

## 5. Домен (`shared/.../catalog/`, `campaign/`, `info/`)

### 5.1 Каталог

| Тип | Назначение |
|-----|-----------|
| `Catalog` | Боссы (`BossDef`, стойки `StanceDef` по уровням враждебности, `StanceChangeMode`), достижения, задания (`QuestDef`: `victory`, `expired`), главы (`ChapterDef`: `effects`, `decisions`), кузня (`ForgeItemDef`, `ForgeSlot`), лаборатория (`LabPotionDef`, `LabUnit`); `difficultyForChapter` |
| `CatalogJson.parse(text)` | Разбор ответа `GET /catalog/bundle` и файла `catalog-bundle.json` |
| `Effect`, `Condition`, `Decision` | Язык эффектов (`web/doc/data-model.md` §4.3) |
| `EffectPlanner.plan(effects, facts, context)` | `Plan(actions, explanations)`: условия — по снимку `CampaignFacts` (достижения, глава книги, добавленные задания, дополнения владельца); `RuleExplanation(kind, description, result)` |
| `EffectDescriber` | Формулировки правил «Если …» (задание — «добавить задание», глава с «иначе» — «открыть задание») |
| `BossDef.toBattleBoss(difficulty)` | Босс для движка боя (`Boss`/`BossStance`: `0` — последняя стойка, `null` — по запросу) |

### 5.2 Кампания

| Тип | Назначение |
|-----|-----------|
| `CampaignState` | Документ кампании: глава 0–11, статус, кузня, лаборатория, финальный босс, `progressSeq`, заметки, `HunterState` (навыки `SkillStep`, ресурсы `ResourceCode`, `InventoryItem`), `QuestState`, `AchievementState`, `TrophyState`, `BattleRecord` |
| `CampaignRules(catalog)` | Все правила: `create`, `rename`, `setNotes`, `changeChapter`, `renameHunter`, `unlockSkill`/`lockSkill`, `adjustResources`, `openForges`/`forgeCost`/`craft`, `brew`, `addItem`/`editItem`/`removeItem`, `exchange`, `convert`, `sell`, `completeQuest`, `reopenQuest`, `setOpenQuests`, `addAchievement`/`deleteAchievement`, `battleTarget`, `startBattle`, `abandonBattle`, `activeBattles`, `previewResult`, `submitResult`, `requireConsistent`, `previewTransition`, `acceptTransition`, `rejectTransition`, `applyPlan` |
| `CampaignOp` | Действие игрока (сериализуемое): `apply(rules, state, ctx)`; журнал для синхронизации |
| `RuleContext` | Дополнения владельца, время, автор (`Actor`), генератор ключей |
| `BattleReport`, `ResultOverrides`, `ResultPreview`, `TransitionPreview`, `CompletedQuest` | Итог боя, правки наград, превью для окон |
| `CampaignRuleException`, `CampaignChangedException(reasons)` | Недопустимое действие; итог боя расходится с кампанией |

### 5.3 Инфо

`InfoData` (разделы `InfoSectionCode`, статьи `InfoEntry` с ключом картинки), `InfoJson.parseServer` /
`parseDefault`, `InfoText`: `paragraphs`, `segments` (`InfoSegment.Plain/Bold/Link`), `resolver`, `matches`, `label`.

---

## 6. Слой данных (Room KMP, версия 18)

| Entity | DAO | Назначение |
|--------|-----|-----------|
| `CampaignStoreEntity` (`campaign_store`) | `CampaignStoreDao` | Документ кампании и синхронизация |
| `KeyValueEntity` (`kv_store`) | `KeyValueDao` | Настройки |
| `CacheBlobEntity` (`cache_blob`) | `CacheBlobDao` | Каталог, «Инфо», картинки с ETag |

- `ALL_MIGRATIONS` — 1→2 … 17→18; `MIGRATION_17_18` (`database/Migration18.kt`) переносит кампании старой схемы
  (`doc/sync.md` §3). Старые сиды (`TaskInfoSeed`, `ChapterInfoSeed`, `seedBosses`) нужны только миграциям 9–17.
- Platform (expect/actual): `PlatformContext`, `currentTimeMillis()`, `createPrimalDatabase(context)`,
  `readBundledText(context, name)` — файл приложения (Android — assets).

---

## 7. Сеть и синхронизация (`shared/.../net/`, `sync/`)

| Тип | Назначение |
|-----|-----------|
| `PrimalApi(engine, serverUrl, cookies)` | Клиент API: вход, «кто я», имя, дополнения, `catalogBundle(etag)`, `info(etag)`, `infoImage`, `campaigns`, `snapshot(id, version)`, `putSnapshot`, `importCampaign`, `deleteCampaign`, `shareLink`/`createShareLink`/`revokeShareLink`, `shareInfo`, `join`, `events(id)` (SSE). Ошибки — `ApiException(status, code, detail)`, нет связи — `OfflineException` |
| `CookieJar` | Cookie устройства и CSRF: домен, путь, срок, `Secure` |
| `createHttpEngine()` | expect/actual: OkHttp (Android), Darwin (iOS) |
| `SettingsStore` | `AppSettings`: режим (`ConnectionMode`), сервер, `AccountInfo`, дополнения, время синхронизации |
| `CatalogRepository`, `InfoRepository` | Справочники: кэш → файл приложения; `refresh(api)` по ETag |
| `CampaignStore` | `observeAll`, `observe`, `create`, `perform(localId, op)`, `requestUpload`, `markDeleted`, `replay(base, ops)` |
| `SnapshotMapper` | Снимок сервера ↔ `CampaignState` |
| `SyncEngine` | `sync(references)`, `syncCampaign(localId)`, `requestPush()`; `status: StateFlow<SyncStatus>`, `messages` |
| `AppCore` | Корень: БД, настройки, справочники, хранилище, API, синхронизация (раз в минуту), `login`, `register`, `logout`, `setServer`, `setMode`, `setExpansions`, `join` |

---

## 8. Компоненты UI (Android, `androidApp/.../ui/`)

| Файл | Содержимое |
|------|-----------|
| `MainActivity.kt` | `PrimalApp`: экран по `AppViewModel.stack`, snackbar, «Назад»; приём ссылок (`ACTION_VIEW`) |
| `PrimalApplication.kt` | `AppCore` и `AppViewModel` процесса |
| `MainMenuScreen.kt`, `BattleSetupScreen.kt`, `BattleHost.kt`, `BattleScreen.kt` | Меню, подготовка, бой и итоги |
| `CampaignListScreen.kt`, `CampaignCreateScreen.kt`, `CampaignSheetScreen.kt` | Кампании, лист, «Поделиться» (QR — ZXing) |
| `WorkshopScreens.kt` | Кузница, лаборатория, «Обменять ресурсы» |
| `ProgressionScreens.kt` | Выбор задания, итог боя, переход главы |
| `InfoScreen.kt`, `SettingsScreen.kt` (с `JoinScreen`) | Инфо, настройки, приглашение |
| `Common.kt` | `ScreenScaffold`, `Dropdown`, `ConfirmDialog`, `Banner`, `ResourceIcon`, `ResourceLine`, `RulesSection`, `ExpansionBadge` |
| `ReactionDeckHint.kt` | Колода карт реакций |
