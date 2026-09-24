from enum import Enum

import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage
from pages.battle_preparation import BattlePreparation
from pages.battle_page import BattleSource


class Hunter(Enum):
    DAREON = "Дареон"
    MIRA = "Мира"
    TOREG = "Торег"
    LIONAR = "Льонар"
    KARA = "Кара"
    HELEREN = "Хелерен"
    DRUSK = "Друск"
    ZARAIA = "Зарайа"


class CreateCampaignPage(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Новая кампания"]')
    NAME_INPUT = (AppiumBy.XPATH, '//android.widget.EditText[.//android.widget.TextView[@text="Название кампании"]]')
    START_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Начать кампанию"]')
    BACK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Назад"]')
    ERROR_EMPTY_NAME = "Введите название кампании"
    ERROR_NO_HUNTERS = "Выберите хотя бы один класс"

    @staticmethod
    def _hunter_checkbox(hunter: Hunter) -> tuple[str, str]:
        return (AppiumBy.XPATH, f'//android.widget.CheckBox[following-sibling::*[1][self::android.widget.TextView[@text="{hunter.value}"]]]')

    @staticmethod
    def _hunter_name_field(hunter: Hunter) -> tuple[str, str]:
        return (AppiumBy.XPATH, f'//android.widget.TextView[@text="{hunter.value}"]/following-sibling::android.widget.EditText')

    @allure.step("Проверить наличие заголовка «Новая кампания»")
    def is_title_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Проверить отображение ошибки: {text}")
    def is_error_displayed(self, text: str) -> bool:
        return self.is_element_visible((AppiumBy.XPATH, f'//android.widget.TextView[@text="{text}"]'))

    @allure.step("Ввести название кампании: {name}")
    def set_campaign_name(self, name: str) -> "CreateCampaignPage":
        self.type_text(self.NAME_INPUT, name)
        return self

    @allure.step("Получить название кампании")
    def get_campaign_name(self) -> str:
        return self.get_text(self.NAME_INPUT)

    @allure.step("Выбрать охотника")
    def select_hunter(self, hunter: Hunter, custom_name: str | None = None) -> "CreateCampaignPage":
        self.scroll_to_text(hunter.value)
        self.click(self._hunter_checkbox(hunter))
        if custom_name:
            self.type_text(self._hunter_name_field(hunter), custom_name)
        return self

    @allure.step("Проверить, что охотник выбран")
    def is_hunter_selected(self, hunter: Hunter) -> bool:
        self.scroll_to_text(hunter.value)
        checkbox = self.wait.until(EC.presence_of_element_located(self._hunter_checkbox(hunter)))
        return checkbox.get_attribute("checked") == "true"

    @allure.step("Получить имя игрока охотника")
    def get_hunter_custom_name(self, hunter: Hunter) -> str:
        return self.get_text(self._hunter_name_field(hunter)).strip()

    @allure.step("Нажать кнопку «Начать кампанию»")
    def start_campaign(self) -> BattlePreparation:
        self.click(self.START_BUTTON)
        return BattlePreparation(self.driver, source=BattleSource.CAMPAIGN)

    @allure.step("Нажать кнопку «Назад»")
    def go_back(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.BACK_BUTTON)
        return MainPage(self.driver)