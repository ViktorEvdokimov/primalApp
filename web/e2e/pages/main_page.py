import allure

from pages.base_page import BasePage


class MainPage(BasePage):
    """Главное меню."""

    path = "/"
    root_test_id = "main-menu"

    EXPEDITION = "menu-expedition"
    CAMPAIGNS = "menu-campaigns"
    RESUME_BATTLE = "menu-resume-battle"

    def go_to_expedition(self) -> None:
        self.click(self.EXPEDITION)

    @allure.step("Открыть подготовку экспедиции")
    def select_expedition(self):
        from pages.expedition_page import ExpeditionPage

        self.go_to_expedition()
        expedition = ExpeditionPage(self.page)
        expedition.should_be_open()
        return expedition

    def is_resume_battle_visible(self) -> bool:
        return self.by_test_id(self.RESUME_BATTLE).is_visible()

    @allure.step("Нажать «Вернуться к бою»")
    def resume_battle(self):
        from pages.battle_page import BattlePage

        self.click(self.RESUME_BATTLE)
        battle = BattlePage(self.page)
        battle.should_be_open()
        return battle
