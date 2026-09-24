import re

import allure
from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage


class CampaignListPage(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ваши кампании"]')
    CAMPAIGNS_COUNT = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Кампаний:")]')
    NEW_CAMPAIGN_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Новая кампания"]/parent::android.view.View')
    BACK_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Назад в меню"]/parent::android.view.View')

    @allure.step("Проверить, что список кампаний отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    def _parse_counter(self) -> tuple[int, int]:
        text = self.get_text(self.CAMPAIGNS_COUNT)
        match = re.search(r"(\d+)\s*/\s*(\d+)", text)
        assert match, f"Не удалось разобрать счётчик кампаний: «{text}»"
        return int(match.group(1)), int(match.group(2))

    @allure.step("Получить количество кампаний")
    def get_campaigns_count(self) -> int:
        return self._parse_counter()[0]

    @allure.step("Получить общее количество кампаний из лимита")
    def get_campaigns_limit(self) -> int:
        return self._parse_counter()[1]

    @allure.step("Получить список названий кампаний")
    def get_campaign_names(self) -> list[str]:
        elements = self.driver.find_elements(AppiumBy.XPATH, '//android.view.View[@is-collection="true"]//android.widget.TextView[not(@text="Удалить")]')
        return [el.text.strip() for el in elements
                if el.text.strip() and not re.match(r"Глава \d", el.text.strip())]

    @allure.step("Открыть кампанию по названию: {name}")
    def open_campaign(self, name: str) -> "CampaignSheetPage":
        from pages.campaign_sheet_page import CampaignSheetPage
        card = f'android.view.View[@clickable="true"][.//android.widget.TextView[@text="{name}"]]'
        self.click((AppiumBy.XPATH, f'//{card}[not(.//{card})]'))
        return CampaignSheetPage(self.driver)

    @allure.step("Удалить кампанию по названию: {name}")
    def delete_campaign(self, name: str) -> "CampaignListPage":
        before = self.get_campaign_names().count(name)
        self.click((AppiumBy.XPATH, f'//android.widget.TextView[@text="{name}"]/following::android.widget.TextView[@text="Удалить"]'))
        self.wait.until(lambda _: self.get_campaign_names().count(name) < before)
        return self

    @allure.step("Проверить доступность кнопки «Новая кампания»")
    def is_new_campaign_enabled(self) -> bool:
        return self.driver.find_element(*self.NEW_CAMPAIGN_BUTTON).get_attribute("enabled") == "true"

    @allure.step("Нажать кнопку «Новая кампания»")
    def new_campaign(self) -> "CreateCampaignPage":
        from pages.create_campaign_page import CreateCampaignPage
        self.click(self.NEW_CAMPAIGN_BUTTON)
        return CreateCampaignPage(self.driver)

    @allure.step("Нажать кнопку «Назад в меню»")
    def go_back_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.BACK_TO_MENU)
        return MainPage(self.driver)