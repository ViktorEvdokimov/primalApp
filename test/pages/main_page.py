import allure
from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage
from pages.battle_preparation import BattlePreparation


class MainPage(BasePage):
    EXPEDITION = (AppiumBy.XPATH, '//android.widget.TextView[@text="Режим экспедиции"]')
    CAMPAIGN = (AppiumBy.XPATH, '//android.widget.TextView[@text="Режим кампании"]')


    @allure.step("Переход в меню экспедиции")
    def select_expedition(self) -> BattlePreparation:
        self.click(self.EXPEDITION)
        return BattlePreparation(self.driver)


    @allure.step("Переход в меню кампании")
    def select_campaign(self):
        self.click(self.CAMPAIGN)
