# Архитектура приложения Primal App

## 1. Модульная структура

```
┌─────────────────────────────────────────────────────────┐
│                        primalApp                        │
│                                                         │
│  ┌─────────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │     :shared      │  │ :androidApp │  │   iosApp    │ │
│  │   (KMP Library)  │  │  (Android)  │  │   (iOS)     │ │
│  │                  │  │             │  │             │ │
│  │  commonMain      │  │  Compose UI │  │  SwiftUI    │ │
│  │  ├── model/       │  │  ↓          │  │  ↓          │ │
│  │  ├── model/campaign/│ │  ViewModel  │  │  ViewModel  │ │
│  │  ├── model/ext/   │  │  ↑          │  │  ↑          │ │
│  │  ├── domain/      │  │  depends──→ │  │  depends──→ │ │
│  │  ├── viewmodel/   │  │             │  │             │ │
│  │  ├── repository/  │  │             │  │             │ │
│  │  └── database/    │  │             │  │             │ │
│  │      ├── entity/  │  └─────────────┘  └─────────────┘ │
│  │      ├── dao/     │                                   │
│  │      └── mapper/  │                                   │
│  │  commonTest      │  │             │  │             │ │
│  │  ├── MonsterTest │  └─────────────┘  └─────────────┘ │
│  │  └── HunterTest  │                                   │
│  │                  │                                   │
│  │  androidMain     │                                   │
│  │  iosMain         │                                   │
│  └─────────────────┘                                   │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Слои приложения (MVVM + Clean Architecture)

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  ┌──────────────────────┐  ┌──────────────────────────┐ │
│  │  Android: Compose UI  │  │  iOS: SwiftUI Views      │ │
│  │  MainActivity.kt      │  │  ContentView.swift       │ │
│  │  ├── PreBattleScreen  │  │                          │ │
│  │  ├── BattleScreen     │  │                          │ │
│  │  ├── PhaseChangeDialog│  │                          │ │
│  │  ├── RageSurgeDialog  │  │                          │ │
│  │  ├── VictoryScreen    │  │                          │ │
│  │  └── DefeatScreen     │  │                          │ │
│  └──────────┬───────────┘  └─────────────┬────────────┘ │
│             │ collectAsState()            │ @Observed    │
│             ▼                             ▼              │
├─────────────────────────────────────────────────────────┤
│                     ViewModel Layer                      │
│  ┌──────────────────────────────────────────────────────┐│
│  │  BattleViewModel (shared/commonMain)                 ││
│  │  ├── _state: MutableStateFlow<BattleScreenState>     ││
│  │  ├── timerJob: Job?                                   ││
│  │  ├── lastSnapshot: MonsterSnapshot?                   ││
│  │  └── Methods: startBattle, onDamageInputChanged,       ││
│  │              onQuickButtonPress, onOkPress,             ││
│  │              onCancelPress, onUndoPress, commitDamage,  ││
│  │              addRage, removeRage, toggleHardened,       ││
│  │              endRound, confirmPhaseChange, resetBattle,  ││
│  │              confirmRageSurge                            ││
│  └────────────────────────┬─────────────────────────────┘│
│                           │                              │
├───────────────────────────┼──────────────────────────────┤
│                     Domain Layer                          │
│  ┌────────────────────────┼─────────────────────────────┐│
│  │  Validators & Progression (shared/commonMain)        ││
│  │  ├── SkillValidatorImpl.kt: canUnlock,               ││
│  │  │   getAvailableBranches                             ││
│  │  ├── ResourceExchangeValidatorImpl.kt: 1:1 exchange   ││
│  │  ├── ChapterProgressionImpl.kt: chapter + forge/lab   ││
│  │  └── ExchangeResult (Valid/Invalid)                   ││
│  │                                                       ││
│  │  Extension Functions (shared/commonMain)             ││
│  │  ├── MonsterExt.kt: takeDamage, addRage, removeRage,  ││
│  │  │   addRagePerHunter, endRound, toggleHardened,      ││
│  │  │   resetPhase                                       ││
│  │  └── HunterExt.kt: takeDamage, heal, revive,          ││
│  │      isCritical                                       ││
│  └────────────────────────┬─────────────────────────────┘│
│                           │                              │
├───────────────────────────┼──────────────────────────────┤
│                      Data Layer                           │
│  ┌────────────────────────┼─────────────────────────────┐│
│  │  CampaignRepository (interface)                       ││
│  │  ├── getAllCampaigns, getCampaign, create/save/delete ││
│  │  ├── getHunters, addHunters                           ││
│  │  ├── getSkills, unlockSkill                           ││
│  │  ├── getMaterials/Plants/Elements, addResource        ││
│  │  ├── exchangeResources, advanceChapter                ││
│  │  └── saveVictory, quests, achievements, trophies      ││
│  │                                                       ││
│  │  Room Database (shared/commonMain)                    ││
│  │  ├── Entity: Campaign, Hunter, Skill, Resource,       ││
│  │  │   Achievement, Trophy, Quest (7 tables)            ││
│  │  ├── DAO: CampaignDao, HunterDao, SkillDao,           ││
│  │  │   ResourceDao, AchievementDao, TrophyDao, QuestDao ││
│  │  ├── Mapper: Entity ↔ Domain                          ││
│  │  └── Platform: expect/actual для Room.Builder          ││
│  │                                                       ││
│  │  Data Classes (shared/commonMain)                    ││
│  │  ├── Monster: name, currentPhase, currentHealth,      ││
│  │  │   accumulatedDamage, damageForWound,               ││
│  │  │   healthForStanceChange, rage, isHardened,         ││
│  │  │   isDefeated, isLastPhase                          ││
│  │  ├── Hunter: name, maxHealth, currentHealth,          ││
│  │  │   isUnconscious, isAlive, healthPercentage         ││
│  │  └── DamageResult: woundsInflicted, remainingDamage,  ││
│  │      phaseChanged, newPhase, message                  ││
│  └──────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

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
  3. val result = monster.takeDamage(11)
        │
        ▼
Monster.takeDamage(11):
  → DamageResult(wounds=2, remaining=3, phaseChanged=true)
        │
        ▼
commitDamage() (продолжение):
  - phase = PHASE_II, showPhaseChangeDialog = true
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

```
┌─────────────────────────────────────────────────────────────┐
│                     Version Catalog                          │
│  gradle/libs.versions.toml                                  │
│  ├── kotlin 2.0.21                                          │
│  ├── agp 8.13.2                                             │
│  ├── coroutines 1.8.1                                       │
│  ├── composeBom 2024.09.00                                  │
│  ├── activityCompose 1.9.1                                  │
│  ├── room 2.6.1 (KMP)                                       │
│  ├── ksp 2.0.21-1.0.28                                      │
│  └── kotlinx-datetime 0.6.1                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
┌─────────────┐ ┌───────────┐ ┌───────────┐
│   :shared   │ │:androidApp│ │  iosApp   │
│             │ │    │      │ │    │      │
│ kotlinx-    │ │    ├──────┤ │    ├──────┤
│ coroutines  │ │    │shared│ │    │shared│
│ room-runtime│ │    │      │ │    │      │
│ room-ktx    │ │ compose │ │ SwiftUI │
│ room-comp.  │ │ material│ │         │
│ (KSP)       │ │         │ │         │
│ kotlinx-    │ │         │ │         │
│ datetime    │ │         │ │         │
└─────────────┘ └───────────┘ └───────────┘
```

---

## 6. Основные проектные решения

| Решение | Причина |
|---------|---------|
| MVVM, а не MVC/MVP | Чёткое разделение UI и логики, поддержка KMP |
| StateFlow, а не LiveData | KMP-совместимость (LiveData — Android-only) |
| Room KMP 2.6.1 для хранения | Единый слой данных в shared/commonMain через expect/actual для Room.Builder; персистентность кампаний доступна на Android и iOS |
| expect/actual для PlatformContext | Room.databaseBuilder требует платформенный контекст (Android Context vs iOS NSFileManager путь) |
| expect/actual для currentTimeMillis() | System.currentTimeMillis() недоступен в KMP commonMain; заменён на Kotlin-обёртку |
| Функции-расширения, а не методы классов | Идиоматичный Kotlin, разделение модели и логики |
| `var` поля в data class, а не `copy()` | Производительность (мутабельные поля при частых обновлениях) |
| `DamageResult` как возвращаемый тип | Полная информация о результате для UI |
| `FightPhase` enum, а не sealed class | Простота, 7 фиксированных состояний |
| Version Catalog (`libs.versions.toml`) | Единая точка управления версиями, рекомендовано Google |
| `CoroutineScope(SupervisorJob() + Dispatchers.Main)` | Отказоустойчивость: падение одной корутины не убивает остальные |
