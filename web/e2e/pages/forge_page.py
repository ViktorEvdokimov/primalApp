import re

import allure
from playwright.sync_api import Locator, Page, expect

from pages.base_page import BasePage


class ForgePage(BasePage):
    """Кузница кампании (задача 9.1): планшеты открытых кузниц текущего уровня для выбранного охотника."""

    root_test_id = "page-campaign-forge"

    def __init__(self, page: Page, campaign_id: int | None = None):
        super().__init__(page)
        if campaign_id is not None:
            self.path = f"/campaigns/{campaign_id}/forge"

    def should_be_open(self) -> None:
        expect(self.by_test_id("forge-level")).to_be_visible(timeout=10000)

    def jump_elements(self) -> list[str]:
        """Стихии кнопок быстрого перехода."""
        return [jump.get_attribute("data-element") or "" for jump in self.by_test_id("forge-jump").all()]

    def section(self, element: str) -> Locator:
        return self.page.locator(f'[data-testid="forge-section"][data-element="{element}"]')

    def item_names(self, element: str) -> list[str]:
        items = self.section(element).get_by_test_id("forge-item")
        expect(items.first).to_be_visible()
        return [item.get_attribute("data-name") or "" for item in items.all()]

    def item(self, name: str) -> Locator:
        return self.page.locator(f'[data-testid="forge-item"][data-name="{name}"]')

    def stock(self) -> dict[str, int]:
        """Запас охотника: код ресурса → количество, по подписям «Огонь 2»."""
        result: dict[str, int] = {}
        for entry in self.by_test_id("forge-stock-item").all():
            result[entry.get_attribute("data-code") or ""] = int((entry.inner_text().split() or ["0"])[-1])
        return result

    @allure.step("Дождаться запаса {code} = {value}")
    def wait_stock(self, code: str, value: int) -> None:
        """Запас обновился после создания; нулевой ресурс в запасе не показывается."""
        entry = self.page.locator(f'[data-testid="forge-stock-item"][data-code="{code}"]')
        if value == 0:
            expect(entry).to_have_count(0)
        else:
            expect(entry).to_have_text(re.compile(rf"\s{value}$"))

    @allure.step("Выбрать охотника «{player}»")
    def select_hunter(self, player: str) -> "ForgePage":
        self.page.locator(f'[data-testid="forge-hunter-option"][data-player="{player}"]').click()
        return self

    @allure.step("Создать «{name}»")
    def create(self, name: str) -> None:
        self.item(name).get_by_test_id("forge-item-create").click()
        expect(self.by_test_id("forge-confirm")).to_be_visible()
        self.click("forge-confirm-ok")
        expect(self.by_test_id("forge-confirm")).to_be_hidden()
