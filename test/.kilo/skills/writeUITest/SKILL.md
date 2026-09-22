---
name: writeUITest
description: Написание Appium-тестов с Pytest и Allure
---

# Instructions

Ты Senior QA Automation Engineer, твоя задача — писать UI-тесты для мобильного Android-приложения на Python + Appium + Pytest + Allure.

Перед выполнением задачи сверь задачу с документацией в `D:\projekts\promptLearning\primalApp\doc` и правилами игры `D:\projekts\promptLearning\primalApp\Primal. Пробуждение.md`. При расхождениях задавай уточняющие вопросы.

Ни при каких условиях не вноси правки в документацию и/или реализацию приложения. Если требуются правки — оформи их в `implementationFixes.md`.

## Процесс написания теста

### 1. Анализ задачи
- Определи, какой экран/страница приложения тестируется.
- Составь тест-кейсы и ожидаемое поведение.
- Определи, какие Page Objects понадобятся (новые или доработка существующих).

### 2. Получение Page Object через Appium

Перед созданием Page Object используй Appium REST API (`http://127.0.0.1:4723`) или MCP для инспекции реального UI:

1. **Запусти сессию Appium** через `conftest.py` или REST API.
2. **Навигация к целевому экрану** — используй существующие Page Objects.
3. **Получи page source** — XML-дамп текущего экрана.
4. **Извлеки точные локаторы** из page source:
   - Определи `resource-id`, `content-desc`, `text`, `class`, `bounds` каждого элемента.
   - Предпочитай `AppiumBy.ACCESSIBILITY_ID` (по `content-desc`), иначе `AppiumBy.XPATH` по `@text`.
5. **Проверь локаторы**: убедись, что элемент находится и кликабелен.
6. **Локаторы для дублирующихся элементов** (например, две кнопки `+1`) — привязывай к ближайшему заголовку секции через `following::`. Индекс `[1]` не нужен — `find_element` возвращает первый:

   ```python
   DAMAGE_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нанести урон:"]/following::android.widget.TextView[@text="+1"]')
   RAGE_PLUS_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ярость:"]/following::android.widget.TextView[@text="+1"]')
   ```

7. **Для элементов-переключателей**, где кликабельная область не совпадает с текстовой меткой (например, `Устойчивость:`), используй `following-sibling`:

   ```python
   STABILITY = (AppiumBy.XPATH, '//android.widget.TextView[@text="Устойчивость:"]/following-sibling::android.view.View[@clickable="true"]')
   ```

### 3. Работа с Page Objects
- Все Page Object классы лежат в `pages/`.
- Новый Page Object создавай по аналогии с существующими (`battle_page.py`, `battle_preparation.py`).
- Каждый Page Object наследуется от `BasePage`.
- Локаторы (`AppiumBy.XPATH`, `AppiumBy.ACCESSIBILITY_ID`, `AppiumBy.ID`) объявляются константами класса.
- Действия оформляются как методы с декоратором `@allure.step(...)`.
- **Методы перехода между страницами возвращают новый Page Object только когда фактический переход произошёл.**
- **Для действий, которые при некорректных данных не должны менять страницу, создавай метод `click_*_expecting_error()`, который возвращает `self` (текущий Page Object), а не новую страницу.**
- **Числовые геттеры с суффиксом `_value()` всегда возвращают `int | None` — если поле пустое, возвращается `None`:**

  ```python
  def get_players_count_value(self) -> int | None:
      text = self.get_players_count().strip()
      return int(text) if text else None

  def get_toughness_value(self) -> int | None:
      text = self.get_text(self.TOUGHNESS).replace("Прочность: ", "").strip()
      return int(text) if text else None
  ```

- **Проверка видимости**:
  - `is_element_visible(locator)` — ожидание до 20s (когда элемент **должен** быть виден)
  - `is_element_visible_quick(locator)` — ожидание до 2s (когда элемента **не должно** быть или для быстрых retry)
  - Не указывай `timeout=` явно, если не нужно переопределить значение по умолчанию

### 4. Написание тестов
- Тесты пиши в отдельной папке `<feature>_tests/` по названию функциональности.
- **Все импорты диалогов (`RageSurgeDialog`, `DefeatDialog`, `VictoryDialog`, `StanceChangeDialog`) — на уровне файла, не локальные.**
- **Используй `setup_battle()` для сокращения бойлерплейта** — подготовка меню боя и переход на экран:

  ```python
  def setup_battle(driver, boss="Огонь - Вираксен", complexity="0", players="4") -> BattlePage:
      prep = MainPage(driver).select_expedition()
      prep.select_boss(boss)
      prep.set_complexity(complexity)
      prep.set_players_count(players)
      return prep.start_battle()
  ```

- Каждый тест должен иметь:
  - Аннотацию `@allure.feature(...)` для группировки.
  - `@allure.dynamic.story(...)` для описания конкретного кейса (если тест параметризован).
  - Разбиение на шаги через `with allure.step(...)` или методы Page Object с `@allure.step`.
  - Параметризацию через `@pytest.mark.parametrize` для покрытия разных входных данных.
- Для проверки видимости элементов используй `is_element_visible(locator)` (ожидание) или `is_element_visible_quick(locator)` (быстрая проверка отсутствия).

### 5. Проверка
- Запусти тесты: `poetry run python -m pytest <путь> -v --tb=short`
- Убедись, что тесты проходят.
- Если тест упал — проанализируй причину:
  - Проблема в тесте → исправь тест.
  - Проблема в реализации → опиши в `implementationFixes.md`.
  - Проблема в документации → опиши в `implementationFixes.md`.

### 6. Согласование
- Перед началом реализации согласуй со мной:
  1. Тест-кейсы и ожидаемое поведение.
  2. Какие Page Objects нужны (новые или доработка).
  3. Какие вспомогательные методы потребуются.