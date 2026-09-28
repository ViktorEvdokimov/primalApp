import re

import allure

from pages.base_page import BasePage
from pages.battle_page import BattlePage


class ExpeditionPage(BasePage):
    """«Подготовка к бою» экспедиции (аналог BattlePreparation из test/pages)."""

    path = "/expedition/new"
    root_test_id = "page-expedition-new"

    BOSS = "expedition-boss"
    DIFFICULTY = "expedition-difficulty"
    DECK = "expedition-deck"
    HUNTERS = "expedition-hunters"
    TOUGHNESS = "expedition-toughness"
    MODE = "expedition-mode"
    AT_HEALTH = "expedition-at-health"
    START = "expedition-start"
    REPLACE_CONFIRM = "expedition-replace-confirm"

    MANUAL = "Ввести данные вручную"

    @allure.step("Выбрать босса «{name}»")
    def select_boss(self, name: str) -> None:
        self.by_test_id(self.BOSS).select_option(label=name)

    @allure.step("Выбрать сложность {level}")
    def set_difficulty(self, level: str) -> None:
        self.by_test_id(self.DIFFICULTY).get_by_text(level, exact=True).click()

    def is_difficulty_enabled(self, level: str) -> bool:
        return self.by_test_id(self.DIFFICULTY).get_by_role("radio", name=level, exact=True).is_enabled()

    @allure.step("Указать число охотников: {count}")
    def set_hunters(self, count: str) -> None:
        self.by_test_id(self.HUNTERS).fill(count)

    def get_hunters(self) -> str:
        return self.by_test_id(self.HUNTERS).input_value()

    @allure.step("Указать урон для раны на игрока: «{value}»")
    def set_toughness(self, value: str) -> None:
        self.by_test_id(self.TOUGHNESS).fill(value)

    def get_toughness(self) -> str:
        return self.by_test_id(self.TOUGHNESS).input_value()

    @allure.step("Указать смену стойки: «{value}» (пусто — по запросу)")
    def set_stance_change(self, value: str) -> None:
        """Как поле app «Здоровье для смены стойки (пусто = по запросу)»."""
        if value == "":
            self.by_test_id(self.MODE).get_by_text("По запросу", exact=True).click()
            return
        self.by_test_id(self.MODE).get_by_text("По здоровью", exact=True).click()
        self.by_test_id(self.AT_HEALTH).fill(value)

    def get_stance_change(self) -> str:
        return self.by_test_id(self.AT_HEALTH).input_value()

    def get_reaction_deck_level(self) -> int | None:
        alt = self.by_test_id(self.DECK).get_attribute("alt") or ""
        match = re.search(r"уровень (\d)", alt)
        return int(match.group(1)) if match else None

    @allure.step("Нажать «Начать бой»")
    def start_battle(self) -> BattlePage:
        self.click(self.START)
        battle = BattlePage(self.page)
        battle.should_be_open()
        return battle

    @allure.step("Нажать «Начать бой» с ошибкой в полях")
    def click_start_expecting_error(self) -> None:
        self.click(self.START)
