import allure
from playwright.sync_api import Locator, Page, expect

from pages.base_page import BasePage


class EquipmentPage(BasePage):
    """«Инфо» → «Экипировка» (qa № 154): карты снаряжения и зелий; фильтры по типу, классу и уровню."""

    path = "/info/equipment"
    root_test_id = "page-info"

    def __init__(self, page: Page):
        super().__init__(page)

    def should_be_open(self) -> None:
        expect(self.by_test_id("equipment-tab").first).to_be_visible(timeout=10000)

    @allure.step("Вкладка {tab}")
    def select_tab(self, tab: str) -> "EquipmentPage":
        self.page.locator(f'[data-testid="equipment-tab"][data-tab="{tab}"]').click()
        return self

    @allure.step("Тип {item_type}")
    def filter_type(self, item_type: str) -> "EquipmentPage":
        self.page.locator(f'[data-testid="equipment-type-option"][data-type="{item_type}"]').click()
        return self

    @allure.step("Класс {hunter_class}")
    def filter_class(self, hunter_class: str) -> "EquipmentPage":
        self.page.locator(f'[data-testid="equipment-class-option"][data-class="{hunter_class}"]').click()
        return self

    @allure.step("Уровень {level}")
    def filter_level(self, level: int) -> "EquipmentPage":
        self.page.locator(f'[data-testid="equipment-level-option"][data-level="{level}"]').click()
        return self

    def items(self) -> Locator:
        return self.by_test_id("equipment-item")

    def names(self) -> list[str]:
        expect(self.items().first).to_be_visible()
        return [item.get_attribute("data-name") or "" for item in self.items().all()]

    def image(self, name: str) -> Locator:
        return self.page.locator(f'[data-testid="equipment-item"][data-name="{name}"]').get_by_test_id("equipment-image")
