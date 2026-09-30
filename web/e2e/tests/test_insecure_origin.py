"""Сайт по HTTP с адреса в локальной сети — как с телефона у стола (`doc/setup.md`, раздел 2.4).

Такой адрес браузер считает незащищённым: нет `crypto.randomUUID`, `navigator.clipboard` и service worker.
Имя `primal.lan` направляется на локальный стек правилом резолвера Chromium — контекст настоящий, не имитация.
"""

import re
from urllib.parse import urlparse

import allure
import pytest
from playwright.sync_api import BrowserType, Page, expect
from pytest_check import check

from pages.campaign_battle_page import win
from pages.campaigns_page import CampaignsPage
from pages.expedition_page import ExpeditionPage
from pages.main_page import MainPage
from tests.test_campaign_progression import play_prologue
from tests.test_login import register

LAN_HOST = "primal.lan"


@pytest.fixture
def lan_page(browser_type: BrowserType, browser_type_launch_args: dict, base_url: str):
    """Страница сайта по адресу http://primal.lan:<порт> — незащищённый контекст."""
    site = urlparse(base_url)
    if site.scheme != "http":
        pytest.skip("Проверка незащищённого адреса — только для локального стека по HTTP")
    target = "127.0.0.1" if site.hostname == "localhost" else site.hostname
    browser = browser_type.launch(
        **{**browser_type_launch_args, "args": [f"--host-resolver-rules=MAP {LAN_HOST} {target}"]})
    context = browser.new_context(base_url=f"http://{LAN_HOST}:{site.port or 80}", locale="ru-RU",
                                  viewport={"width": 412, "height": 915})
    page = context.new_page()
    page.goto("/")
    MainPage(page).should_be_open()
    assert page.evaluate("window.isSecureContext") is False, "Адрес должен быть незащищённым, как IP в сети"
    yield page
    context.close()
    browser.close()


@allure.feature("Адрес в локальной сети")
class TestInsecureOrigin:

    @allure.title("Экспедиция по HTTP с адреса в сети: «Начать бой» открывает бой")
    def test_expedition_starts(self, lan_page: Page):
        # подготовка
        expedition = ExpeditionPage(lan_page).open()
        expedition.should_be_open()
        expedition.set_stance_change("")

        # вызов
        battle = expedition.start_battle()

        # проверка
        with check("Бой открыт и доводится до победы"):
            assert win(battle).result() == "VICTORY"

    @allure.title("Кампания по HTTP с адреса в сети: бой пролога начинается, ссылка копируется")
    def test_campaign_battle_and_copy(self, lan_page: Page):
        # подготовка
        register(lan_page)
        CampaignsPage(lan_page).open().create().create("По сети", ["DAREON", "MIRA"])
        expect(lan_page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
        campaign_id = int(lan_page.url.rstrip("/").split("/")[-3])

        # вызов: бой пролога — «Начать бой» на подготовке кампании
        sheet = play_prologue(lan_page, campaign_id)

        # проверка: копирование ссылки без navigator.clipboard
        sheet.click("sheet-share")
        lan_page.get_by_test_id("share-create").click()
        expect(lan_page.get_by_test_id("share-url")).to_be_visible()
        lan_page.get_by_test_id("share-copy").click()
        with check("«Копировать» копирует и честно об этом сообщает"):
            expect(lan_page.get_by_text("Ссылка скопирована.")).to_be_visible()
        with check("Бой кампании пройден — лист открыт в главе 1"):
            assert sheet.chapter() == 1
