import allure
from playwright.sync_api import expect

from pages.base_page import BasePage


class ShareDialog(BasePage):
    """Окно «Поделиться» на листе кампании (только владельцу)."""

    root_test_id = "share-dialog"

    @allure.step("Открыть «Поделиться»")
    def open_from_sheet(self) -> "ShareDialog":
        self.click("sheet-share")
        self.should_be_open()
        return self

    @allure.step("Создать ссылку")
    def create(self) -> str:
        self.click("share-create")
        return self.url()

    def url(self) -> str:
        field = self.by_test_id("share-url")
        expect(field).to_be_visible()
        return field.input_value()

    def participants(self) -> list[str]:
        return [item.inner_text() for item in self.by_test_id("share-participant").all()]

    @allure.step("Отозвать ссылку")
    def revoke(self) -> None:
        self.click("share-revoke")
        self.click("share-confirm-ok")
        expect(self.by_test_id("share-no-link")).to_be_visible()

    @allure.step("Закрыть окно «Поделиться»")
    def close(self) -> None:
        self.page.keyboard.press("Escape")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()
