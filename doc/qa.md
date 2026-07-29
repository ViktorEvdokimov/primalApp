# Вопросы и ответы по проекту Primal App

## A. Вопросы, решённые разработчиком самостоятельно (best practices)

### 1. На какой платформе разрабатывать?

**Решение:** Kotlin Multiplatform (KMP) — общая бизнес-логика в `shared/`, платформенный UI в `androidApp/` (Compose) и `iosApp/` (SwiftUI).

**Реализация:** `shared/build.gradle.kts:1` — `kotlin("multiplatform")` с targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.

---

### 2. Какой UI-фреймворк?

**Решение:** Jetpack Compose + Material3 для Android. Shared-модуль не содержит UI-кода — только модели, ViewModel и бизнес-логику.

**Реализация:** `androidApp/build.gradle.kts:1` — плагин `composeCompiler`, зависимости `compose-bom`, `compose-ui`, `compose-material3`.

---

### 3. Какая архитектура?

**Решение:** MVVM. `BattleViewModel` (`BattleViewModel.kt:49`) управляет состоянием через `MutableStateFlow<BattleScreenState>`. UI наблюдает через `collectAsState()` (`MainActivity.kt:55`).

---

### 4. Как хранить данные на устройстве?

**Решение:** Room KMP 2.6.1. Вся БД (7 Entity, 7 DAO, mapper, CampaignRepositoryImpl) расположена в `shared/src/commonMain/kotlin/com/primalapp/database/`. Platform-specific код (Room.databaseBuilder, currentTimeMillis) вынесен в expect/actual.

**Реализация:**
- `shared/.../database/Platform.kt` — expect-декларации
- `shared/.../database/Platform.android.kt` — Android actual (`Context`, `Room.databaseBuilder`)
- `shared/.../database/Platform.ios.kt` — iOS actual (`NSFileManager`, `Room.databaseBuilder`)
- `shared/.../database/PrimalDatabase.kt` — `@Database` с 7 таблицами
- `shared/.../database/CampaignRepositoryImpl.kt` — полная реализация CampaignRepository

---

### 5. Как реализовать таймер 2 секунды для добора урона?

**Решение:** `Job` корутины с `delay(2000)`. Каждое нажатие кнопки отменяет предыдущий Job и запускает новый. Значение суммируется в `pendingDamage`.

**Реализация:** `BattleViewModel.kt:84–98` — метод `addDamage`:
```kotlin
timerJob?.cancel()                          // отмена предыдущего
pendingDamage += amount                     // накопление
timerJob = scope.launch { delay(2000); commitDamage() }  // автоприменение
```

---

### 6. Как обрабатывать ввод урона с клавиатуры?

**Решение:** `OutlinedTextField` с `KeyboardType.Number` + кнопки быстрого добавления. Валидация: `toIntOrNull() ?: return`.

**Реализация:** `MainActivity.kt:226–245` — поле + кнопка OK, вызов `viewModel.setManualDamage(dmg)` (`BattleViewModel.kt:101–109`).

---

### 7. Какой минимальный SDK?

**Решение:** API 26 (Android 8.0, 2017).

**Реализация:** `androidApp/build.gradle.kts:12` — `minSdk = 26`.

---

### 8. Как организовать фазы боя?

**Решение:** `enum class FightPhase` (`BattleViewModel.kt:23–31`) — 7 состояний: `PRE_BATTLE`, `SETUP`, `PHASE_I`, `PHASE_II`, `PHASE_III`, `VICTORY`, `DEFEAT`. Переходы управляются через `when` в `commitDamage()` и `endRound()`.

---

### 9. Как хранить до 10 кампаний и переключаться?

**Решение:** Room-таблица `campaigns` (max 10 проверяется через `getMaxCampaigns() = 10`). Каскадные связи: Campaign → Hunters → Skills, Resources; Campaign → Achievements, Trophies, Quests. UI — `MainMenuScreen` → `CampaignListScreen` → `CampaignSheetScreen`.

**Реализовано:** `shared/.../database/` (Room KMP), `shared/.../viewmodel/CampaignViewModel.kt`, `androidApp/.../ui/CampaignListScreen.kt`.

---

### 10. Структура классов данных?

**Решение:** `data class` c мутабельными `var` полями для часто меняющегося состояния (здоровье, ярость). Иммутабельные `val` для констант. Computed-свойства (`isAlive`, `healthPercentage`, `isLastPhase`).

**Реализация:**
- `Monster.kt:3–21` — 2 `val` + 9 `var` + 1 computed
- `Hunter.kt:3–16` — 2 `val` + 2 `var` + 2 computed

---

## B. Вопросы, заданные пользователю

### 11. Что означает статус «затвердевший»?

**Вопрос:** Как работает механика «затвердевший»?

**Ответ:** Остаток урона после нанесения раны сгорает (не переносится). Без статуса — остаток сохраняется.

**Реализация:** `MonsterExt.kt:45–51`:
```kotlin
val remaining = if (isHardened && wounds > 0) {
    val leftover = accumulatedDamage
    accumulatedDamage = 0       // остаток сгорает
    leftover
} else {
    accumulatedDamage           // остаток сохраняется
}
```

**Тест:** `MonsterTest.kt:83–111` — 2 теста (hardened vs non-hardened при 8 и 9 урона).

---

### 12. Как работает таймер на 2 секунды?

**Вопрос:** Сбрасывается ли таймер при новом нажатии?

**Ответ:** Сброс при каждом нажатии.

**Реализация:** `BattleViewModel.kt:91` — `timerJob?.cancel()` перед запуском нового. Каждое нажатие (+1/+10/+50) перезапускает 2-секундный отсчёт.

---

### 13. Какой тип проекта: Android или KMP?

**Вопрос:** Чистый Android или Kotlin Multiplatform?

**Ответ:** KMP (Android + iOS).

**Реализация:** `settings.gradle.kts:12–13` — `include(":shared")` и `include(":androidApp")`. Структура: `shared/src/commonMain`, `androidApp/`, `iosApp/`.

---

## C. Вопросы, выявленные при реализации

### 14. Как обрабатывать смену стойки в середине цепочки ран?

**Проблема:** При большом уроне (например, 8 урона при `damageForWound=4`) наносится 2 раны. Если после первой раны здоровье падает ниже `healthForStanceChange`, нужно сменить фазу — но вторая рана наносится уже с новыми параметрами.

**Решение:** Фаза проверяется и меняется **между ранами** внутри цикла `while`. Сразу после `currentPhase++` следующие итерации цикла продолжают использовать текущий `damageForWound` (он не изменился до вызова `resetPhase` из ViewModel).

**Реализация:** `MonsterExt.kt:22–42` — цикл `while (accumulatedDamage >= damageForWound)` с проверкой `currentPhase < maxPhases` внутри.

**Тест:** `MonsterTest.kt:53–66` — проверка фазового перехода при 8 урона, `healthForStanceChange=8`.

---

### 15. Почему `toggleHardened()` проверяет `wounds > 0`?

**Проблема:** Если `isHardened = true`, но ни одной раны не нанесено (урон меньше `damageForWound`), остаток НЕ должен сгорать — он копится к следующей ране.

**Решение:** Условие `isHardened && wounds > 0`. Сгорание только если была хотя бы одна рана.

**Реализация:** `MonsterExt.kt:45` — `if (isHardened && wounds > 0)`.

---

### 16. Как предотвратить игнорирование ручного ввода урона?

**Проблема:** Если пользователь вводит урон руками, а через 2 секунды таймер от предыдущего нажатия кнопки доберёт остаток — получится двойное применение.

**Решение:** `setManualDamage` (`BattleViewModel.kt:101–109`) отменяет активный таймер (`timerJob?.cancel()`) и НЕ запускает новый. Урон применяется только по кнопке «Применить урон сейчас» или через ручной вызов `commitDamage()`.

---

### 17. Как обеспечивается потокобезопасность StateFlow?

**Решение:** `MutableStateFlow` гарантирует атомарные обновления через `update {}`. Все изменения состояния проходят через `_state.update { it.copy(...) }`.

**Реализация:** Например, `BattleViewModel.kt:134–144` — `_state.update { it.copy(phase = newPhase, monster = current.monster, ...) }`.

---

## D. Тестовое покрытие

| Файл | Количество тестов | Что проверяется |
|------|------------------|-----------------|
| `MonsterTest.kt` | 12 | Раны, урон, hardened, фазы, добивание, сброс фазы, ярость |
| `HunterTest.kt` | 10 | Урон, лечение, потеря сознания, воскрешение, крит, проценты |
| **Всего** | **22** | Полное покрытие моделей и функций-расширений |

---

## E. Вопросы по доработке «Лист компании» (v2)

### 18. Классы персонажей — 4 или 6?

**Вопрос (27.07.2026):** В требовании перечислено 6 классов: Кара, Хелерен, Дареон, Мира, Торег, Льонар. В базовой книге правил только 4. Откуда Кара и Хелерен?

**Ответ:** Кара и Хелерен из дополнений. Использовать все 6 классов.

**Реализация:** `HunterClass.kt` — enum из 6 значений: DAREON, MIRA, TOREG, LIONAR, KARA, HELEREN.

---

### 19. Количество материй — 6 или 7?

**Вопрос (27.07.2026):** В требовании указано 6 видов материй, в правилах — 7: Кости, Кристалл, Кровь, Чешуя, Зимия, Иридия, Златия.

**Ответ:** Использовать 7 материй как в правилах.

**Реализация:** `Material.kt` — enum из 7 значений.

---

### 20. Главы апгрейда кузни/лаборатории

**Вопрос (27.07.2026):** Подтверждены ли главы 3 и 7 для апгрейда? В правилах точные номера не зафиксированы.

**Ответ:** Главы 3 и 7 (как в требовании).

**Реализация:** `domain/ChapterProgression.kt:10-11` — методы `getForgeUpgradeChapter(2) → 3`, `getForgeUpgradeChapter(3) → 7`.

---

### 21. Исключение первого боя

**Вопрос (27.07.2026):** Что означает «кроме первого боя» в требовании?

**Ответ:** Помечать все бои, включая первый (Пролог — полноценная глава).

---

### 22. Название режима: «Быстрый бой» или «Режим экспедиции»?

**Вопрос (27.07.2026):** Какой термин использовать в интерфейсе?

**Ответ:** «Режим экспедиции» (как в официальных правилах).

---

### 23. Список 9 стихий

**Вопрос (27.07.2026):** Какие именно 9 стихий использовать?

**Ответ:** Полный список из правил — 6 базовых (Огонь, Рог, Коралл, Кристалл, Молния, Металл) + 3 дополнения (Перо, Яд, Лёд).

**Реализация:** `Element.kt` — enum из 9 значений с флагом `isExpansion`.

---

## F. Результаты реализации слоя данных (27.07.2026)

**Перенесён в Task4 (28.07.2026) — Room перемещён из androidApp в shared/commonMain.**

### Созданные/изменённые файлы

**shared/commonMain — доменный слой (13 файлов):**
| Файл | Назначение |
|------|-----------|
| `model/campaign/Campaign.kt` | Кампания: id, имя, глава, кузня, лаборатория, заметки |
| `model/campaign/CampaignHunter.kt` | Охотник в кампании: имя игрока + класс |
| `model/campaign/SkillNode.kt` | Узел древа навыков: ветвь + ступень + флаг |
| `model/campaign/Achievement.kt` | Достижение |
| `model/campaign/Trophy.kt` | Трофей (босс, стихия, глава) |
| `model/campaign/Quest.kt` | Задание: id, глава, стихия, статус |
| `model/campaign/ResourceEntry.kt` + `ResourceType.kt` | Запись о ресурсе + enum типа |
| `model/campaign/HunterClass.kt` | 6 классов (Дареон, Мира, Торег, Льонар, Кара, Хелерен) |
| `model/campaign/SkillBranch.kt` | 5 ветвей (А, Б, В, Г, Д) |
| `model/campaign/Material.kt` | 7 материй |
| `model/campaign/Plant.kt` | 6 растений |
| `model/campaign/Element.kt` | 9 стихий (6 базовых + 3 дополнения) |
| `repository/CampaignRepository.kt` | Интерфейс: CRUD кампаний, охотников, навыков, ресурсов, обмен, главы |
| `domain/SkillValidator.kt` + `SkillValidatorImpl.kt` | Валидация разблокировки навыков |
| `domain/ResourceExchangeValidator.kt` + `ResourceExchangeValidatorImpl.kt` | Валидация обмена (1:1, same-type) |
| `domain/ChapterProgression.kt` + `ChapterProgressionImpl.kt` | Продвижение глав, апгрейд кузни/лаборатории |

**androidApp — слой Room (удалён 28.07.2026, перенесён в shared):**

| Файл | Назначение | Статус |
|------|-----------|--------|
| `database/*` | Все файлы БД | Перенесены в `shared/.../database/` |

**Изменённые конфиги:**
- `gradle/libs.versions.toml` — KSP 2.0.21-1.0.28, Room 2.6.1, kotlinx-datetime 0.6.1
- `build.gradle.kts` — плагин KSP
- `shared/build.gradle.kts` — KSP + Room KMP + kotlinx-datetime (добавлено 28.07.2026)
- `androidApp/build.gradle.kts` — KSP и Room удалены (28.07.2026, транзитивно через shared)

### Coverage требований

| Требование | Статус | Где реализовано |
|-----------|--------|-----------------|
| Дерево навыков А1-Д2, tier 2 требует tier 1 | ✅ | `SkillValidatorImpl`, `initSkillTree()` |
| 7 материй, ввод числа, обмен | ✅ | `ResourceEntity`, `exchangeResources()` |
| 6 растений, аналогично материям | ✅ | тот же механизм |
| 9 стихий, аналогично | ✅ | тот же механизм |
| Заметки | ✅ | `Campaign.notes` |
| Глава авто-инкремент + ручной режим | ✅ | `advanceChapter()`, `updateChapter()` |
| Кузня/лаб: старт 1, гл.3→2, гл.7→3 | ✅ | `ChapterProgressionImpl` |
| Пост-победный флоу | ✅ | `saveVictory()` |
| Выбор «Режим экспедиции» / «Режим кампании» | ✅ | `MainMenuScreen` + `onQuickBattleSelected()` / `onCampaignModeSelected()` |
| Название кампании + выбор классов | ✅ | `createCampaign()`, `addHunters()` |
| Автосохранение до 10 кампаний | ✅ | `getCampaignCount()`, `getMaxCampaigns()=10` |
| UI / ViewModel | ✅ | `CampaignViewModel`, все экраны в `androidApp/.../ui/` |

### Результат сборки

```
BUILD SUCCESSFUL in 6s
30 actionable tasks: 4 executed, 26 up-to-date
```

## G. Task4: Миграция Room в shared (28.07.2026)

### 24. Почему Room перемещён из androidApp в shared?

**Причина:** Обеспечить единый слой данных в KMP-модуле для Android и iOS. До миграции Room находился в `androidApp/`, что делало его недоступным для iOS-таргета.

**Реализация:** Весь слой БД (7 Entity, 7 DAO, mapper, PrimalDatabase, CampaignRepositoryImpl, Platform expect/actual) перемещён из `androidApp/.../database/` в `shared/.../database/`.

### 25. Какие KMP-адаптации потребовались?

| Проблема | Решение |
|----------|---------|
| `System.currentTimeMillis()` недоступен в commonMain | `expect fun currentTimeMillis()`: Android → `System.currentTimeMillis()`, iOS → `NSDate().timeIntervalSince1970 * 1000` |
| `Room.databaseBuilder()` требует платформенный контекст | `expect fun createPrimalDatabase(PlatformContext)`: Android → `Context`, iOS → `String` (путь к БД) |
| Entity default values с `System.currentTimeMillis()` | Заменены на `currentTimeMillis()` |
| CampaignRepositoryImpl с `System.currentTimeMillis()` | Заменён на `currentTimeMillis()` |
| Converters.kt (пустой) | Удалён |

### 26. Почему Room KMP 2.6.1, а не SQLDelight?

- Room сохраняет 100% существующей логики (аннотации `@Entity`/`@Dao`/`@Database` работают в commonMain без изменений)
- SQLDelight потребовал бы переписывания всех DAO в `.sq` файлы
- Room 2.6.x имеет KMP-поддержку (commonMain + platform-specific через expect/actual)

### 27. Что изменилось в androidApp?

- `MainActivity.kt`: импорт `CampaignRepositoryImpl`, `PlatformContext`, `createPrimalDatabase` теперь из `com.primalapp.database` вместо `com.primalapp.android.database`
- Создание БД: `createPrimalDatabase(context as PlatformContext)` вместо `Room.databaseBuilder(context, PrimalDatabase::class.java, "primal.db").build()`
- `build.gradle.kts`: удалены KSP и Room-зависимости (транзитивно через shared)
- Удалён весь каталог `androidApp/.../database/` |
