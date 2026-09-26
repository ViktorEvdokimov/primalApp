import re

import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common import NoSuchElementException, TimeoutException
from selenium.webdriver.support.ui import WebDriverWait
from pages.quest_selection_page import QuestSelectionPage
from pages.main_page import MainPage

from pages.base_page import BasePage


def _button(text: str) -> tuple[str, str]:
    return (AppiumBy.XPATH, f'//android.widget.TextView[@text="{text}"]/parent::android.view.View')


class CampaignSheetPage(BasePage):
    CHAPTER_LABEL = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Глава ")]')
    CAMPAIGN_NAME = (AppiumBy.XPATH, '//android.widget.TextView[following-sibling::*[1][self::android.widget.TextView][starts-with(@text, "Глава ")]]')
    FORGE_LABEL = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Кузня ")]')
    LAB_LABEL = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Лаб ")]')
    CHAPTER_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Глава:"]')
    CHAPTER_VALUE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Глава:"]/following-sibling::android.widget.TextView[1]')
    CHAPTER_DEC = (AppiumBy.XPATH, '//android.widget.TextView[@text="Глава:"]/following-sibling::android.view.View[1]')
    CHAPTER_INC = (AppiumBy.XPATH, '//android.widget.TextView[@text="Глава:"]/following-sibling::android.view.View[2]')
    HUNTERS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Охотники:"]')
    HUNTER_BUTTONS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Охотники:"]/following-sibling::android.view.View/android.widget.TextView[contains(@text, "(")]')
    SKILLS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Древо навыков:"]')
    RESOURCES_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ресурсы:"]')
    MATERIALS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Материи:"]')
    PLANTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Растения:"]')
    ELEMENTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Стихии:"]')
    QUESTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Задания:"]')
    OPENED_QUESTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Открытые:"]')
    # Открытые — до «Выполненные:» (если он есть), выполненные — между «Выполненные:» и «Достижения:» (D-5)
    OPENED_QUEST_ITEMS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Открытые:"]/following-sibling::android.widget.TextView[following-sibling::android.widget.TextView[@text="Достижения:"]][not(preceding-sibling::android.widget.TextView[@text="Выполненные:"])]')
    COMPLETED_QUEST_ITEMS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выполненные:"]/following-sibling::android.widget.TextView[following-sibling::android.widget.TextView[@text="Достижения:"]]')
    EDIT_QUESTS_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Открытые:"]/following-sibling::android.view.View[1]')
    NO_OPENED_QUESTS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нет открытых заданий."]')
    ACHIEVEMENTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Достижения:"]')
    EARNED_ACHIEVEMENTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Полученные:"]')
    EDIT_ACHIEVEMENTS_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Полученные:"]/following-sibling::android.view.View[1]')
    NO_ACHIEVEMENTS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нет достижений."]')
    EARNED_ACHIEVEMENTS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Полученные:"]/following-sibling::android.widget.TextView[following-sibling::android.widget.TextView[@text="Поверженные боссы:"]]')
    SKILL_TREE_ITEMS = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Ветвь ")] | //android.view.View/android.widget.TextView[string-length(@text) = 2]')
    DEFEATED_BOSSES_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Поверженные боссы:"]')
    DEFEATED_BOSSES = (AppiumBy.XPATH, '//android.widget.TextView[@text="Поверженные боссы:"]/following-sibling::android.widget.TextView[following-sibling::android.widget.TextView[@text="Стихии поверженных боссов:"]]')
    DEFEATED_BOSSES_ELEMENTS_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Стихии поверженных боссов:"]')
    DEFEATED_BOSSES_ELEMENTS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Стихии поверженных боссов:"]/following-sibling::android.widget.TextView[1]')
    NOTES_HEADER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Заметки:"]')
    NOTES_INPUT = (AppiumBy.XPATH, '//android.widget.TextView[@text="Заметки:"]/following-sibling::android.widget.EditText[1]')
    SAVE_NOTES_BUTTON = _button("Сохранить заметки")
    START_BATTLE = _button("Начать бой")
    GO_TO_MAIN_MENU = _button("В главное меню")

    # Тексты, которые есть только на листе (не в диалогах наград и не на экране подготовки к бою)
    ANY_SECTION = (AppiumBy.XPATH, '//android.widget.TextView[@text="Глава:" or @text="Охотники:" or @text="Задания:" '
                                   'or @text="Полученные:" or @text="Заметки:" or @text="Сохранить заметки" '
                                   'or @text="В главное меню"]')

    MATERIAL_NAMES = ["Чешуя", "Кости", "Кровь", "Зимия", "Иридия", "Златия"]
    PLANT_NAMES = ["Ниллея", "Тармарет", "Альбалацея", "Меллис", "Антемон", "Селикорния"]
    ELEMENT_NAMES = ["Огонь", "Рог", "Коралл", "Кристалл", "Молния", "Металл", "Перо", "Яд", "Лёд"]

    @staticmethod
    def _resource_label(name: str) -> tuple[str, str]:
        return (AppiumBy.XPATH, f'//android.widget.TextView[starts-with(@text, "{name}: ")]')

    @allure.step("Проверить, что лист кампании отображается")
    def is_displayed(self) -> bool:
        # Лист может быть прокручен в любое место — ждём любую из опорных секций
        return self.is_element_visible(self.ANY_SECTION)

    @allure.step("Получить название кампании")
    def get_campaign_name(self) -> str:
        self.scroll_into_view("Глава:")
        return self.get_text(self.CAMPAIGN_NAME)

    @allure.step("Получить номер текущей главы")
    def get_chapter(self) -> int:
        self.scroll_into_view("Глава:")
        return int(self.get_text(self.CHAPTER_VALUE).strip())

    @allure.step("Увеличить главу на 1")
    def chapter_increment(self) -> "CampaignSheetPage":
        self.scroll_into_view("Глава:")
        expected = self.get_chapter() + 1
        self.click(self.CHAPTER_INC)
        self.wait.until(lambda _: self.get_chapter() == expected)
        return self

    @allure.step("Уменьшить главу на 1")
    def chapter_decrement(self) -> "CampaignSheetPage":
        self.scroll_into_view("Глава:")
        before = self.get_chapter()
        self.click(self.CHAPTER_DEC)
        self.wait.until(lambda _: self.get_chapter() != before)
        return self

    @allure.step("Установить главу: {chapter}")
    def set_chapter(self, chapter: int) -> "CampaignSheetPage":
        while self.get_chapter() < chapter:
            self.chapter_increment()
        while self.get_chapter() > chapter:
            self.chapter_decrement()
        return self

    @allure.step("Получить уровень кузницы")
    def get_forge_level(self) -> int:
        self.scroll_into_view("Глава:")
        return int(re.search(r"\d+", self.get_text(self.FORGE_LABEL)).group())

    @allure.step("Получить уровень лаборатории")
    def get_lab_level(self) -> int:
        self.scroll_into_view("Глава:")
        return int(re.search(r"\d+", self.get_text(self.LAB_LABEL)).group())

    @allure.step("Получить список охотников (имя игрока, класс)")
    def get_hunters(self) -> list[tuple[str, str]]:
        self.scroll_into_view("Охотники:")
        hunters = []
        for el in self.driver.find_elements(*self.HUNTER_BUTTONS):
            match = re.match(r"(.+)\n\((.+)\)", el.text.strip())
            if match:
                hunters.append((match.group(1), match.group(2)))
        return hunters

    @allure.step("Проскроллить вниз")
    def scroll_down(self) -> "CampaignSheetPage":
        scroll_view = self.driver.find_element(AppiumBy.CLASS_NAME, "android.widget.ScrollView")
        self.driver.execute_script("mobile: scrollGesture", {"elementId": scroll_view.id, "direction": "down", "percent": 0.6})
        return self

    @allure.step("Проскроллить вверх")
    def scroll_up(self) -> "CampaignSheetPage":
        scroll_view = self.driver.find_element(AppiumBy.CLASS_NAME, "android.widget.ScrollView")
        self.driver.execute_script("mobile: scrollGesture", {"elementId": scroll_view.id, "direction": "up", "percent": 0.6})
        return self

    @allure.step("Получить значение ресурса: {name}")
    def get_resource_value(self, name: str) -> int:
        self.scroll_into_view(f"{name}: ", partial=True)
        text = self.get_text(self._resource_label(name))
        return int(re.search(r":\s*(\d+)", text).group(1))

    def _change_resource(self, name: str, delta: int) -> None:
        before = self.get_resource_value(name)
        expected = max(before + delta, 0)
        label = self._resource_label(name)
        button_index = 2 if delta > 0 else 1
        self.click((label[0], f'{label[1]}/following-sibling::android.view.View[{button_index}]'))
        if expected != before:
            self.wait.until(lambda _: self.get_resource_value(name) == expected)

    @allure.step("Нажать + для ресурса: {name}")
    def resource_increment(self, name: str) -> "CampaignSheetPage":
        self._change_resource(name, 1)
        return self

    @allure.step("Нажать - для ресурса: {name}")
    def resource_decrement(self, name: str) -> "CampaignSheetPage":
        self._change_resource(name, -1)
        return self

    @allure.step("Получить все материи")
    def get_all_materials(self) -> dict[str, int]:
        return {name: self.get_resource_value(name) for name in self.MATERIAL_NAMES}

    @allure.step("Получить все растения")
    def get_all_plants(self) -> dict[str, int]:
        return {name: self.get_resource_value(name) for name in self.PLANT_NAMES}

    @allure.step("Получить все стихии")
    def get_all_elements(self) -> dict[str, int]:
        return {name: self.get_resource_value(name) for name in self.ELEMENT_NAMES}

    @allure.step("Проверить, что заголовок «Стихии:» отображается")
    def is_elements_section_visible(self) -> bool:
        self.scroll_into_view("Стихии:")
        return self.is_element_visible(self.ELEMENTS_HEADER)

    @allure.step("Получить древо навыков")
    def get_skill_tree(self) -> dict[str, list[str]]:
        self.scroll_into_view("Ветвь Д")
        tree: dict[str, list[str]] = {}
        branch = None
        # Ветви и кнопки навыков — соседи в одном плоском списке; относительный XPath от элемента
        # в UiAutomator2 видит только потомков, поэтому разбираем общий список в порядке документа
        for el in self.driver.find_elements(*self.SKILL_TREE_ITEMS):
            text = el.text.strip()
            if text.startswith("Ветвь "):
                branch = text.removeprefix("Ветвь ").strip()
                tree[branch] = []
            elif branch and re.fullmatch(r"[А-Д][12]", text):
                tree[branch].append(text)
        return tree

    @allure.step("Нажать навык: {skill}")
    def click_skill(self, skill: str) -> "CampaignSheetPage":
        self.scroll_into_view(skill)
        self.click(_button(skill))
        return self

    @allure.step("Проверить, открыт ли навык: {skill}")
    def is_skill_unlocked(self, skill: str) -> bool:
        self.scroll_into_view(skill)
        # Состояние навыка передаётся семантикой selected (задача 42.2): цвет кнопки UiAutomator2 не видит.
        # Для кнопок Compose отображает selected в атрибут checked (selected — только у вкладок)
        button = self.driver.find_element(*_button(skill))
        return "true" in (button.get_attribute("checked"), button.get_attribute("selected"))

    @allure.step("Дождаться состояния навыка {skill}: открыт = {unlocked}")
    def wait_skill_state(self, skill: str, unlocked: bool, timeout: float = 5) -> bool:
        try:
            WebDriverWait(self.driver, timeout).until(lambda _: self.is_skill_unlocked(skill) == unlocked)
            return True
        except TimeoutException:
            return False

    @allure.step("Проверить, что секция заданий отображается")
    def is_quests_section_visible(self) -> bool:
        self.scroll_into_view("Задания:")
        return self.is_element_visible(self.QUESTS_HEADER)

    @allure.step("Получить номера открытых заданий")
    def get_opened_quests(self) -> list[int]:
        self.scroll_into_view("Достижения:")
        numbers = []
        for el in self.driver.find_elements(*self.OPENED_QUEST_ITEMS):
            match = re.match(r"(\d+)\.", el.text.strip())
            if match:
                numbers.append(int(match.group(1)))
        return numbers

    @allure.step("Получить номера выполненных заданий")
    def get_completed_quests(self) -> list[int]:
        self.scroll_into_view("Достижения:")
        numbers = []
        for el in self.driver.find_elements(*self.COMPLETED_QUEST_ITEMS):
            match = re.match(r"(\d+)\.", el.text.strip())
            if match:
                numbers.append(int(match.group(1)))
        return numbers

    @allure.step("Нажать «Выполнено» у задания {number}")
    def complete_quest(self, number: int) -> "CampaignSheetPage":
        label = f'//android.widget.TextView[starts-with(@text, "{number}. ")]'
        before = self.get_opened_quests()
        self.scroll_into_view(f"{number}. ", partial=True)
        self.click((AppiumBy.XPATH, f'{label}/following-sibling::android.view.View[1]'))
        self.wait.until(lambda _: self.get_opened_quests() != before)
        return self

    @allure.step("Нажать «Отмена» у выполненного задания {number}")
    def uncomplete_quest(self, number: int) -> "CampaignSheetPage":
        """Выполненное задание — в списке «Выполненные:»; кнопка «Отмена» рядом возвращает его в открытые."""
        label = f'//android.widget.TextView[@text="Выполненные:"]/following-sibling::android.widget.TextView[starts-with(@text, "{number}. ")]'
        before = self.get_completed_quests()
        self.scroll_into_view(f"{number}. ", partial=True)
        self.click((AppiumBy.XPATH, f'{label}/following-sibling::android.view.View[1]'))
        self.wait.until(lambda _: self.get_completed_quests() != before)
        return self

    @allure.step("Нажать «Редактировать» у заданий")
    def edit_quests(self) -> "CampaignRewardsPage.QuestsEditorDialog":
        from pages.campaign_rewards_page import CampaignRewardsPage
        self.scroll_into_view("Открытые:")
        self.click(self.EDIT_QUESTS_BUTTON)
        return CampaignRewardsPage.QuestsEditorDialog(self.driver)

    def _is_placeholder_visible(self, text: str, locator: tuple[str, str]) -> bool:
        # Прокрутка к заголовку секции может остановиться у нижнего края экрана, а строка-заглушка под ним
        # окажется за краем: узлов за пределами области прокрутки в дереве нет. Поэтому докручиваем до самой строки
        try:
            self.scroll_into_view(text)
        except NoSuchElementException:
            return False
        return self.is_element_visible_quick(locator, timeout=3)

    @allure.step("Проверить, что нет открытых заданий")
    def has_no_opened_quests(self) -> bool:
        return self._is_placeholder_visible("Нет открытых заданий.", self.NO_OPENED_QUESTS)

    @allure.step("Проверить, что секция достижений отображается")
    def is_achievements_section_visible(self) -> bool:
        self.scroll_into_view("Достижения:")
        return self.is_element_visible(self.ACHIEVEMENTS_HEADER)

    @allure.step("Получить полученные достижения")
    def get_achievements(self) -> list[str]:
        self.scroll_into_view("Поверженные боссы:")
        items = self.driver.find_elements(*self.EARNED_ACHIEVEMENTS)
        return [el.text.strip() for el in items if el.text.strip() and el.text.strip() != "Нет достижений."]

    @allure.step("Нажать «Редактировать» у достижений")
    def edit_achievements(self) -> "CampaignRewardsPage.AchievementsEditorDialog":
        from pages.campaign_rewards_page import CampaignRewardsPage
        self.scroll_into_view("Полученные:")
        self.click(self.EDIT_ACHIEVEMENTS_BUTTON)
        return CampaignRewardsPage.AchievementsEditorDialog(self.driver)

    @allure.step("Проверить, что нет достижений")
    def has_no_achievements(self) -> bool:
        return self._is_placeholder_visible("Нет достижений.", self.NO_ACHIEVEMENTS)

    @allure.step("Проверить, что секция поверженных боссов отображается")
    def is_defeated_bosses_section_visible(self) -> bool:
        self.scroll_into_view("Поверженные боссы:")
        return self.is_element_visible(self.DEFEATED_BOSSES_HEADER)

    @allure.step("Получить список поверженных боссов")
    def get_defeated_bosses(self) -> list[str]:
        self.scroll_into_view("Стихии поверженных боссов:")
        return [el.text.strip() for el in self.driver.find_elements(*self.DEFEATED_BOSSES) if el.text.strip()]

    @allure.step("Получить стихии поверженных боссов")
    def get_defeated_bosses_elements(self) -> list[str]:
        self.scroll_into_view("Стихии поверженных боссов:")
        text = self.get_text(self.DEFEATED_BOSSES_ELEMENTS).strip()
        return [] if text == "Нет." else [part.strip() for part in text.split(",")]

    @allure.step("Проверить, что секция заметок отображается")
    def is_notes_section_visible(self) -> bool:
        return self.is_element_visible(self.NOTES_HEADER)

    @allure.step("Получить текст заметок")
    def get_notes(self) -> str:
        self.scroll_into_view("Сохранить заметки")
        return self.get_text(self.NOTES_INPUT)

    @allure.step("Ввести заметки: {notes}")
    def set_notes(self, notes: str) -> "CampaignSheetPage":
        self.scroll_into_view("Сохранить заметки")
        self.type_text(self.NOTES_INPUT, notes)
        return self

    @allure.step("Сохранить заметки")
    def save_notes(self) -> "CampaignSheetPage":
        self.scroll_into_view("Сохранить заметки")
        self.click(self.SAVE_NOTES_BUTTON)
        return self

    @allure.step("Начать бой из листа кампании")
    def start_battle(self) -> "QuestSelectionPage":
        self.scroll_into_view("Начать бой")
        self.click(self.START_BATTLE)
        return QuestSelectionPage(self.driver)

    @allure.step("Начать финальный бой (после главы 11 задание не выбирается)")
    def start_final_battle(self) -> "BattlePreparation":
        from pages.battle_page import BattleSource
        from pages.battle_preparation import BattlePreparation
        self.scroll_into_view("Начать бой")
        self.click(self.START_BATTLE)
        prep = BattlePreparation(self.driver, source=BattleSource.CAMPAIGN)
        assert prep.is_displayed(), "Финальный бой должен начинаться без выбора задания"
        return prep

    @allure.step("Перейти в главное меню")
    def go_to_main_menu(self) -> "MainPage":
        self.scroll_into_view("В главное меню")
        self.click(self.GO_TO_MAIN_MENU)
        return MainPage(self.driver)
