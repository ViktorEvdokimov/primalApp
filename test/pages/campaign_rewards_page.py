import re

import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage


def _button(text: str) -> tuple[str, str]:
    return (AppiumBy.XPATH, f'//android.widget.TextView[@text="{text}"]/parent::android.view.View')


def _parse_resources(items, names: list[str]) -> dict[str, int]:
    """items — элементы или строки вида «Кости: 2»."""
    result = {}
    for item in items:
        text = item if isinstance(item, str) else item.text
        match = re.match(r"(\S+):\s*(\d+)$", text.strip())
        if match and match.group(1) in names:
            result[match.group(1)] = int(match.group(2))
    return result


class _ScrollableDialog(BasePage):
    """Окно наград с прокручиваемым содержимым: тексты собираются по всей высоте окна."""
    TITLE: tuple[str, str]
    SCROLL_AREA = (AppiumBy.CLASS_NAME, "android.widget.ScrollView")
    CONTENT_TEXTS = (AppiumBy.XPATH, '//android.widget.ScrollView//android.widget.TextView')

    def _visible_texts(self) -> list[str]:
        return [el.text.strip() for el in self.driver.find_elements(*self.CONTENT_TEXTS) if el.text.strip()]

    def _scroll(self, direction: str) -> None:
        areas = self.driver.find_elements(*self.SCROLL_AREA)
        if areas:
            self.driver.execute_script("mobile: scrollGesture", {"elementId": areas[0].id, "direction": direction, "percent": 0.8})

    def _collect_texts(self) -> list[str]:
        """Все тексты окна по порядку: содержимое прокручивается, а узлы за пределами области не видны в дереве."""
        self.wait.until(EC.visibility_of_element_located(self.TITLE))
        for _ in range(10):
            before = self._visible_texts()
            self._scroll("up")
            if self._visible_texts() == before:
                break
        collected = self._visible_texts()
        for _ in range(10):
            self._scroll("down")
            page = self._visible_texts()
            # Склеиваем по перекрытию: хвост собранного совпадает с началом новой страницы
            overlap = next(k for k in range(min(len(collected), len(page)), -1, -1) if collected[len(collected) - k:] == page[:k])
            if overlap == len(page):
                break
            collected += page[overlap:]
        return collected

    def _items_after(self, texts: list[str], header: str) -> list[str]:
        if header not in texts:
            return []
        items = []
        for text in texts[texts.index(header) + 1:]:
            if text.endswith(":") or text.startswith("Карты наград"):
                break
            items.append(text)
        return [] if items == ["—"] else items

    @allure.step("Получить условия раздела «{header}» (формулировка, результат)")
    def get_conditions(self, header: str = "Условные задания:") -> list[tuple[str, str]]:
        """Условное правило в формулировке правил и строка результата под ним."""
        items = self._items_after(self._collect_texts(), header)
        return list(zip(items[0::2], items[1::2]))


class CampaignRewardsPage(_ScrollableDialog):
    """Окно «Награды главы N»."""
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Награды главы")]')
    RESOURCE_ITEMS = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, ": ")]')
    OPENED_QUESTS = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Открываемые задания:")]')
    EXPIRED_QUESTS = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Истекающие задания:")]')
    ACCEPT_BUTTON = _button("Принять")
    DECLINE_BUTTON = _button("Отклонить")

    MATERIAL_NAMES = ["Кости", "Чешуя", "Кровь", "Зимия", "Иридия", "Златия"]
    PLANT_NAMES = ["Ниллея", "Тармарет", "Альбалацея", "Меллис", "Антемон", "Селикорния"]

    @allure.step("Проверить, что окно наград главы отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Получить заголовок окна")
    def get_title(self) -> str:
        return self.get_text(self.TITLE)

    @allure.step("Получить номер главы из заголовка")
    def get_chapter(self) -> int:
        return int(re.search(r"\d+", self.get_title()).group())

    @allure.step("Получить все материи и их количества")
    def get_materials(self) -> dict[str, int]:
        return _parse_resources(self.driver.find_elements(*self.RESOURCE_ITEMS), self.MATERIAL_NAMES)

    @allure.step("Получить все растения и их количества")
    def get_plants(self) -> dict[str, int]:
        return _parse_resources(self.driver.find_elements(*self.RESOURCE_ITEMS), self.PLANT_NAMES)

    @allure.step("Получить открываемые задания")
    def get_opened_quests(self) -> list[int]:
        text = self.get_text(self.OPENED_QUESTS).split(":", 1)[1]
        return [int(n) for n in re.findall(r"\d+", text)]

    @allure.step("Получить истекающие задания")
    def get_expired_quests(self) -> list[int]:
        if not self.is_element_visible_quick(self.EXPIRED_QUESTS):
            return []
        text = self.get_text(self.EXPIRED_QUESTS).split(":", 1)[1]
        return [int(n) for n in re.findall(r"\d+", text)]

    @allure.step("Проверить наличие сообщения главы: {text}")
    def has_message(self, text: str) -> bool:
        # Условные сообщения выводятся в разделе условий ниже — ищем по всему окну с прокруткой
        return any(text in item for item in self._collect_texts())

    @allure.step("Нажать кнопку «Принять»")
    def accept(self) -> "CampaignSheetPage":
        from pages.campaign_sheet_page import CampaignSheetPage
        self.click(self.ACCEPT_BUTTON)
        self.wait.until(EC.invisibility_of_element_located(self.TITLE))
        return CampaignSheetPage(self.driver)

    @allure.step("Нажать кнопку «Отклонить»")
    def decline(self) -> "CampaignSheetPage":
        from pages.campaign_sheet_page import CampaignSheetPage
        self.click(self.DECLINE_BUTTON)
        self.wait.until(EC.invisibility_of_element_located(self.TITLE))
        return CampaignSheetPage(self.driver)


    class QuestsEditorDialog(BasePage):
        TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Редактирование заданий"]')
        SUBTITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отметьте номера открытых заданий:"]')
        SAVE_BUTTON = _button("Сохранить")
        CANCEL_BUTTON = _button("Отмена")

        @staticmethod
        def _checkbox(quest_number: int) -> tuple[str, str]:
            # Только прямая ось: в UiAutomator2 preceding-sibling::X[1] возвращает первый по документу, а не ближайший
            return (AppiumBy.XPATH, f'//android.widget.CheckBox[following-sibling::*[1][self::android.widget.TextView][@text="{quest_number}"]]')

        @allure.step("Проверить, что диалог редактирования заданий открыт")
        def is_displayed(self) -> bool:
            return self.is_element_visible(self.TITLE)

        @allure.step("Установить статус задания {quest_number}: {completed}")
        def set_quest_completed(self, quest_number: int, completed: bool = True) -> "CampaignRewardsPage.QuestsEditorDialog":
            if self.is_quest_completed(quest_number) != completed:
                self.click(self._checkbox(quest_number))
                self.wait.until(lambda _: self.is_quest_completed(quest_number) == completed)
            return self

        @allure.step("Проверить, можно ли изменить задание {quest_number}")
        def is_quest_editable(self, quest_number: int) -> bool:
            chk = self.wait.until(EC.presence_of_element_located(self._checkbox(quest_number)))
            return chk.get_attribute("enabled") == "true"

        @allure.step("Получить статус задания {quest_number}")
        def is_quest_completed(self, quest_number: int) -> bool:
            chk = self.wait.until(EC.presence_of_element_located(self._checkbox(quest_number)))
            return chk.get_attribute("checked") == "true"

        @allure.step("Получить номера отмеченных заданий")
        def get_completed_quests(self) -> list[int]:
            self.wait.until(EC.visibility_of_element_located(self.TITLE))
            numbers = self.driver.find_elements(AppiumBy.XPATH,
                '//android.widget.CheckBox[@checked="true"]/following-sibling::android.widget.TextView[1]')
            return sorted(int(el.text.strip()) for el in numbers if el.text.strip().isdigit())

        @allure.step("Нажать «Сохранить»")
        def save(self) -> "CampaignSheetPage":
            from pages.campaign_sheet_page import CampaignSheetPage
            self.click(self.SAVE_BUTTON)
            self.wait.until(EC.invisibility_of_element_located(self.TITLE))
            return CampaignSheetPage(self.driver)

        @allure.step("Нажать «Отмена»")
        def cancel(self) -> "CampaignSheetPage":
            from pages.campaign_sheet_page import CampaignSheetPage
            self.click(self.CANCEL_BUTTON)
            self.wait.until(EC.invisibility_of_element_located(self.TITLE))
            return CampaignSheetPage(self.driver)


    class AchievementsEditorDialog(BasePage):
        TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Редактирование достижений"]')
        NAME_INPUT = (AppiumBy.XPATH, '//android.widget.EditText[.//android.widget.TextView[@text="Название достижения"]]')
        ADD_BUTTON = _button("Добавить")
        DONE_BUTTON = _button("Готово")
        CANCEL_BUTTON = _button("Отмена")

        @allure.step("Проверить, что диалог редактирования достижений открыт")
        def is_displayed(self) -> bool:
            return self.is_element_visible(self.TITLE)

        @allure.step("Добавить достижение: {name}")
        def add_achievement(self, name: str) -> "CampaignRewardsPage.AchievementsEditorDialog":
            self.type_text(self.NAME_INPUT, name)
            self.click(self.ADD_BUTTON)
            self.wait.until(lambda _: name in self.get_achievements())
            return self

        @allure.step("Удалить достижение: {name}")
        def remove_achievement(self, name: str) -> "CampaignRewardsPage.AchievementsEditorDialog":
            self.click((AppiumBy.XPATH, f'//android.widget.TextView[@text="{name}"]/following-sibling::android.view.View[1]'))
            self.wait.until(lambda _: name not in self.get_achievements())
            return self

        @allure.step("Получить список достижений в редакторе")
        def get_achievements(self) -> list[str]:
            items = self.driver.find_elements(AppiumBy.XPATH,
                '//android.widget.TextView[following-sibling::android.view.View[1]/android.widget.TextView[@text="Удалить"]]')
            return [el.text.strip() for el in items]

        @allure.step("Нажать «Готово»")
        def done(self) -> "CampaignSheetPage":
            from pages.campaign_sheet_page import CampaignSheetPage
            self.click(self.DONE_BUTTON)
            self.wait.until(EC.invisibility_of_element_located(self.TITLE))
            return CampaignSheetPage(self.driver)

        @allure.step("Нажать «Отмена»")
        def cancel(self) -> "CampaignSheetPage":
            from pages.campaign_sheet_page import CampaignSheetPage
            self.click(self.CANCEL_BUTTON)
            self.wait.until(EC.invisibility_of_element_located(self.TITLE))
            return CampaignSheetPage(self.driver)


class QuestRewardsPage(_ScrollableDialog):
    """Окно «Награды за задание» / «Награды за поражение»."""
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Награды за задание" or @text="Награды за поражение"]')
    QUEST_TITLE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Задание ")]')
    ACCEPT_BUTTON = _button("Принять")
    EXIT_BUTTON = _button("Выход")

    @allure.step("Проверить, что окно наград за задание отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Получить заголовок задания")
    def get_quest_title(self) -> str:
        return self.get_text(self.QUEST_TITLE)

    @allure.step("Получить материи")
    def get_materials(self) -> dict[str, int]:
        items = self._items_after(self._collect_texts(), "Материи:")
        return _parse_resources(items, CampaignRewardsPage.MATERIAL_NAMES)

    @allure.step("Получить растения")
    def get_plants(self) -> dict[str, int]:
        items = self._items_after(self._collect_texts(), "Растения:")
        return _parse_resources(items, CampaignRewardsPage.PLANT_NAMES)

    @allure.step("Получить открываемые задания")
    def get_opened_quests(self) -> list[int]:
        items = self._items_after(self._collect_texts(), "Открываемые задания:")
        return [int(n) for item in items for n in re.findall(r"\d+", item)]

    @allure.step("Получить достижения")
    def get_achievements(self) -> list[str]:
        return self._items_after(self._collect_texts(), "Достижения:")

    @allure.step("Принять награды за задание")
    def accept(self) -> CampaignRewardsPage:
        self.click(self.ACCEPT_BUTTON)
        page = CampaignRewardsPage(self.driver)
        assert page.is_displayed(), "После наград за задание не открылось окно наград главы"
        return page
