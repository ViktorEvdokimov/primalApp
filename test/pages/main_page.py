import allure
from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage
from pages.battle_preparation import BattlePreparation
from pages.battle_page import BattleSource


class MainPage(BasePage):
    EXPEDITION = (AppiumBy.XPATH, '//android.widget.TextView[@text="Режим экспедиции"]')
    CAMPAIGN = (AppiumBy.XPATH, '//android.widget.TextView[@text="Режим кампании"]')
    RESUME_BATTLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Вернуться к бою"]')

    @allure.step("Проверить наличие кнопки «Вернуться к бою»")
    def is_resume_battle_visible(self) -> bool:
        return self.is_element_visible_quick(self.RESUME_BATTLE, timeout=3)

    @allure.step("Нажать «Вернуться к бою»")
    def resume_battle(self) -> "BattlePage":
        from pages.battle_page import BattlePage
        self.click(self.RESUME_BATTLE)
        return BattlePage(self.driver, source=BattleSource.EXPEDITION)

    @allure.step("Переход в меню экспедиции")
    def select_expedition(self) -> BattlePreparation:
        self.click(self.EXPEDITION)
        return BattlePreparation(self.driver, source=BattleSource.EXPEDITION)


    @allure.step("Переход в меню кампании")
    def select_campaign(self) -> "CreateCampaignPage | CampaignListPage":
        from pages.campaign_list_page import CampaignListPage
        from pages.create_campaign_page import CreateCampaignPage
        self.click(self.CAMPAIGN)
        if self.is_element_visible_quick(CampaignListPage.TITLE, timeout=3):
            return CampaignListPage(self.driver)
        return CreateCampaignPage(self.driver)

    @allure.step("Создать кампанию: {name}")
    def create_campaign(self, name: str) -> "CreateCampaignPage":
        from pages.campaign_list_page import CampaignListPage
        from pages.create_campaign_page import CreateCampaignPage
        self.click(self.CAMPAIGN)
        if self.is_element_visible_quick(CampaignListPage.TITLE, timeout=3):
            list_page = CampaignListPage(self.driver)
            if name in list_page.get_campaign_names():
                list_page.delete_campaign(name)
            page = list_page.new_campaign()
        else:
            page = CreateCampaignPage(self.driver)
        page.set_campaign_name(name)
        return page
