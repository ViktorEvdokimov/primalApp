import allure
from playwright.sync_api import Page, expect

from pages.base_page import BasePage
from pages.battle_page import BattlePage, ResultPage


class CampaignBattleSetupPage(BasePage):
    """Подготовка к бою кампании (задача 5.4): выбор задания, затем форма подготовки с префиллом."""

    root_test_id = "page-campaign-battle-new"

    PURPOSE = "campaign-battle-purpose"
    BOSS = "campaign-battle-boss"
    DIFFICULTY = "campaign-battle-difficulty"
    HUNTERS = "campaign-battle-hunters"
    MODE = "campaign-battle-mode"
    START = "campaign-battle-start"

    def __init__(self, page: Page, campaign_id: int | None = None):
        super().__init__(page)
        if campaign_id is not None:
            self.path = f"/campaigns/{campaign_id}/battle/new"

    # --- выбор задания ---

    def open_quests(self) -> list[int]:
        items = self.by_test_id("quest-select-item")
        expect(self.by_test_id("quest-select")).to_be_visible()
        return [int(item.get_attribute("data-number") or "0") for item in items.all()]

    @allure.step("Выбрать задание {number}")
    def select_quest(self, number: int) -> "CampaignBattleSetupPage":
        self.page.locator(f'[data-testid="quest-select-item"][data-number="{number}"]').click()
        expect(self.by_test_id(self.PURPOSE)).to_be_visible()
        return self

    @allure.step("«Продолжить без задания»")
    def without_quest(self) -> "CampaignBattleSetupPage":
        self.click("quest-select-free")
        expect(self.by_test_id(self.PURPOSE)).to_be_visible()
        return self

    # --- подготовка ---

    def purpose(self) -> str:
        expect(self.by_test_id(self.PURPOSE)).to_be_visible()
        return self.by_test_id(self.PURPOSE).get_attribute("data-purpose") or ""

    def purpose_text(self) -> str:
        return self.by_test_id(self.PURPOSE).inner_text()

    def boss(self) -> str:
        return self.by_test_id(self.BOSS).input_value()

    def is_boss_locked(self) -> bool:
        return self.by_test_id(self.BOSS).is_disabled()

    def difficulty(self) -> str:
        return self.by_test_id(self.DIFFICULTY).locator("input:checked").get_attribute("value") or ""

    def hunters(self) -> str:
        return self.by_test_id(self.HUNTERS).input_value()

    @allure.step("Начать бой со сменой стойки по запросу")
    def start_on_demand(self) -> BattlePage:
        """Стойка меняется только по кнопке: победа одним вводом урона, без окон смены стойки."""
        self.by_test_id(self.MODE).get_by_text("По запросу", exact=True).click()
        self.click(self.START)
        battle = BattlePage(self.page)
        battle.should_be_open()
        return battle


@allure.step("Победить: урон = здоровье × прочность")
def win(battle: BattlePage) -> ResultPage:
    battle.apply_damage_manual(str((battle.get_health_value() or 0) * (battle.get_toughness_value() or 0)))
    result = ResultPage(battle.page)
    result.should_be_open()
    return result


@allure.step("«К наградам»")
def to_rewards(result: ResultPage) -> None:
    result.click("battle-to-rewards")
