"""Инвентарь и обмен ресурсов (задача 9.3): стартовые предметы, правка без оплаты, обмен, преобразование, продажа."""

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_sheet_page import CampaignSheetPage
from pages.forge_page import ForgePage
from pages.inventory_panel import ExchangeDialog, InventoryPanel
from tests.test_campaign_progression import campaign_id, play_prologue  # noqa: F401 — фикстура campaign_id


@allure.feature("Инвентарь")
class TestInventory:

    @allure.title("Новая кампания: стартовые предметы по правилам; правка, добавление и удаление без оплаты")
    def test_starting_kit_and_edit(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.open()
        sheet.should_be_open()
        inventory = InventoryPanel(page)

        # проверка: базовое оружие класса, основные шлем и доспех, зелье «Алемор» — всё 1-го уровня
        sheet.select_hunter("Дареон")
        inventory.wait_item("Большой меч")
        with check("Стартовое снаряжение Дареона"):
            assert inventory.names("EQUIPMENT") == ["Большой меч", "Основной шлем", "Основной доспех"]
        with check("Стартовое зелье"):
            assert inventory.names("POTION") == ["Алемор"]
        with check("Уровень 1"):
            assert inventory.level("Большой меч") == 1
        sheet.select_hunter("Мира")
        inventory.wait_item("Большой лук")
        with check("Оружие Миры — большой лук"):
            assert inventory.names("EQUIPMENT")[0] == "Большой лук"

        # вызов: правка у Миры
        inventory.set_level("Основной шлем", 2)
        inventory.add("Карта награды №7", kind="REWARD")
        inventory.remove("Основной доспех")

        # проверка: после перезагрузки всё сохранено
        page.reload()
        sheet.should_be_open()
        sheet.select_hunter("Мира")
        inventory.wait_item("Карта награды №7")
        with check("Уровень шлема сохранён"):
            assert inventory.level("Основной шлем") == 2
        with check("Карта награды — без уровня"):
            assert inventory.names("REWARD") == ["Карта награды №7"]
            assert inventory.level("Карта награды №7") is None
        with check("Доспех убран"):
            assert "Основной доспех" not in inventory.names()

    @allure.title("Кузница: не хватает чешуи — обмен с Мирой, преобразование стихии, продажа карты; созданный предмет — в инвентаре")
    def test_exchange_from_forge(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка: пролог открывает кузню огня; Мире — 2 чешуи, Дареону — кости, кровь и огонь
        sheet = play_prologue(page, campaign_id)
        sheet.select_hunter("Мира")
        sheet.increment("Чешуя").increment("Чешуя")
        sheet.wait_resources_saved()
        sheet.select_hunter("Дареон")
        sheet.increment("Кости").increment("Кровь").increment("Огонь")
        sheet.wait_resources_saved()
        page.get_by_test_id("sheet-open-forge").click()
        forge = ForgePage(page)
        forge.should_be_open()
        before = forge.stock()
        need = 2 - before.get("SCALES", 0)  # пролог мог дать чешую

        # вызов: «Лавовый щит» — 2 чешуи, у Дареона их не хватает
        forge.item("Лавовый щит").get_by_test_id("forge-item-exchange").click()
        dialog = ExchangeDialog(page)
        dialog.should_be_open()

        # проверка: «что получить» — чешуя, взамен — материи Дареона, с кем — только Мира (у неё есть чешуя)
        with check("Подсказка, чего не хватает"):
            assert f"Чешуя {need}" in dialog.lacking()
        with check("Предзаполнено «что хотите получить»"):
            assert dialog.want() == "SCALES"
        with check("С кем — только у кого есть чешуя"):
            assert [o.split(" (")[0] for o in dialog.options("exchange-partner")] == ["Мира"]
        with check("Взамен — только материи"):
            assert all(o.split(" (")[0] in CampaignSheetPage.MATERIAL_NAMES for o in dialog.options("exchange-offer"))

        # вызов: обмен 1 к 1 — по чешуе за кость, затем за кровь (если не хватает двух)
        dialog.trade(offer="BONES").submit()
        if need == 2:
            forge.wait_stock("SCALES", 1)
            forge.item("Лавовый щит").get_by_test_id("forge-item-exchange").click()
            dialog = ExchangeDialog(page)
            dialog.should_be_open()
            dialog.trade(offer="BLOOD").submit()

        # проверка
        with check("Сообщение об обмене"):
            expect(page.get_by_text("получил(а) «Чешуя»").last).to_be_visible()
        forge.wait_stock("SCALES", 2)
        with check("Отданное ушло Мире"):
            after = forge.stock()
            assert after.get("BONES", 0) == before["BONES"] - 1
            assert after.get("BLOOD", 0) == before["BLOOD"] - (need - 1)

        # вызов: создание щита, потом преобразование огня в кость из любого недоступного предмета
        forge.create("Лавовый щит")
        forge.wait_stock("SCALES", 0)
        page.get_by_test_id("forge-item-exchange").first.click()
        dialog = ExchangeDialog(page)
        dialog.should_be_open()
        dialog.mode("Преобразование")
        fire = forge.stock().get("FIRE", 0)
        bones = forge.stock().get("BONES", 0)
        dialog.gain("BONES").submit()

        # проверка
        forge.wait_stock("FIRE", fire - 1)
        with check("Кость получена"):
            assert forge.stock().get("BONES", 0) == bones + 1

        # вызов: продажа «Лавового щита» за огонь (его кузня)
        page.get_by_test_id("forge-item-exchange").first.click()
        dialog = ExchangeDialog(page)
        dialog.should_be_open()
        dialog.mode("Продажа предмета")
        with check("Зелья нет среди карт на продажу"):
            assert all("Алемор" not in option for option in dialog.card_options())
        page.get_by_test_id("exchange-card").select_option(label="Лавовый щит · 1")
        dialog.gain("FIRE").submit()

        # проверка
        forge.wait_stock("FIRE", fire)
        page.get_by_test_id("forge-back").click()
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.should_be_open()
        sheet.select_hunter("Дареон")
        inventory = InventoryPanel(page)
        inventory.wait_item("Большой меч")
        with check("Проданный щит ушёл из инвентаря"):
            assert "Лавовый щит" not in inventory.names()

    @allure.title("Обмен из листа: выбрать, что получить, что предложить и с кем")
    def test_exchange_from_sheet(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка: у Миры — иридия, у Дареона — кровь
        sheet = CampaignSheetPage(page, campaign_id)
        sheet.open()
        sheet.should_be_open()
        sheet.select_hunter("Мира")
        sheet.increment("Иридия")
        sheet.wait_resources_saved()
        sheet.select_hunter("Дареон")
        sheet.increment("Кровь")
        sheet.wait_resources_saved()

        # вызов
        page.get_by_test_id("inventory-exchange").click()
        dialog = ExchangeDialog(page)
        dialog.should_be_open()
        with check("Сначала ничего не выбрано"):
            assert dialog.want() == ""
        dialog.trade(want="IRIDIA", offer="BLOOD", partner="Мира").submit()

        # проверка
        expect(page.get_by_text("Дареон отдал(а) «Кровь» охотнику Мира и получил(а) «Иридия».")).to_be_visible()
        expect(sheet.resource("Иридия").get_by_test_id("resource-value")).to_have_text("1")
        with check("Кровь ушла"):
            assert sheet.resource_value("Кровь") == 0
        sheet.select_hunter("Мира")
        with check("Мира получила кровь"):
            assert sheet.resource_value("Кровь") == 1
