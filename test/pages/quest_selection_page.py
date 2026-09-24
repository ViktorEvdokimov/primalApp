import re

import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage


class QuestSelectionPage(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выбор задания"]')
    QUEST_ITEMS = (AppiumBy.XPATH, '//android.view.View[@clickable="true"]/android.widget.TextView')
    NO_QUESTS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нет открытых заданий."]')
    CONTINUE_WITHOUT_QUEST = (AppiumBy.XPATH, '//android.widget.TextView[@text="Продолжить без задания"]/parent::android.view.View')
    CANCEL = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отмена"]/parent::android.view.View')

    @allure.step("Проверить, что диалог выбора задания открыт")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Получить список доступных заданий")
    def get_available_quests(self) -> list[dict]:
        self.wait.until(EC.visibility_of_element_located(self.TITLE))
        quests = []
        for el in self.driver.find_elements(*self.QUEST_ITEMS):
            match = re.match(r"(\d+)\.\s*(.+)", el.text.strip())
            if match:
                quests.append({"number": int(match.group(1)), "name": match.group(2)})
        return quests

    @allure.step("Получить номера доступных заданий")
    def get_available_quest_numbers(self) -> list[int]:
        return sorted(q["number"] for q in self.get_available_quests())

    @allure.step("Выбрать задание по номеру: {number}")
    def select_quest(self, number: int) -> "BattlePreparation":
        from pages.battle_page import BattleSource
        from pages.battle_preparation import BattlePreparation
        self.click((AppiumBy.XPATH, f'//android.widget.TextView[starts-with(@text, "{number}.")]/parent::android.view.View'))
        return BattlePreparation(self.driver, source=BattleSource.CAMPAIGN)

    @allure.step("Продолжить без задания")
    def continue_without_quest(self) -> "BattlePreparation":
        from pages.battle_page import BattleSource
        from pages.battle_preparation import BattlePreparation
        self.click(self.CONTINUE_WITHOUT_QUEST)
        return BattlePreparation(self.driver, source=BattleSource.CAMPAIGN)

    @allure.step("Нажать «Отмена»")
    def cancel(self) -> "CampaignSheetPage":
        from pages.campaign_sheet_page import CampaignSheetPage
        self.click(self.CANCEL)
        self.wait.until(EC.invisibility_of_element_located(self.TITLE))
        return CampaignSheetPage(self.driver)
