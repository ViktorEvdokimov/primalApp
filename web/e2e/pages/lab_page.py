import re

import allure
from playwright.sync_api import Locator, Page, expect

from pages.base_page import BasePage


class LabPage(BasePage):
    """Лаборатория кампании (задача 9.2): 6 зелий, цена одинакова на всех уровнях."""

    root_test_id = "page-campaign-lab"

    def __init__(self, page: Page, campaign_id: int | None = None):
        super().__init__(page)
        if campaign_id is not None:
            self.path = f"/campaigns/{campaign_id}/lab"

    def should_be_open(self) -> None:
        expect(self.by_test_id("lab-level")).to_be_visible(timeout=10000)

    def potion_names(self) -> list[str]:
        potions = self.by_test_id("lab-potion")
        expect(potions.first).to_be_visible()
        return [potion.get_attribute("data-name") or "" for potion in potions.all()]

    def potion(self, name: str) -> Locator:
        return self.page.locator(f'[data-testid="lab-potion"][data-name="{name}"]')

    @allure.step("Дождаться запаса {code} = {value}")
    def wait_stock(self, code: str, value: int) -> None:
        entry = self.page.locator(f'[data-testid="lab-stock-item"][data-code="{code}"]')
        if value == 0:
            expect(entry).to_have_count(0)
        else:
            expect(entry).to_have_text(re.compile(rf"\s{value}$"))

    @allure.step("Открыть приготовление «{name}»")
    def open_brew(self, name: str) -> Locator:
        self.potion(name).get_by_test_id("lab-potion-brew").click()
        dialog = self.by_test_id("lab-confirm")
        expect(dialog).to_be_visible()
        return dialog

    @allure.step("Приготовить")
    def confirm(self) -> None:
        self.click("lab-confirm-ok")
        expect(self.by_test_id("lab-confirm")).to_be_hidden()
