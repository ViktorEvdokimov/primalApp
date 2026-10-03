"""Лаборатория (задача 9.2): охотник готовит зелье из растений, растение на выбор — как в примере правил."""

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_sheet_page import CampaignSheetPage
from pages.lab_page import LabPage
from tests.test_campaign_progression import campaign_id  # noqa: F401 — фикстура


@allure.feature("Лаборатория")
class TestLab:

    @allure.title("«Эвок» без антемона: тратятся тармарет и меллис (пример правил)")
    def test_brew_with_choice(self, page: Page, campaign_id: int):  # noqa: F811
        # подготовка: у Дареона тармарет и меллис, антемона нет
        sheet = CampaignSheetPage(page, campaign_id).open()
        sheet.should_be_open()
        sheet.select_hunter("Дареон")
        sheet.increment("Тармарет").increment("Меллис")
        sheet.wait_resources_saved()
        page.get_by_test_id("sheet-open-lab").click()
        lab = LabPage(page)
        lab.should_be_open()

        # проверка: планшет из 6 зелий
        with check("6 зелий планшета"):
            assert lab.potion_names() == ["Алемор", "Имперум", "Ирден", "Хатрокс", "Эвок", "Видья"]

        # вызов
        dialog = lab.open_brew("Эвок")
        with check("Выбран меллис: антемона нет"):
            expect(dialog.get_by_test_id("lab-choice")).to_have_value("MELLIS")
            expect(dialog.get_by_test_id("lab-confirm-cost")).to_have_text("Будет списано: Тармарет 1, Меллис 1.")
        lab.confirm()

        # проверка
        with check("Сообщение о карте 1-го уровня"):
            expect(page.get_by_text("«Эвок» приготовлено — возьмите из коробки карту 1-го уровня.")).to_be_visible()
        with check("Тармарет и меллис списаны"):
            lab.wait_stock("TARMARET", 0)
            lab.wait_stock("MELLIS", 0)
        with check("Без растений «Эвок» недоступен"):
            expect(lab.potion("Эвок").get_by_test_id("lab-potion-brew")).to_be_disabled()
