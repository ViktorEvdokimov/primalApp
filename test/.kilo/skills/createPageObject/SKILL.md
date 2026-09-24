---
name: createPageObject
description: Создание и доработка Page Object для мобильного Android-приложения через appium-mcp
---

# Skill: createPageObject

Senior QA Automation Engineer. Задача — создать новый Page Object или доработать существующий для Android-приложения на Python + Appium + Pytest + Allure.

## Ограничения проекта

- **Запрещено:** временные скрипты/файлы, REST-API вызовы (`curl`/HTTP к Appium). Только прямое подключение через **appium-mcp**.
- Перед работой сверься с документацией `D:\projekts\promptLearning\primalApp\doc` и правилами `Primal. Пробуждение.md`.
- Не вноси правки в документацию/реализацию приложения.

## Общие правила подключения к устройству

1. Подключайся через appium-mcp (stdio, `APPIUM_HOST=127.0.0.1`, `APPIUM_PORT=4723`).
2. Для **доработки существующего экрана** (когда состояние приложения важно) используй `noReset: true`, `forceAppLaunch: false`, `shouldTerminateApp: false` — экран не сбросится.
3. Для **чистого прохождения флоу** (навигация от главного меню) используй `noReset: true`, `forceAppLaunch: true`.
4. После инспекции удаляй созданную сессию Appium через `appium_session_management` (action: delete).
5. Локаторы бери **из фактического page source**, не из предположений.

## Сценарий 1: новый Page Object

### Шаги

1. **Определи целевой экран** по задаче и документации.
2. **Подключись** через appium-mcp и создай сессию.
3. **Перейди к целевому экрану**, проходя флоу с самого начала:
   — от главного меню через существующие Page Objects/координаты.
4. **Получи page source** (`appium_get_page_source`) и извлеки:
   — `text`, `content-desc`, `class`, `bounds`, состояние `checkable/clickable/checked`.
5. **Если экран прокручиваемый** — проскролль (`appium_gesture` action: scroll) и сними page source дополнительно, чтобы собрать все секции.
6. **Создай класс** в `pages/<screen>_page.py`:
   - наследуется от `BasePage`;
   - локаторы — константы класса (tuple `(AppiumBy.XPATH, "…")`);
   - действия — методы с `@allure.step(...)`;
   - методы перехода возвращают **новый Page Object** только при реальном переходе;
   - для дублирующихся элементов привязывай локатор к заголовку секции через `following::` / `following-sibling::`.
7. **Привяжи переходы**: обнови методы на предыдущих страницах, чтобы они возвращали новый Page Object.

### Пример класса

```python
class MyScreenPage(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Заголовок"]')
    ACTION = (AppiumBy.XPATH, '//android.view.View[.//android.widget.TextView[@text="Действие"]]')

    @allure.step("Проверить отображение экрана")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Нажать действие")
    def do_action(self) -> "NextPage":
        from pages.next_page import NextPage
        self.click(self.ACTION)
        return NextPage(self.driver)
```

## Сценарий 2: доработка существующего Page Object

### Ключевое правило

Зная структуру окон приложения, **сбрось состояние приложения и перейди к искомому меню от главной страницы** через существующие Page Objects. Не опирайся на текущее состояние экрана из прошлой сессии.

### Шаги

1. **Сбрось состояние**: создай сессию с `forceAppLaunch: true` (приложение перезапустится с `MainPage`).
2. **Пройди флоу от главной страницы** к целевому экрану, используя уже созданные Page Objects:
   `MainPage → BattlePreparation → BattlePage → VictoryDialog → CampaignRewardsPage → CampaignSheetPage` и т.д.
3. **Получи page source** и сверь локаторы с существующим классом:
   - совпадает текст/структура → локатор верный;
   - изменился текст/структура → обнови локатор.
4. **Проверь новые элементы**: тапай по кнопкам («Редактировать», «+50», «Продолжить» и т.п.), получай page source дочерних окон/диалогов.
5. **Дополни Page Object** новыми секциями и методами (заголовки, кнопки, списки, чекбоксы, поля ввода).
6. **Убедись в отсутствии скрытых секций** — если экран прокручиваемый, проскролль до конца и зафиксируй все нижние элементы.

### Цепочки переходов (для навигации)

```
Главное меню
  ├─ Режим экспедиции → BattlePreparation → BattlePage → VictoryDialog/DefeatDialog
  └─ Режим кампании → CampaignListPage (если есть кампании) | CreateCampaignPage (если нет)
        → BattlePreparation → BattlePage → CampaignVictoryDialog
             └─ Продолжить → CampaignRewardsPage | CampaignSheetPage
        └─ (в листе) открыть кампанию → CampaignSheetPage
             └─ Начать бой → QuestSelectionPage → BattlePage
```

## Чек-лист завершения

- [ ] Локаторы взяты из фактического page source
- [ ] Все секции экрана покрыты (учтён скролл)
- [ ] Методы перехода возвращают корректные объекты
- [ ] `poetry run python -c "import pages.<имя>"` проходит без ошибок
- [ ] Временные файлы/скрипты удалены