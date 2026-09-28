"""Совместная игра по ссылке (задача 6.3): владелец и гость в двух браузерах, обновления листа через SSE."""

import re
from urllib.parse import urlparse

import allure
from playwright.sync_api import Browser, Page, expect
from pytest_check import check

from pages.battle_page import BattlePage
from pages.campaign_battle_page import CampaignBattleSetupPage, to_rewards, win
from pages.campaign_sheet_page import CampaignSheetPage
from pages.campaigns_page import CampaignsPage
from pages.main_page import MainPage
from pages.rewards_page import OutcomePage, TransitionPage
from pages.share_dialog import ShareDialog
from tests.test_campaign_progression import play_prologue
from tests.test_login import login, unique_email


@allure.feature("Совместная игра")
class TestSharedCampaign:

    @allure.title("Владелец и гость: баннер боя, предупреждение, первая победа закрывает главу, отзыв ссылки")
    def test_owner_and_guest(self, page: Page, browser: Browser, base_url: str, mailpit):
        # подготовка: владелец создаёт кампанию, проходит пролог и делится ссылкой
        login(page, mailpit, unique_email(), name="Алиса")
        CampaignsPage(page).open().create().create("Общая", ["DAREON", "MIRA"])
        expect(page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
        campaign_id = int(page.url.rstrip("/").split("/")[-3])
        owner_sheet = play_prologue(page, campaign_id)
        share = ShareDialog(page).open_from_sheet()
        invite = urlparse(share.create()).path
        share.close()

        # гость открывает ссылку в другом браузере и присоединяется под именем
        guest_context = browser.new_context(base_url=base_url, locale="ru-RU", viewport={"width": 412, "height": 915})
        guest = guest_context.new_page()
        guest.goto(invite)
        expect(guest.get_by_test_id("join-campaign")).to_contain_text("«Общая»")
        guest.get_by_test_id("join-name").fill("Вадим")
        guest.get_by_test_id("join-submit").click()
        guest_sheet = CampaignSheetPage(guest, campaign_id)
        guest_sheet.should_be_open()
        with check("Токен убран из адресной строки гостя"):
            expect(guest).to_have_url(re.compile(rf"/campaigns/{campaign_id}$"))
        with check("У гостя нет «Поделиться» и «Удалить кампанию»"):
            expect(guest.get_by_test_id("sheet-share")).to_have_count(0)
            expect(guest.get_by_test_id("sheet-delete")).to_have_count(0)

        # гость начинает бой по заданию 1 — у владельца появляется баннер «Идёт бой»
        guest.get_by_test_id("sheet-start-battle").click()
        guest_battle = CampaignBattleSetupPage(guest).select_quest(1).start_on_demand()
        with check("Баннер у владельца без перезагрузки (SSE)"):
            expect(page.get_by_test_id("sheet-active-battle")).to_contain_text("Вадим", timeout=5000)

        # владелец начинает второй бой: предупреждение, но старт не блокируется
        page.get_by_test_id("sheet-start-battle").click()
        owner_setup = CampaignBattleSetupPage(page).select_quest(2)
        owner_setup.by_test_id(owner_setup.MODE).get_by_text("По запросу", exact=True).click()
        owner_setup.click(owner_setup.START)
        warning = page.get_by_test_id("active-battle-warning")
        with check("Предупреждение называет бой гостя"):
            expect(warning).to_contain_text("Вадим, задание 1")
        page.get_by_test_id("active-battle-warning-start").click()
        owner_battle = BattlePage(page)
        owner_battle.should_be_open()
        owner_result = win(owner_battle)

        # гость первым принимает победу — глава закрыта
        to_rewards(win(guest_battle))
        OutcomePage(guest).should_be_open()
        OutcomePage(guest).accept()
        with check("Гостю — награды главы 2"):
            assert TransitionPage(guest).to_chapter() == 2

        # результат владельца больше не принимается — с объяснением
        to_rewards(owner_result)
        reason = page.get_by_test_id("result-changed-reason").first
        with check("Причина: глава 1 уже завершена победой гостя"):
            expect(reason).to_contain_text("Глава 1 уже завершена")
            expect(reason).to_contain_text("Вадим")
        page.get_by_test_id("result-dismiss-changed").click()
        owner_sheet.should_be_open()

        # владелец отзывает ссылку — гость теряет доступ и возвращается в меню
        guest_sheet.open()
        guest_sheet.should_be_open()
        ShareDialog(page).open_from_sheet().revoke()
        with check("Гость в меню с сообщением об отзыве доступа"):
            MainPage(guest).should_be_open()
            expect(guest.get_by_text("Доступ к кампании закрыт: ссылку отозвали.")).to_be_visible(timeout=10000)
        guest_context.close()
