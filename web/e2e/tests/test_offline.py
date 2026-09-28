"""Сайт как приложение и экспедиция без сети (задача 7.1)."""

import allure
from playwright.sync_api import Browser, expect
from pytest_check import check

from pages.campaign_battle_page import win
from pages.expedition_page import ExpeditionPage
from pages.main_page import MainPage

VIRAXEN = "Огонь - Вираксен"


@allure.feature("Без сети")
class TestOffline:

    @allure.title("Манифест приложения: название, иконки, standalone")
    def test_manifest(self, page, base_url: str):
        # вызов
        response = page.request.get(f"{base_url}/manifest.webmanifest")

        # проверка
        manifest = response.json()
        with check("Манифест для установки на телефон"):
            assert manifest["display"] == "standalone"
            assert manifest["short_name"] == "Primal"
            assert {icon["sizes"] for icon in manifest["icons"]} >= {"192x192", "512x512"}

    @allure.title("После одного открытия сайта экспедиция проходит без сети")
    def test_expedition_offline(self, browser: Browser, base_url: str):
        # подготовка: первое открытие — service worker ставится, справочники попадают в кэш
        context = browser.new_context(base_url=base_url, locale="ru-RU", viewport={"width": 412, "height": 915})
        page = context.new_page()
        page.goto("/")
        page.wait_for_function("navigator.serviceWorker && navigator.serviceWorker.controller !== null", timeout=20000)
        expedition = ExpeditionPage(page).open()
        expedition.should_be_open()
        expect(expedition.by_test_id(expedition.BOSS).locator("option", has_text="Вираксен")).to_have_count(1)

        # вызов: сети нет, сайт открывается заново
        context.set_offline(True)
        page.goto("/")
        MainPage(page).should_be_open()
        with check("Баннер «Нет подключения»"):
            expect(page.get_by_test_id("offline-banner")).to_be_visible()
        page.get_by_test_id("menu-expedition").click()
        expedition = ExpeditionPage(page)
        expedition.should_be_open()
        expedition.select_boss(VIRAXEN)
        expedition.set_stance_change("")
        result = win(expedition.start_battle())

        # проверка
        with check("Бой без сети доведён до победы"):
            assert result.result() == "VICTORY"
        context.close()
