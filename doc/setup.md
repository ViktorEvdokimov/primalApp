# Инструкция по запуску

## Требования к окружению

| Инструмент | Версия |
|-----------|--------|
| JDK | 17+ |
| Kotlin | 2.0.21 (встроен в Gradle-плагин) |
| Android Studio | Hedgehog (2023.1.1) или новее |
| Gradle | 8.7 (через wrapper — автоустановка) |
| Android SDK | API 34 (compile), API 26 (min) |
| Xcode | 15+ (только для iOS-сборки, macOS) |

---

## Первый запуск в Android Studio (рекомендуемый способ)

1. **Открыть проект:**
   ```
   File → Open → выбрать D:\projekts\promptLearning\primalApp
   ```

2. **Дождаться синхронизации Gradle.** Android Studio автоматически:
   - Обнаружит `gradlew.bat` (Gradle wrapper)
   - Загрузит Gradle 8.7 (~10–30 сек при первом запуске)
   - Синхронизирует все зависимости

   Если синхронизация не запустилась автоматически:
   ```
   File → Sync Project with Gradle Files  (Ctrl+Shift+O по умолчанию)
   ```

3. **Убедиться, что синхронизация прошла успешно** — в панели `Build` не должно быть ошибок, в Gradle-панели (View → Tool Windows → Gradle) должны отображаться модули и задачи.

4. **Запустить приложение:**
   - В выпадающем списке конфигураций (рядом с кнопкой Run) **явно выберите `androidApp`** (а не `shared [allTests]`)
   - Нажмите **Run** (Shift+F10)
   - Выберите эмулятор или подключённое устройство

   > **Важно:** если в списке выбрано `shared [allTests]` или `shared [test]` — запустятся только тесты, APK на устройство установлен не будет. Убедитесь, что выбрано `androidApp`.

---

## Запуск на виртуальном устройстве (Android Emulator)

Если у вас нет физического Android-устройства, приложение можно запустить на эмуляторе, встроенном в Android Studio.

### Создание виртуального устройства (AVD)

1. Откройте **Device Manager** (кнопка в правом верхнем углу Android Studio, либо `View → Tool Windows → Device Manager`).

2. Нажмите **Create device** (кнопка `+`).

3. **Выберите профиль устройства:**
   - Рекомендуется **Pixel 6** или **Pixel 7** (средний размер экрана, хорошая производительность)
   - Нажмите **Next**

4. **Выберите образ системы (System Image):**
   - Вкладка **Recommended** → выберите **Tiramisu** (API 33) или **UpsideDownCake** (API 34)
   - Если образ не скачан — нажмите **Download** рядом с названием (потребуется ~1–2 ГБ трафика)
   - Нажмите **Next**

5. **Настройте AVD:**
   - **AVD Name:** оставьте по умолчанию или задайте своё (например, `Pixel 6 API 34`)
   - **Graphics:** выберите **Hardware - GLES 2.0** (аппаратное ускорение) для лучшей производительности
   - **RAM:** рекомендуется не менее **2048 MB** (2 ГБ)
   - Нажмите **Finish**

6. Виртуальное устройство появится в списке Device Manager.

### Системные требования для эмулятора

| Компонент | Требование |
|-----------|-----------|
| Процессор | Intel Core i5 / AMD Ryzen 5 или выше |
| ОЗУ | 8 ГБ минимум, 16 ГБ рекомендуется |
| Свободное место | ~10 ГБ (образ системы + кэш) |
| Виртуализация | **Включена в BIOS** — Intel VT-x / AMD-V |
| ОС | Windows 10/11 64-bit |

### Включение аппаратной виртуализации (если эмулятор не запускается)

**Windows:**
1. Перезагрузите компьютер и войдите в BIOS (обычно клавиши `F2`, `F10`, `Del` при загрузке)
2. Найдите настройку **Intel VT-x**, **Intel Virtualization Technology** или **AMD SVM Mode**
3. Включите её (**Enabled**)
4. Сохраните и выйдите (обычно `F10`)

**Проверка после загрузки Windows:**
```powershell
# Откройте PowerShell от имени администратора и выполните:
systeminfo | findstr /C:"Virtualization"
```
Если вывод содержит `Enabled` — виртуализация активна.

### Запуск приложения на эмуляторе

1. **Запустите эмулятор** — в Device Manager нажмите кнопку ▶ напротив созданного AVD. Дождитесь полной загрузки Android (рабочий стол).

2. **Запустите приложение** (Shift+F10) — Android Studio автоматически установит APK на запущенный эмулятор.

3. Если эмулятор ещё не запущен, Android Studio предложит выбрать устройство при первом запуске — выберите ваш AVD из списка.

### Управление эмулятором

| Панель | Действие |
|--------|---------|
| Боковая панель эмулятора | Кнопки: назад, домой, недавние, поворот экрана, громкость, скриншот |
| `Ctrl+M` | Открыть меню (аналог кнопки меню) |
| Двойной клик мыши | Тап по экрану |
| Колесо мыши | Вертикальная прокрутка |
| Перетаскивание мышью | Свайп |

---

## Запуск через командную строку (PowerShell / CMD)

> **Важно:** `gradlew.bat` — это скрипт, запускающий Gradle. Его нужно вызывать из командной строки (CMD или PowerShell), а НЕ как аргумент Gradle-задачи.

**Windows (PowerShell / CMD):**

```powershell
# Сборка Android-приложения
.\gradlew.bat :androidApp:assembleDebug

# Запуск тестов
.\gradlew.bat :shared:allTests

# Полная сборка shared-модуля
.\gradlew.bat :shared:build

# Установка на подключённое устройство
.\gradlew.bat :androidApp:installDebug

# Просмотр всех доступных задач
.\gradlew.bat tasks
```

**Linux / macOS:**

```bash
./gradlew :shared:build
./gradlew :androidApp:assembleDebug
```

---

## Как запускать задачи в Gradle-панели Android Studio

После успешной синхронизации все задачи доступны в панели **Gradle** (View → Tool Windows → Gradle).

1. Разверните дерево: `PrimalApp → Tasks → build`
2. Дважды кликните на задачу (например, `assembleDebug`)
3. **Не вводите** `./gradlew` или `gradlew` в поле поиска задач — это не задача Gradle

Примеры задач для двойного клика:

| Задача | Где найти |
|--------|----------|
| `:androidApp:assembleDebug` | PrimalApp → androidApp → Tasks → build |
| `:shared:allTests` | PrimalApp → shared → Tasks → verification |
| `:shared:build` | PrimalApp → shared → Tasks → build |

---

## Запуск iOS-версии

> Требуется macOS + Xcode 15+

1. Открыть `iosApp/iosApp.xcodeproj` в Xcode
2. Выбрать симулятор
3. Run (Cmd+R)

---

## Типовые ошибки и их решение

### `Task './gradlew' not found`

**Причина:** `./gradlew` был передан как **аргумент задачи Gradle**, а не вызван как исполняемый скрипт.

**Решение:** Запускайте `gradlew.bat` из **терминала** (PowerShell/CMD), а не из Gradle-панели:
```powershell
.\gradlew.bat :shared:build          # правильно
```
```powershell
gradle ./gradlew :shared:build       # НЕПРАВИЛЬНО
```

### `Unresolved reference: plugins`

**Причина:** BOM-байты (Byte Order Mark) в начале файла — файл повреждён.

**Решение:** Пересохранить файл без BOM через `File → Encoding → UTF-8` (без BOM) в Android Studio.

### `Plugin [...] was not found`

**Причина:** Плагин отсутствует в репозитории (неверный ID или версия).

**Решение:** Проверить `gradle/libs.versions.toml` — сверить ID и версии плагинов с официальной документацией.

### Gradle sync fails with timeouts

**Причина:** Медленное или нестабильное интернет-соединение.

**Решение:**
1. Проверить интернет-соединение
2. Очистить кэш Gradle: удалить `C:\Users\<user>\.gradle\caches`
3. Повторить `File → Sync Project with Gradle Files`

### Приложение не появляется на эмуляторе (иконка отсутствует)

**Причина:** в выпадающем списке Run Configuration выбрана не та цель — например, `shared [allTests]` вместо `androidApp`.

**Решение:**
1. Нажмите на выпадающий список слева от кнопки Run
2. Выберите **`androidApp`** (а не `shared` или `shared [allTests]`)
3. Если `androidApp` отсутствует: `Run → Edit Configurations → + → Android App → Module: androidApp → OK`
4. Нажмите Run (Shift+F10)

### Сборка успешна, но APK не устанавливается на эмулятор

**Причина:** эмулятор не запущен, или `adb` потерял соединение.

**Решение:**
1. Убедитесь, что эмулятор запущен (виден рабочий стол Android) — откройте Device Manager и проверьте статус
2. В терминале выполните `adb devices` — в списке должен быть эмулятор (`emulator-5554 device`)
3. Если эмулятор в статусе `offline`: перезапустите его через Device Manager
4. Вручную установите APK: `adb install androidApp\build\outputs\apk\debug\androidApp-debug.apk`

---

## Структура проекта (реальная)

```
primalApp/
├── build.gradle.kts                               # Корневой: объявление плагинов
├── settings.gradle.kts                            # include(":shared"), include(":androidApp")
├── gradle.properties                              # JVM args, AndroidX, KMP-флаги
├── gradlew / gradlew.bat                          # Gradle Wrapper (Windows + Unix)
├── .gitignore
│
├── gradle/
│   ├── libs.versions.toml                         # Каталог версий
│   │   ├── [versions]  kotlin=2.0.21, agp=8.13.2, coroutines=1.8.1, composeBom=2024.09.00, room=2.6.1, ksp=2.0.21-1.0.28, kotlinxDatetime=0.6.1
│   │   ├── [libraries] kotlin-test, coroutines, compose-*, room-runtime, room-ktx, room-compiler, kotlinx-datetime
│   │   └── [plugins]   androidApp, androidLib, kotlinMultiplatform, compose, ksp
│   └── wrapper/
│       ├── gradle-wrapper.jar                     # Бинарный файл wrapper (58 KB)
│       └── gradle-wrapper.properties              # distributionUrl=gradle-8.7-bin.zip
│
├── shared/                                        # KMP shared-модуль
│   ├── build.gradle.kts                           # multiplatform + androidLibrary
│   │   ├── KMP targets: androidTarget, iosX64, iosArm64, iosSimulatorArm64
│   │   ├── plugins: kotlinMultiplatform, androidLibrary, ksp
│   │   ├── dependencies: coroutines-core, kotlinx-datetime, room-runtime, room-ktx
│   │   ├── kspCommonMainMetadata: room-compiler
│   │   └── iosTarget: framework baseName="Shared", isStatic=true
│   └── src/
│       ├── commonMain/kotlin/com/primalapp/
│       │   ├── model/
│       │   │   ├── Monster.kt                     # data class (9 полей + computed)
│       │   │   ├── Hunter.kt                      # data class (4 поля + computed)
│       │   │   ├── campaign/                      # Доменные модели кампании
│       │   │   │   ├── Campaign.kt, CampaignHunter.kt, SkillNode.kt
│       │   │   │   ├── Achievement.kt, Trophy.kt, Quest.kt
│       │   │   │   ├── ResourceEntry.kt, ResourceType.kt
│       │   │   │   ├── HunterClass.kt, SkillBranch.kt
│       │   │   │   ├── Material.kt, Plant.kt, Element.kt
│       │   │   └── ext/
│       │   │       ├── MonsterExt.kt              # takeDamage, rage, hardened, фазы, DamageResult
│       │   │       └── HunterExt.kt               # takeDamage, heal, revive, isCritical
│       │   ├── domain/
│       │   │   ├── ChapterProgression.kt + Impl   # Продвижение глав
│       │   │   ├── ResourceExchangeValidator.kt + Impl  # Валидация обмена
│       │   │   ├── SkillValidator.kt + Impl       # Валидация навыков
│       │   │   └── ExchangeResult.kt              # Valid/Invalid sealed class
│       │   ├── repository/
│       │   │   └── CampaignRepository.kt          # Интерфейс репозитория
│       │   ├── database/                           # Room KMP — слой данных
│       │   │   ├── Platform.kt                    # expect: PlatformContext, currentTimeMillis, createPrimalDatabase
│       │   │   ├── PrimalDatabase.kt              # @Database (7 таблиц)
│       │   │   ├── CampaignRepositoryImpl.kt      # Реализация CampaignRepository
│       │   │   ├── entity/ (7 файлов)              # Room @Entity
│       │   │   ├── dao/ (7 файлов)                 # Room @Dao
│       │   │   └── mapper/CampaignMapper.kt        # Entity ↔ Domain
│       │   └── viewmodel/
│       │       ├── BattleViewModel.kt             # MVVM: FightPhase, BattleScreenState, методы боя
│       │       └── CampaignViewModel.kt           # MVVM: AppScreen, CampaignUiState, управление кампаниями
│       ├── commonTest/kotlin/com/primalapp/model/
│       │   ├── MonsterTest.kt                     # 12 unit-тестов
│       │   └── HunterTest.kt                      # 10 unit-тестов
│       ├── androidMain/kotlin/com/primalapp/database/
│       │   └── Platform.android.kt                # actual: Context typealias + Room.databaseBuilder
│       └── iosMain/kotlin/com/primalapp/database/
│           └── Platform.ios.kt                    # actual: NSFileManager путь + Room.databaseBuilder
│
├── androidApp/                                    # Android приложение
│   ├── build.gradle.kts                           # androidApplication + composeCompiler
│   │   ├── namespace=com.primalapp.android
│   │   ├── compileSdk=34, minSdk=26, targetSdk=34
│   │   ├── compose=true, jvmTarget=17
│   │   └── depends on :shared (Room KMP — транзитивно)
│   └── src/main/
│       ├── AndroidManifest.xml                    # MainActivity как LAUNCHER
│       ├── res/values/
│       │   ├── strings.xml                        # app_name = "Primal App"
│       │   └── themes.xml                         # Material Light NoActionBar
│       └── kotlin/com/primalapp/android/
│           ├── MainActivity.kt                    # Compose UI + БД-фабрика через shared
│           └── ui/
│               ├── MainMenuScreen.kt              # Главное меню
│               ├── CampaignSetupScreen.kt         # Создание кампании
│               ├── CampaignListScreen.kt          # Список кампаний
│               ├── CampaignSheetScreen.kt         # Лист кампании
│               ├── BattleScreen.kt                # Боевой экран
│               ├── ExchangeDialog.kt              # Диалог обмена ресурсов
│               ├── PostVictoryDialog.kt           # Пост-победный диалог
│               └── PhaseChangeDialog.kt           # Диалог смены стойки
│
├── iosApp/iosApp/                                 # iOS приложение (заглушка)
│   ├── iOSApp.swift                               # @main App struct
│   ├── ContentView.swift                          # Начальный SwiftUI View
│   └── Info.plist                                 # iOS-конфигурация
│
└── doc/                                           # Документация
    ├── behavior.md                                # Описание поведения (со схемой состояний)
    ├── setup.md                                   # Инструкция по запуску (этот файл)
    ├── qa.md                                      # Вопросы и ответы
    ├── architecture.md                            # Архитектура, слои, зависимости
    └── api.md                                     # API-справочник (классы + методы)
```
