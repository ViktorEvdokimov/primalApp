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
    var damageForWound: Int = 4,            // Урон, требуемый для 1 раны
    var healthForStanceChange: Int = 7,     // Порог здоровья для смены стойки
    var rage: Int = 0,                      // Текущая ярость
    var isHardened: Boolean = false,        // Статус "затвердевший"
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
2. `accumulatedDamage += amount`
3. Цикл `while (accumulatedDamage >= damageForWound)`:
   - `accumulatedDamage -= damageForWound`, `currentHealth -= 1`
   - Если `currentHealth <= 0` → `isDefeated = true`, возврат
   - Если `!isHardened && health <= healthForStanceChange && phase < maxPhases` → `currentPhase++`
4. Если `isHardened && wounds > 0` → `accumulatedDamage = 0` (сгорание)
5. Возврат `DamageResult`

**Пример:**
```
monster(currentHealth=10, damageForWound=4).takeDamage(9)
→ DamageResult(wounds=2, remaining=1, phaseChanged=false)

monster(currentHealth=10, damageForWound=4, isHardened=true).takeDamage(9)
→ DamageResult(wounds=2, remaining=9, phaseChanged=false)  // остаток сгорел
```

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
**Строка:** `85`

`isHardened = !isHardened`. Возвращает новое значение.

---

#### `resetPhase(damageForWound: Int, healthForStanceChange: Int)`
**Строка:** `90`

Сбрасывает параметры фазы:
- `this.damageForWound = damageForWound`
- `this.healthForStanceChange = healthForStanceChange`
- `this.accumulatedDamage = 0`

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
    val showRageSurgeDialog: Boolean = false
)
```

**Новые поля (v1.1):**

| Поле | Тип | Описание |
|------|-----|----------|
| `damageInputText` | `String` | Текст в поле ввода урона (управляется ViewModel) |
| `inputMode` | `InputMode` | Режим ввода: `NONE`, `MANUAL`, `QUICK_BUTTON` |
| `canUndo` | `Boolean` | Доступна ли отмена последнего действия |

### 3.2a InputMode

**Файл:** `shared/src/commonMain/kotlin/com/primalapp/viewmodel/BattleViewModel.kt:30`

```kotlin
enum class InputMode {
    NONE,          // Начальное состояние / после сброса
    MANUAL,        // Пользователь вводит урон с клавиатуры
    QUICK_BUTTON   // Пользователь нажал кнопку (+1/+10/+50) — активен таймер
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

#### `startBattle(hunterCount: Int, damageForWound: Int, healthForStanceChange: Int)`
**Строка:** `65`

Создаёт охотников и монстра, переводит фазу в `PHASE_I`.

**Параметры:**
- `hunterCount` — количество охотников (1–4)
- `damageForWound` — урон для одной раны
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

Обработчик нажатия кнопок быстрого добавления (+1/+10/+50).

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

Отмена последнего применённого урона. Восстанавливает состояние монстра из `lastSnapshot` (здоровье, накопленный урон, фазу, флаг поражения, ярость). Сбрасывает `canUndo`.

---

#### `commitDamage()`
**Строка:** `162`

Применяет накопленный урон. Сохраняет снимок монстра (`lastSnapshot`) для возможной отмены. Вызывает `monster.takeDamage(pendingDamage)`. Определяет новую фазу и флаг диалога смены стойки. Сбрасывает `inputMode` в `NONE`, очищает поле ввода.

---

#### `confirmPhaseChange(damageForWound: Int, healthForStanceChange: Int)`
**Строка:** `189`

Подтверждает смену стойки: вызывает `monster.resetPhase(...)`, закрывает диалог.

---

#### `confirmRageSurge()`
**Строка:** `303`

Сбрасывает ярость до значения `hunterCount`, закрывает диалог «Всплеск ярости». Вызывается при достижении условия `rage >= hunterCount * 3`.

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

Переключает `monster.isHardened`.

---

#### `endRound()`
**Строка:** `367`

Завершает раунд:
1. Если `pendingDamage > 0` — вызывает `monster.takeDamage(pendingDamage)`
2. `monster.endRound(hunterCount)` — добавляет ярость за охотников
3. `currentRound += 1`
4. Если `currentRound > maxRounds` → `DEFEAT`
5. Сбрасывает `inputMode` в `NONE`, `canUndo = false`
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
    val currentCampaign: Campaign? = null,
    val hunters: List<CampaignHunter> = emptyList(),
    val selectedHunterIndex: Int = 0,
    val skills: List<SkillNode> = emptyList(),
    val materials: Map<Material, Int> = emptyMap(),
    val plants: Map<Plant, Int> = emptyMap(),
    val elements: Map<Element, Int> = emptyMap(),
    val notes: String = "",
    val availableSkillBranches: List<SkillBranch> = emptyList(),
    val showExchangeDialog: Boolean = false,
    val exchangeResourceType: ResourceType? = null,
    val exchangeResourceName: String = "",
    val exchangeAmount: String = "0",
    val showPostVictory: Boolean = false,
    val bossName: String = "",
    val bossElement: Element? = null,
    val completedQuestId: String = "",
    val availableQuestsForNext: List<Quest> = emptyList(),
    val selectedNextQuestId: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String = "",
    val error: String? = null
)
```

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
| `onUpdateChapter(chapter)` | Ручное изменение главы |
| `onOpenExchange(type, name)` | Открытие диалога обмена |
| `onCloseExchange()` | Закрытие диалога обмена |
| `onExchangeAmountChanged(amount)` | Ввод количества для обмена |
| `onStartCampaignBattle()` | Запуск боя в рамках кампании |
| `getBattleViewModel()` | Получение BattleViewModel для UI |
| `onBattleFinished()` | Завершение боя, возврат к листу |
| `onVictory()` | Открытие пост-победного диалога |
| `onVictoryBossNameChanged(name)` | Ввод имени босса |
| `onVictoryBossElementChanged(element)` | Выбор стихии босса |
| `onVictoryNextQuestSelected(questId)` | Выбор следующего квеста |
| `onConfirmVictory()` | Сохранение победы (трофей + квест + глава) |
| `onBackToMenu()` | Возврат в главное меню |
| `onErrorDismissed()` | Сброс сообщения об ошибке |

---

## 5. Слой данных (Room KMP)

### 5.1 Entity (7 таблиц)

| Entity | Таблица | Поля |
|--------|---------|------|
| `CampaignEntity` | `campaigns` | id, name, currentChapter, forgeLevel, labLevel, notes, createdAt, updatedAt |
| `HunterEntity` | `hunters` | id, campaignId (FK→campaigns), playerName, className |
| `SkillEntity` | `skills` | id, hunterId (FK→hunters), branch, tier, unlocked |
| `ResourceEntity` | `resources` | id, hunterId (FK→hunters), resourceType, resourceName, quantity |
| `AchievementEntity` | `achievements` | id, campaignId (FK→campaigns), achievementId, name, description, unlocked |
| `TrophyEntity` | `trophies` | id, campaignId (FK→campaigns), bossName, element, chapter, acquiredAt |
| `QuestEntity` | `quests` | id, campaignId (FK→campaigns), questId, name, chapter, element, isCompleted, isAvailable |

### 5.2 DAO (7 интерфейсов)

| DAO | Основные методы |
|-----|-----------------|
| `CampaignDao` | getAllCampaigns (Flow), getCampaign, insert/update/delete, getCount |
| `HunterDao` | getHunters (Flow), getHunter, insert/update/delete, insertReturningId |
| `SkillDao` | getSkills (Flow), insert, setUnlocked, deleteByHunter |
| `ResourceDao` | getResources (Flow), getByType, getResource, updateQuantity, getAlliesWithResource |
| `AchievementDao` | getAchievements (Flow), insert, setUnlocked, deleteByCampaign |
| `TrophyDao` | getTrophies (Flow), insert, deleteByCampaign |
| `QuestDao` | getQuests (Flow), getAvailable, getCompleted, insert, completeQuest, makeQuestAvailable |

### 5.3 Platform (expect/actual)

| Файл | Назначение |
|------|-----------|
| `Platform.kt` (commonMain) | `expect class PlatformContext`, `expect fun currentTimeMillis()`, `expect fun createPrimalDatabase()` |
| `Platform.android.kt` (androidMain) | `actual typealias PlatformContext = Context`, `Room.databaseBuilder(context, ...)` |
| `Platform.ios.kt` (iosMain) | `actual class PlatformContext`, `NSFileManager` путь + `Room.databaseBuilder(name = ...)` |

---

## 6. Компоненты UI (Android)
