"""Общие шаги для тестов режима кампании."""

import allure

from pages.battle_page import BattleSource, CampaignVictoryDialog, StanceChangeDialog
from pages.battle_preparation import BattlePreparation
from pages.campaign_list_page import CampaignListPage
from pages.campaign_rewards_page import CampaignRewardsPage, QuestRewardsPage
from pages.campaign_sheet_page import CampaignSheetPage
from pages.create_campaign_page import Hunter
from pages.main_page import MainPage

WINNING_DAMAGE = "200"
MAX_STANCES = 9


@allure.step("Создать и начать кампанию «{name}»")
def start_new_campaign(driver, name: str, hunters: list[Hunter] | None = None,
                       custom_names: dict[Hunter, str] | None = None) -> BattlePreparation:
    page = MainPage(driver).create_campaign(name)
    for hunter in hunters or [Hunter.DAREON, Hunter.MIRA]:
        page.select_hunter(hunter, custom_name=(custom_names or {}).get(hunter))
    prep = page.start_campaign()
    assert prep.is_displayed(), f"Кампания «{name}» не стартовала"
    return prep


@allure.step("Создать кампанию «{name}» и вернуться в главное меню")
def create_campaign_in_menu(driver, name: str, hunters: list[Hunter] | None = None,
                            custom_names: dict[Hunter, str] | None = None) -> MainPage:
    return start_new_campaign(driver, name, hunters, custom_names).exit_to_menu()


@allure.step("Открыть лист кампании «{name}»")
def open_sheet(driver, name: str) -> CampaignSheetPage:
    list_page = MainPage(driver).select_campaign()
    assert isinstance(list_page, CampaignListPage), "Ожидался список кампаний"
    sheet = list_page.open_campaign(name)
    assert sheet.is_displayed()
    return sheet


@allure.step("Победить в бою")
def win_battle(prep: BattlePreparation) -> CampaignVictoryDialog:
    battle = prep.start_battle()
    assert battle.source == BattleSource.CAMPAIGN
    victory = CampaignVictoryDialog(prep.driver)
    stance_dialog = StanceChangeDialog(prep.driver)
    battle.apply_damage_manual(WINNING_DAMAGE)
    # У многостоечных боссов (например, Пробуждённый — 5 стоек) урон сначала вызывает смену стойки:
    # подтверждаем предзаполненные значения новой стойки и добиваем
    for _ in range(MAX_STANCES):
        if victory.is_element_visible_quick(victory.TITLE, timeout=3):
            break
        if stance_dialog.is_element_visible_quick(stance_dialog.TITLE, timeout=2):
            stance_dialog.click_ok()
        battle.apply_damage_manual(WINNING_DAMAGE)
    assert victory.is_displayed(), "Диалог победы не появился"
    return victory


@allure.step("Создать кампанию «{name}» и победить в прологе")
def win_prologue(driver, name: str, hunters: list[Hunter] | None = None) -> CampaignRewardsPage:
    victory = win_battle(start_new_campaign(driver, name, hunters))
    rewards = victory.click_continue()
    assert isinstance(rewards, CampaignRewardsPage), "После пролога должны открываться награды главы"
    return rewards


@allure.step("Пройти пролог кампании «{name}» и принять награды главы 1")
def sheet_after_prologue(driver, name: str, hunters: list[Hunter] | None = None) -> CampaignSheetPage:
    sheet = win_prologue(driver, name, hunters).accept()
    assert sheet.is_displayed()
    return sheet


@allure.step("Выиграть бой из листа (задание: {quest})")
def win_battle_from_sheet(sheet: CampaignSheetPage, quest: int | None = None) -> QuestRewardsPage:
    selection = sheet.start_battle()
    prep = selection.select_quest(quest) if quest else selection.continue_without_quest()
    rewards = win_battle(prep).click_continue()
    assert isinstance(rewards, QuestRewardsPage), "После боя из листа должны открываться награды за задание"
    return rewards
