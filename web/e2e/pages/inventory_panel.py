import allure
from playwright.sync_api import Locator, Page, expect

from pages.base_page import BasePage


class InventoryPanel(BasePage):
    """Инвентарь выбранного охотника на листе кампании (задача 9.3): снаряжение, зелья, карты наград."""

    root_test_id = "inventory"

    def __init__(self, page: Page):
        super().__init__(page)

    def item(self, name: str) -> Locator:
        return self.page.locator(f'[data-testid="inventory-item"][data-name="{name}"]')

    def names(self, kind: str | None = None) -> list[str]:
        """Названия предметов; `kind` — EQUIPMENT, POTION или REWARD."""
        scope = self.page if kind is None else self.page.locator(f'[data-testid="inventory-group"][data-kind="{kind}"]')
        return [item.get_attribute("data-name") or "" for item in scope.get_by_test_id("inventory-item").all()]

    @allure.step("Дождаться предмета «{name}» в инвентаре")
    def wait_item(self, name: str) -> None:
        expect(self.item(name)).to_be_visible()

    def level(self, name: str) -> int | None:
        badge = self.item(name).get_by_test_id("inventory-item-level")
        return int(badge.get_attribute("data-level") or "0") if badge.count() > 0 else None

    @allure.step("Добавить в инвентарь «{name}»")
    def add(self, name: str, kind: str = "EQUIPMENT", level: int = 1) -> None:
        self.click("inventory-add")
        form = self.by_test_id("inventory-form")
        form.get_by_test_id("inventory-form-kind").select_option(kind)
        form.get_by_test_id("inventory-form-name").fill(name)
        if kind != "REWARD":
            form.get_by_test_id("inventory-form-level").select_option(str(level))
        form.get_by_test_id("inventory-form-save").click()
        expect(form).to_be_hidden()
        self.wait_item(name)

    @allure.step("Изменить уровень «{name}» на {level}")
    def set_level(self, name: str, level: int) -> None:
        self.item(name).get_by_test_id("inventory-item-edit").click()
        form = self.by_test_id("inventory-form")
        form.get_by_test_id("inventory-form-level").select_option(str(level))
        form.get_by_test_id("inventory-form-save").click()
        expect(form).to_be_hidden()
        expect(self.item(name).get_by_test_id("inventory-item-level")).to_have_attribute("data-level", str(level))

    @allure.step("Убрать из инвентаря «{name}»")
    def remove(self, name: str) -> None:
        self.item(name).first.get_by_test_id("inventory-item-remove").click()
        expect(self.item(name)).to_have_count(0)


class ExchangeDialog(BasePage):
    """«Обменять ресурсы»: обмен с охотником, преобразование или продажа предмета."""

    root_test_id = "exchange-dialog"

    def __init__(self, page: Page):
        super().__init__(page)
        self.root = self.by_test_id(self.root_test_id)

    def lacking(self) -> str:
        return self.root.get_by_test_id("exchange-lacking").inner_text()

    def mode(self, label: str) -> "ExchangeDialog":
        self.root.get_by_test_id("exchange-mode").get_by_text(label, exact=True).click()
        return self

    def want(self) -> str:
        return self.root.get_by_test_id("exchange-want").input_value()

    def options(self, test_id: str) -> list[str]:
        return [option.inner_text() for option in self.root.get_by_test_id(test_id).locator("option").all()]

    @allure.step("Обмен: получить {want}, взамен {offer}, с охотником {partner}")
    def trade(self, want: str | None = None, offer: str | None = None, partner: str | None = None) -> "ExchangeDialog":
        """Коды ресурсов; `partner` — имя охотника. Не указано — остаётся выбор по умолчанию."""
        if want is not None:
            self.root.get_by_test_id("exchange-want").select_option(want)
        if offer is not None:
            self.root.get_by_test_id("exchange-offer").select_option(offer)
        if partner is not None:
            select = self.root.get_by_test_id("exchange-partner")
            select.select_option(label=next(o for o in self.options("exchange-partner") if o.startswith(partner)))
        return self

    def gain(self, code: str) -> "ExchangeDialog":
        self.root.get_by_test_id("exchange-gain").select_option(code)
        return self

    def gain_value(self) -> str:
        return self.root.get_by_test_id("exchange-gain").input_value()

    def card_options(self) -> list[str]:
        return [option.inner_text() for option in self.root.get_by_test_id("exchange-card").locator("option").all()]

    def can_submit(self) -> bool:
        return self.root.get_by_test_id("exchange-submit").is_enabled()

    @allure.step("Обменять ресурсы: подтвердить")
    def submit(self) -> None:
        self.root.get_by_test_id("exchange-submit").click()
        expect(self.root).to_be_hidden()
