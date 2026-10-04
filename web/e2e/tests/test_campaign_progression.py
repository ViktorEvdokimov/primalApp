"""Прогрессия кампании в браузере (задача 5.4): пролог, бой по заданию, награды, переход главы.

Главы на сайте — главы книги: пролог — 0 (defects.md R-1). Бой выигрывается одним вводом урона: на подготовке
смена стойки «по запросу», урон = здоровье × прочность.
"""

import re

import allure
import pytest
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_battle_page import CampaignBattleSetupPage, to_rewards, win
from pages.campaign_sheet_page import CampaignSheetPage
from pages.campaigns_page import CampaignsPage
from pages.rewards_page import OutcomePage, TransitionPage
from tests.test_login import register


@pytest.fixture
def campaign_id(page: Page) -> int:
    """Новая кампания с Дареоном и Мирой: после создания открыта подготовка пролога."""
    register(page)
    CampaignsPage(page).open().create().create("Прогресс", ["DAREON", "MIRA"])
    expect(page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
    return int(page.url.rstrip("/").split("/")[-3])


def play_prologue(page: Page, campaign_id: int) -> CampaignSheetPage:
    """Победа в прологе и переход в главу 1."""
    result = win(CampaignBattleSetupPage(page).start_on_demand())
    to_rewards(result)
    transition = TransitionPage(page)
    assert transition.to_chapter() == 1
    transition.accept()
    sheet = CampaignSheetPage(page, campaign_id)
    sheet.should_be_open()
    return sheet


def win_quest(page: Page, number: int) -> OutcomePage:
    """С листа: «Начать бой» → задание → победа → окно наград."""
    page.get_by_test_id("sheet-start-battle").click()
    setup = CampaignBattleSetupPage(page).select_quest(number)
    to_rewards(win(setup.start_on_demand()))
    outcome = OutcomePage(page)
    outcome.should_be_open()
    return outcome


@allure.feature("Прогрессия кампании")
class TestCampaignProgression:

    @allure.title("Создание → пролог → глава 1 → задание 1 → глава 2 без правок на листе")
    def test_prologue_to_chapter_two(self, page: Page, campaign_id: int):
        # подготовка пролога сразу после создания кампании
        setup = CampaignBattleSetupPage(page)
        with check("Пролог: Вираксен без выбора, сложность 0, охотников 2"):
            assert setup.purpose() == "PROLOGUE"
            assert setup.boss() == "VIRAXEN" and setup.is_boss_locked()
            assert setup.difficulty() == "0" and setup.hunters() == "2"

        # пролог принимается без окна наград (36.1) — сразу награды главы 1
        to_rewards(win(setup.start_on_demand()))
        transition = TransitionPage(page)
        with check("После пролога — награды главы 1: задания 1, 2, 36"):
            assert transition.to_chapter() == 1
            assert transition.open_quests_text() == "Добавляются задания: 1, 2, 36"
        transition.accept()
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.should_be_open()
        with check("Глава 1"):
            assert sheet.chapter() == 1

        # бой по заданию 1: Торамат, сложность 1
        page.get_by_test_id("sheet-start-battle").click()
        setup = CampaignBattleSetupPage(page)
        with check("Выбор задания: открытые задания главы 1"):
            assert setup.open_quests() == [1, 2, 36]
        setup.select_quest(1)
        with check("Задание 1: Торамат, сложность 1"):
            assert setup.purpose() == "QUEST" and setup.boss() == "TORAMAT" and setup.difficulty() == "1"
        to_rewards(win(setup.start_on_demand()))

        # награды задания: «Отклонить» показывает последствия, «Вернуться» ничего не отправляет
        outcome = OutcomePage(page)
        outcome.should_be_open()
        with check("Награды: 2 «Рога» и ресурсы задания, добавляется задание 4"):
            assert outcome.resources()["HORN"] == 2 and outcome.resources()["BONES"] == 2
            assert outcome.open_quests_text() == "Добавляются задания: 4"
        dialog = outcome.dismiss()
        with check("Окно «Отклонить» перечисляет, что не будет применено"):
            consequences = dialog.consequences()
            assert consequences[0] == "Задание 1 «Память пустыни» не будет отмечено выполненным."
            assert "Переход в главу 2 не состоится, глава останется 1." in consequences
        dialog.cancel()
        outcome.accept()

        # переход в главу 2
        transition = TransitionPage(page)
        with check("Награды главы 2"):
            assert transition.to_chapter() == 2
        transition.accept()
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.should_be_open()
        with check("Глава 2"):
            assert sheet.chapter() == 2
        sheet.open_tab("quests")
        with check("Задание 1 выполнено, задание 4 открыто"):
            assert sheet.completed_quests() == [1]
            assert 4 in sheet.open_quests()
        sheet.open_tab("trophies")
        with check("Трофеи Вираксена и Торамата"):
            expect(page.get_by_test_id("trophy")).to_have_count(2)

    @allure.title("«Отклонить» награды боя после подтверждения: глава и задания не меняются")
    def test_dismiss_rewards(self, page: Page, campaign_id: int):
        # подготовка
        play_prologue(page, campaign_id)
        outcome = win_quest(page, 1)

        # вызов
        outcome.dismiss().confirm()

        # проверка
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.should_be_open()
        with check("Глава осталась 1"):
            assert sheet.chapter() == 1
        with check("Можно снова начать бой"):
            expect(page.get_by_test_id("sheet-start-battle")).to_be_visible()
        sheet.open_tab("quests")
        with check("Задание 1 по-прежнему открыто"):
            assert 1 in sheet.open_quests() and sheet.completed_quests() == []

    @allure.title("Неотправленный результат: выход в меню оставляет бой, баннер ведёт к наградам")
    def test_pending_result_banner(self, page: Page, campaign_id: int):
        # подготовка
        play_prologue(page, campaign_id)
        page.get_by_test_id("sheet-start-battle").click()
        setup = CampaignBattleSetupPage(page).without_quest()
        setup.by_test_id(setup.BOSS).select_option("TORAMAT")
        result = win(setup.start_on_demand())

        # вызов
        result.click("battle-result-menu")

        # проверка
        banner = page.get_by_test_id("pending-result")
        with check("В меню — «Результат боя не отправлен»"):
            expect(banner).to_be_visible()
        page.get_by_test_id("pending-result-send").click()
        outcome = OutcomePage(page)
        outcome.should_be_open()
        with check("Бой без задания: трофей выбранного босса"):
            assert any(line.startswith("Трофей:") for line in outcome.lines())

    @allure.title("Нет сети при «Принять»: результат остаётся в браузере и уходит сам, когда сеть появляется")
    def test_offline_accept(self, page: Page, campaign_id: int):
        # подготовка
        play_prologue(page, campaign_id)
        outcome = win_quest(page, 1)

        # вызов
        page.context.set_offline(True)
        outcome.accept()

        # проверка
        with check("«Результат боя не отправлен» и «Отправить снова»"):
            expect(page.get_by_test_id("result-not-sent")).to_be_visible()
        # вызов: сеть вернулась — итог уходит сам, без нажатия «Отправить снова»
        page.context.set_offline(False)

        # проверка
        transition = TransitionPage(page)
        with check("После восстановления сети итог принят — награды главы 2"):
            assert transition.to_chapter() == 2


@allure.feature("Прогрессия кампании")
class TestCampaignDefeat:

    @allure.title("Поражение по заданию: окна наград нет — «Завершить бой» сразу возвращает на лист, задание открыто")
    def test_defeat_without_rewards(self, page: Page, campaign_id: int):
        # подготовка: глава 1, задание 1
        sheet = play_prologue(page, campaign_id)
        page.get_by_test_id("sheet-start-battle").click()
        battle = CampaignBattleSetupPage(page).select_quest(1).start_on_demand()

        # вызов: «Сдаться» → «Завершить бой»
        result = battle.surrender().confirm()
        with check("Кнопка — «Завершить бой», не «К наградам»"):
            expect(page.get_by_test_id("battle-to-rewards")).to_have_text("Завершить бой")
        result.click("battle-to-rewards")

        # проверка
        sheet.should_be_open()
        expect(page).to_have_url(re.compile(rf"/campaigns/{campaign_id}$"))
        with check("Окна «Награды за поражение» не было"):
            expect(page.get_by_text("Награды за поражение")).to_have_count(0)
        with check("Задание 1 по-прежнему открыто, глава 1"):
            assert 1 in sheet.open_quests()
            assert sheet.chapter() == 1
