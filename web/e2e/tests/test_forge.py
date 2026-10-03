"""Кузница (задача 9.1): кузня стихии открывается победой над её боссом, охотник создаёт снаряжение."""

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_sheet_page import CampaignSheetPage
from pages.forge_page import ForgePage
from tests.test_campaign_progression import campaign_id, play_prologue  # noqa: F401 — фикстура campaign_id


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
