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

## 4. CampaignViewModel (Campaign Management)

### 4.1 AppScreen

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/CampaignViewModel.kt:27`

```kotlin
sealed class AppScreen {
    data object MainMenu : AppScreen()
    data object CampaignSetup : AppScreen()
    data object CampaignList : AppScreen()
    data class CampaignSheet(val campaignId: Long) : AppScreen()
    data class CampaignBattle(val campaignId: Long) : AppScreen()
    data object QuickBattle : AppScreen()
}
```

### 4.2 CampaignUiState

**Файл:** `CampaignViewModel.kt:36`

```kotlin
data class CampaignUiState(
    val screen: AppScreen = AppScreen.MainMenu,
    val campaigns: List<Campaign> = emptyList(),
    val campaignName: String = "",
    val selectedClasses: List<HunterClass> = emptyList(),
    val hunterPlayerNames: Map<HunterClass, String> = emptyMap(),
    val currentCampaign: Campaign? = null,
    val hunters: List<CampaignHunter> = emptyList(),
    val selectedHunterIndex: Int = 0,
    val skills: List<SkillNode> = emptyList(),
    val materials: Map<Material, Int> = emptyMap(),
    val plants: Map<Plant, Int> = emptyMap(),
    val elements: Map<Element, Int> = emptyMap(),
    val victoryMaterials: Map<Material, Int> = emptyMap(),
    val victoryPlants: Map<Plant, Int> = emptyMap(),
    val notes: String = "",
    val availableSkillBranches: List<SkillBranch> = emptyList(),
    val showPostVictory: Boolean = false,
    val bossName: String = "",
    val bossElement: Element? = null,
    val completedQuestId: String = "",
    val availableQuestsForNext: List<Quest> = emptyList(),
    val selectedNextQuestId: String? = null,
    val showQuestSelectDialog: Boolean = false,
    val activeQuestId: String? = null,
    val selectedQuestNumbers: Set<Int> = emptySet(),
    val selectedDefeatQuestNumbers: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveMessage: String = "",
    val error: String? = null,
    val lastActiveBattle: AppScreen? = null,
    val fatalError: String? = null,
    val preBattleHunters: List<Hunter> = emptyList(),
    val isPrologue: Boolean = false,
    val defeatedBosses: List<String> = emptyList(),
    val campaignQuests: List<Quest> = emptyList(),
    val taskInfoByQuestNumber: Map<Int, TaskInfo> = emptyMap(),
    val showQuestRewards: Boolean = false,
    val questRewardsMode: QuestRewardsMode? = null,
    val activeQuestNumber: Int? = null,
    val editDefeatMode: Boolean = false,
    val showQuestEditDialog: Boolean = false,
    val editedQuestNumbers: Set<Int> = emptySet(),
    val showChapterRewards: Boolean = false,
    val chapterInfoByNumber: Map<Int, ChapterInfo> = emptyMap(),
    val chapterRewardsMessage: String = "",
    val questConditionOutcomes: List<ConditionOutcome> = emptyList(),   // условия окна наград задания
    val chapterConditionOutcomes: List<ConditionOutcome> = emptyList(), // условия окна наград главы
    val campaignTrophies: List<Trophy> = emptyList(),
    val campaignAchievements: List<Achievement> = emptyList(),
    val showAchievementEditor: Boolean = false,
    val newAchievementName: String = "",
    val availableBosses: List<Boss> = emptyList(),
    val selectedPreBattleBoss: Boss? = null,
    val selectedPreBattleBossName: String? = null,
    val preBattleDifficulty: Int = 0,
    val preBattleDamageForWound: String = "4",
    val preBattleHealthForStance: String = "7",
    val preBattleHunterCountText: String = "4",       // поле «Количество охотников» экспедиции (D-12)
    val campaignCompletedQuests: List<Quest> = emptyList(), // список «Выполненные:» (D-5)
    val bossHasNoElement: Boolean = false
)
```

### 4.2a Условные правила (`domain/ConditionOutcomes.kt`)

```kotlin
enum class ConditionOutcomeType { QUEST, ACHIEVEMENT, REWARD }
data class ConditionOutcome(
    val type: ConditionOutcomeType,
    val description: String,       // формулировка правила, как в taskInfo.md / compainInfo.md
    val result: String,            // «Добавлено задание 4.», «Достижения нет.» и т. п.
    val openQuest: Int? = null,    // задание, открываемое при «Принять»
    val grantAchievement: String? = null
)
```

| Функция | Назначение |
|---------|-----------|
| `evaluateTaskConditions(conditions, achievements, bookChapter, availableQuestNumbers)` | Условия задания по порядку; открывается задание первого выполненного условия, условные достижения — отдельно |
| `evaluateChapterConditionalQuests(conditions, achievements)` | Условные задания главы, каждое независимо (`requireAll`, `negated`) |
| `evaluateChapterConditionalRewards(chapterInfo, achievements)` | Условное улучшение набора охотника и условные сообщения главы |

Окна наград (`QuestRewardsDialog`, `ChapterRewardsDialog` → `ConditionOutcomesSection`) и применение наград (`applyQuestOpenConditions`, `applyChapterRewards`) используют один расчёт — показанный результат совпадает с тем, что выполнит «Принять».

### 4.3 CampaignViewModel

**Файл:** `CampaignViewModel.kt:65`

```kotlin
class CampaignViewModel(
    private val repository: CampaignRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
)
```

| Метод | Описание |
|-------|----------|
| `onQuickBattleSelected()` | Переход в режим быстрого боя |
| `onCampaignModeSelected()` | Загрузка списка кампаний или создание первой |
| `onNewCampaignRequested()` | Переход к созданию кампании |
| `onCampaignNameChanged(name)` | Ввод названия кампании |
| `onClassToggled(cls)` | Выбор/снятие класса охотника |
| `onStartCampaign()` | Создание кампании + охотников |
| `onCampaignSelected(id)` | Открытие листа кампании |
| `onDeleteCampaign(id)` | Удаление кампании |
| `onHunterSelected(index)` | Переключение между охотниками |
| `onUnlockSkill(branch, tier)` | Разблокировка навыка |
| `onNotesChanged(notes)` | Ввод заметок |
| `onSaveNotes()` | Сохранение заметок |
| `onOpenAchievementEditor()` | Показать диалог редактирования достижений |
| `onCloseAchievementEditor()` | Закрыть диалог редактирования достижений |
| `onNewAchievementNameChanged(name)` | Ввод названия нового достижения |
| `onAddAchievement()` | Добавить достижение (свободный ввод), сохраняется с `unlocked = true` |
| `onDeleteAchievement(achievementId)` | Удалить достижение кампании |
| `onUpdateChapter(chapter)` | Ручное изменение главы, `coerceIn(1, MAX_CHAPTER = 12)` (D-11) |
| `onPreBattleHunterCountChanged(text)` / `onConfirmQuickBattleStart()` | Число охотников экспедиции хранится текстом; старт — при целом > 0, без верхней границы (D-12) |
| `onVictoryExitToMenu()` | «Выход в меню» на экране победы в кампании: применяет награды задания (пролог — трофей и стихии) и главы, затем главное меню (D-13) |
| `onToggleEditedQuest(number)` / `onSaveQuestEdits()` | Редактор открытых заданий; выполненные задания игнорируются (D-5) |
| `bookChapter(appChapter)` | (companion) Глава книги = глава приложения − 1; используется в условиях `CHAPTER_IN`, `ACHIEVEMENT_OWNED_IN_CHAPTER` (R-1) |
| `onStartCampaignBattle()` | Запуск боя в рамках кампании. Если предыдущая глава объявила финал (`ChapterInfo.finalBossName`, гл. 11), бой с этим боссом начинается без выбора задания (R-6) |
| `getBattleViewModel()` | Получение BattleViewModel для UI |
| `onBattleFinished()` | Завершение боя, возврат к листу |
| `onVictory()` | Открытие окна наград: не-пролог — `QuestRewardsDialog`; пролог (первый бой) — окно «Задание выполнено!» НЕ показывается, победные эффекты применяются молча (`applyPrologueVictory`), сразу открывается окно наград главы (36.1) |
| `onDefeat()` | Открытие окна наград за поражение (`QuestRewardsDialog`, mode=DEFEAT) |
| `onVictoryBossNameChanged(name)` | Ввод имени босса |
| `onVictoryBossElementChanged(element)` | Выбор стихии босса |
| `onVictoryQuestToggled(number)` | Выбор открытых заданий в PostVictoryDialog |
| `onVictoryResourceChanged(type, name, delta)` | Изменение количества ресурсов в PostVictoryDialog |
| `onDefeatQuestToggled(number)` | Выбор номера задания в форме поражения |
| `onConfirmVictory()` | Сохранение победы (трофей + задания + ресурсы + 2 стихии) → окно наград главы. Для пролога не используется — применяется `applyPrologueVictory` |
| `applyPrologueVictory(campaignId)` | (приватный, задача 36.1) Молча сохраняет трофей босса пролога, начисляет по 2 стихии каждому охотнику, сбрасывает `isPrologue = false` и открывает окно наград главы |
| `onQuestRewardsAccept()` | «Принять» награды задания из каталога `TaskInfo` → окно наград главы. Бой без задания (42.4): трофей и 2 стихии босса `selectedPreBattleBoss`; без выбранного босса — `error = "Босс не выбран — укажите его через «Редактировать»"` |
| `onUncompleteQuest(questId)` | Кнопка «Отмена» у выполненного задания: `uncompleteQuest` и перезагрузка листа; зависимые задания и награды не меняются (qa 119) |
| `onCompleteQuest(questId)` | Кнопка «Выполнено» (42.3): `completeQuest` + открытие зависимых заданий (`openDependentQuests`); выполненное задание пропадает из `campaignQuests`. Заменил `onToggleQuestCompleted` (режим переключателя убран) |
| `onDefeatRewardsAccept()` | «Принять» награды поражения (открыть задания) → лист кампании |
| `onQuestRewardsEdit()` | «Редактировать»: победа — предзаполнить PostVictoryDialog открытыми и **невыполненными** заданиями + открываемыми заданием (42.3); поражение — форма чекбоксов |
| `onQuestRewardsDismiss()` | «Выход» из окна наград задания |
| `onChapterRewardsAccept()` | «Принять» награды главы: ресурсы, задания (условные — с `negated`/`requireAll`), истечение (в т.ч. всех заданий — `expireAllQuests`), кузня/лаб, глава += 1 (не выше 12) |
| `onChapterRewardsReject()` | «Отклонить» награды главы: эффекты не применяются, достижение решения главы отзывается (D-10) |
| `onChapterDecisionSelected(option, achievementName)` | Выбор варианта решения главы: достижение выдаётся при «Да» и отзывается при «Нет» (D-10) |
| `onBackToMenu()` | Возврат в главное меню |
| `onErrorDismissed()` | Сброс сообщения об ошибке |

---

## 5. Слой данных (Room KMP)

### 5.1 Entity (10 таблиц)

| Entity | Таблица | Поля |
|--------|---------|------|
| `CampaignEntity` | `campaigns` | id, name, currentChapter, forgeLevel, labLevel, notes, createdAt, updatedAt |
| `HunterEntity` | `hunters` | id, campaignId (FK→campaigns), playerName, className |
| `SkillEntity` | `skills` | id, hunterId (FK→hunters), branch, tier, unlocked |
| `ResourceEntity` | `resources` | id, hunterId (FK→hunters), resourceType, resourceName, quantity |
| `AchievementEntity` | `achievements` | id, campaignId (FK→campaigns), achievementId, name, description, unlocked. **Уникальный индекс** `(campaign_id, achievement_id)` (миграция 15→16, D-9) |
| `TrophyEntity` | `trophies` | id, campaignId (FK→campaigns), bossName, element, chapter, acquiredAt |
| `QuestEntity` | `quests` | id, campaignId (FK→campaigns), questId, name, chapter, element, questNumber, isCompleted, isAvailable. **Уникальный индекс** `(campaign_id, quest_id)` (миграция 8→9) |
| `BossEntity` | `bosses` | id, name, element (nullable), difficulty, stance1–5 dfw (nullable)/hsc (nullable). `stance4_dfw`/`stance5_dfw` — `@ColumnInfo(defaultValue = "0")` (совпадает с `CREATE_BOSSES_TABLE`; фикс 30.1) |
| `TaskInfoEntity` | `task_info` | questNumber (PK), name, bossName, bossElement, victoryMaterials, victoryPlants, victoryOpenQuests, victoryOpenQuestConditions, victoryAchievements, victoryRewardCards, victorySpecial, defeatOpenQuests, defeatOpenQuestConditions (мапы/списки — текстом `NAME:qty`, через `;`/`,`; условия — `kind|achievement|chapterSet|quest|else|rewardAchievement`, через `;`; виды: `CHAPTER_IN`, `ACHIEVEMENT_OWNED`, `ACHIEVEMENT_NOT_OWNED`, `ACHIEVEMENT_OWNED_IN_CHAPTER`, `QUEST_NOT_AVAILABLE`), **defeatAchievements** (колонка, миграция 12→13). Карты наград `victoryRewardCards` пишутся через `,` (как в seed); при чтении принимаются `,` и `;` |
| `ChapterInfoEntity` | `chapter_info` | chapter (PK), rewards, rewardPlants, openQuests, conditionalOpenQuests, expireQuests, forgeUpgrade, labUpgrade, hunterKitUpgrade, decisions, messages, conditionalMessages, **hunterKitUpgradeAchievement** (улучшение набора только при достижении, гл. 8 и 10), **expireAllQuests** (гл. 11), **finalBoss** («Пробуждённый», гл. 11) — последние три колонки добавлены миграцией 14→15 |

### 5.2 DAO (10 интерфейсов)

| DAO | Основные методы |
|-----|-----------------|
| `CampaignDao` | getAllCampaigns (Flow), getCampaign, insert/update/delete, getCount |
| `HunterDao` | getHunters (Flow), getHunter, insert/update/delete, insertReturningId |
| `SkillDao` | getSkills (Flow), insert, setUnlocked, deleteByHunter |
| `ResourceDao` | getResources (Flow), getByType, getResource, updateQuantity (запросы союзников для обмена ресурсами удалены — D-17) |
| `AchievementDao` | getAchievements (Flow), insert (`OnConflictStrategy.IGNORE` — уже выданное достижение не дублируется, D-9), setUnlocked, deleteByCampaign, **deleteAchievement (campaign_id + achievement_id)** |
| `TrophyDao` | getTrophies (Flow), insert, deleteByCampaign |
| `QuestDao` | getQuests (Flow), getAvailable (`is_available = 1 AND is_completed = 0`), getCompleted, **upsertQuest** (`@Transaction`: `insertQuestIfAbsent` — `INSERT OR IGNORE`, затем `updateQuestFields`; признак `is_completed` не снимается — D-5; без `ON CONFLICT DO UPDATE`, недоступного в SQLite Android 8–10), completeQuest, **uncompleteQuest** (кнопка «Отмена» у выполненного задания: `is_completed = 0, is_available = 1`), makeQuestAvailable, **setQuestUnavailable** |
| `BossDao` | getAllBosses, getBossesByDifficulty, getBossByNameDifficulty, insertBoss |
| `TaskInfoDao` | getAllTaskInfo, getTaskInfo(questNumber), insertTaskInfo, insertAllTaskInfo |
| `ChapterInfoDao` | getAllChapterInfo, getChapterInfo(chapter), insertChapterInfo, insertAllChapterInfo |

### 5.3 Миграции и версия БД

**Версия БД:** 16 (`@Database(version = 16)`, `exportSchema = true`, схемы в `shared/schemas/...`; схемы 13–16 скопированы в тестовые assets `shared/src/androidUnitTest/assets/...` для `MigrationTest`)

Seed-функции `seedTaskInfo`/`seedChapterInfo` вставляют только колонки, существующие в схеме на момент вызова (`SeedSql.kt`: `columnsOf` через `PRAGMA table_info`, `insertExistingColumns`): ранние миграции 10→11 и 12→13 вызывают актуальный seed при старой схеме.

| Миграция | Действия |
|----------|----------|
| `MIGRATION_1_2` | `ALTER TABLE quests ADD COLUMN quest_number` |
| `MIGRATION_2_3` | Создание таблицы `bosses` + seed (Вираксен, Иекорос) |
| `MIGRATION_3_4` | Пересоздание `bosses` (element NOT NULL, hsc nullable) |
| `MIGRATION_4_5` | `bosses` — element nullable; `trophies` — element nullable; босс «Пробуждённый» |
| `MIGRATION_5_6` | `recreateAndSeedBosses` (dfw nullable) |
| `MIGRATION_6_7` | `recreateAndSeedBosses` |
| `MIGRATION_7_8` | `recreateAndSeedBosses` + 19 боссов |
| `MIGRATION_8_9` | Уникальный индекс `quests(campaign_id, quest_id)` + чистка дубликатов |
| `MIGRATION_9_10` | Создание таблицы `task_info` (каталог заданий) |
| `MIGRATION_10_11` | `task_info` — колонки условий; создание `chapter_info` (каталог глав) + seed |
| `MIGRATION_11_12` | Пере-сид `task_info` с расширенной моделью условий (`ACHIEVEMENT_OWNED_IN_CHAPTER`, `rewardAchievement` — зад. 25, 29/40); схема не менялась |
| `MIGRATION_12_13` | `task_info` — колонка `defeat_achievements`; пересев `task_info` (задания 41-49), `chapter_info` (условия 42-49) и `bosses` (4 босса: Гидар, Рейкал — Яд; Сиркаадж, Мумараак — Лёд). Версия БД 13. |
| `MIGRATION_15_16` | defects.md D-9: удаление точных дублей достижений (остаётся запись с наименьшим `id`) и уникальный индекс `index_achievements_campaign_id_achievement_id` на `achievements(campaign_id, achievement_id)`. Версия БД 16. |
| `MIGRATION_14_15` | Решения defects.md: `chapter_info` — колонки `hunter_kit_upgrade_achievement`, `expire_all_quests`, `final_boss`; пере-сид `chapter_info` (C-8, C-9, R-6) и `task_info` (C-5, C-6, C-7). Версия БД 15. |
| `MIGRATION_13_14` | Задача 42.1, схема не меняется: пере-сид `task_info` (канонические названия достижений «Народ Золотых гор», «Яд Пазиса», «Копьё драконоборца»); `UPDATE achievements` прежних написаний на канонические (`achievement_id` и `name`); удаление точных дублей достижений (`MIN(id)` по `campaign_id, achievement_id`). Версия БД 14. |

### 5.4 Platform (expect/actual)

| Файл | Назначение |
|------|-----------|
| `Platform.kt` (commonMain) | `expect class PlatformContext`, `expect fun currentTimeMillis()`, `expect fun createPrimalDatabase()` |
| `Platform.android.kt` (androidMain) | `actual typealias PlatformContext = Context`, `Room.databaseBuilder(context, ...)` |
| `Platform.ios.kt` (iosMain) | `actual class PlatformContext`, `NSFileManager` путь + `Room.databaseBuilder(name = ...)` |

---

## 6. Компоненты UI (Android)
