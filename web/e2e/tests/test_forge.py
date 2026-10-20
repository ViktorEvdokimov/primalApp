"""Кузница (задача 9.1): кузня стихии открывается победой над её боссом, охотник создаёт снаряжение."""

import re

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_sheet_page import CampaignSheetPage
from pages.campaigns_page import CampaignsPage
from pages.forge_page import ForgePage
from tests.test_campaign_progression import campaign_id, play_prologue  # noqa: F401 — фикстура campaign_id
from tests.test_login import register


@allure.feature("Кузница")
class TestForge:

    @allure.title("После пролога открыта кузня огня; Дареон создаёт «Язык пламени» — ресурсы списаны")
    def test_craft_after_prologue(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка: пролог (Вираксен — огонь), Дареону — кости и кровь на листе
        sheet = play_prologue(page, campaign_id)
        sheet.select_hunter("Дареон")
        sheet.increment("Кости").increment("Кровь")
        sheet.wait_resources_saved()
        page.get_by_test_id("sheet-open-forge").click()
        forge = ForgePage(page)
        forge.should_be_open()

        # проверка: открыта только кузня огня, оружие — только Дареона
        with check("Кнопки перехода — только огонь"):
            assert forge.jump_elements() == ["FIRE"]
        with check("Дареон видит свой меч, шлем, доспех и предметы, но не лук Миры"):
            assert forge.item_names("FIRE") == [
                "Язык пламени", "Чешуйчатый шлем", "Чешуйчатый доспех", "Перчатка Волтьяра", "Лавовый щит"]
        before = forge.stock()

        # вызов
        forge.create("Язык пламени")

        # проверка
        with check("Сообщение о созданной карте 1-го уровня"):
            expect(page.get_by_text("«Язык пламени» создан — возьмите из коробки карту 1-го уровня.")).to_be_visible()
        with check("Списаны 1 огонь, 1 кости, 1 кровь"):
            forge.wait_stock("FIRE", before["FIRE"] - 1)
            after = forge.stock()
            assert after.get("FIRE", 0) == before["FIRE"] - 1
            assert after.get("BONES", 0) == before["BONES"] - 1
            assert after.get("BLOOD", 0) == before["BLOOD"] - 1
        forge.select_hunter("Мира")
        with check("Мира видит свой лук"):
            assert forge.item_names("FIRE")[0] == "Лук-испепелитель"

        # лист кампании показывает тот же запас
        page.get_by_test_id("forge-back").click()
        CampaignSheetPage(page, campaign_id).should_be_open()
        sheet.select_hunter("Дареон")
        with check("На листе кости Дареона списаны"):
            assert sheet.resource_value("Кости") == before["BONES"] - 1

    @allure.title("«Описание» показывает карту снаряжения из файлов игры: оружие Кары, шлем Миры")
    def test_equipment_card(self, page: Page):
        # подготовка: кампания с Карой и Мирой, пролог открыл кузню огня
        register(page)
        CampaignsPage(page).open().create().create("Карты снаряжения", ["KARA", "MIRA"])
        expect(page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
        play_prologue(page, int(page.url.rstrip("/").split("/")[-3]))
        page.get_by_test_id("sheet-open-forge").click()
        forge = ForgePage(page)
        forge.should_be_open()
        forge.select_hunter("Кара")

        # вызов
        image = forge.open_card("Крылья дракона")

        # проверка
        with check("Карта «Крылья дракона» 1-го уровня загружена"):
            expect(image).to_have_attribute("src", "/cards/FIRE_05-1.webp")
            assert image.evaluate("img => img.complete && img.naturalWidth") > 0
        page.keyboard.press("Escape")
        expect(image).to_be_hidden()
        forge.select_hunter("Мира")
        image = forge.open_card("Чешуйчатый шлем")
        with check("Карта шлема «Чешуйчатый шлем» 1-го уровня загружена"):
            expect(image).to_have_attribute("src", "/cards/FIRE_09-1.webp")
            assert image.evaluate("img => img.complete && img.naturalWidth") > 0

    @allure.title("Фильтр по типу предмета: только шлемы, только предметы, снова все")
    def test_type_filter(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка: пролог открыл кузню огня
        sheet = play_prologue(page, campaign_id)
        sheet.select_hunter("Дареон")
        page.get_by_test_id("sheet-open-forge").click()
        forge = ForgePage(page)
        forge.should_be_open()
        items = forge.section("FIRE").get_by_test_id("forge-item")

        # вызов
        forge.filter_type("HELMET")

        # проверка
        with check("Только шлем"):
            expect(items).to_have_count(1)
            assert forge.item_names("FIRE") == ["Чешуйчатый шлем"]
        forge.filter_type("ITEM")
        with check("Только предметы"):
            expect(items).to_have_count(2)
            assert forge.item_names("FIRE") == ["Перчатка Волтьяра", "Лавовый щит"]
        forge.filter_type("ALL")
        with check("Снова весь планшет"):
            expect(items).to_have_count(5)
