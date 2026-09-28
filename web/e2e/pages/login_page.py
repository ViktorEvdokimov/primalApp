import allure
from playwright.sync_api import expect

from pages.base_page import BasePage


class LoginPage(BasePage):
    """Вход по коду из письма: почта → код → имя (для нового пользователя)."""

    path = "/login"
    root_test_id = "page-login"

    EMAIL = "login-email"
    GET_CODE = "login-get-code"
    CODE_SENT = "login-code-sent"
    NAME = "login-name"

    @allure.step("Запросить код на {email}")
    def request_code(self, email: str) -> None:
        self.by_test_id(self.EMAIL).fill(email)
        self.click(self.GET_CODE)
        expect(self.by_test_id(self.CODE_SENT)).to_be_visible()

    @allure.step("Ввести код {code}")
    def enter_code(self, code: str) -> None:
        """Ячейки кода сами переводят фокус; 6-я цифра отправляет код."""
        self.by_test_id("login-code-0").click()
        self.page.keyboard.type(code)

    @allure.step("Указать имя «{name}»")
    def set_name(self, name: str) -> None:
        self.by_test_id(self.NAME).fill(name)
        self.click("login-name-save")

    @allure.step("Пропустить ввод имени")
    def skip_name(self) -> None:
        self.click("login-name-skip")

    def is_name_step(self) -> bool:
        try:
            expect(self.by_test_id(self.NAME)).to_be_visible(timeout=5000)
            return True
        except AssertionError:
            return False


class SettingsPage(BasePage):
    """Настройки: имя, устройства, выход."""

    path = "/settings"
    root_test_id = "page-settings"

    def device_count(self) -> int:
        return self.by_test_id("settings-device").count()

    @allure.step("Отозвать текущее устройство")
    def revoke_current_device(self) -> None:
        current = self.page.locator("[data-testid='settings-device'][data-current='true']")
        current.get_by_test_id("settings-device-revoke").click()

    @allure.step("Выйти")
    def logout(self) -> None:
        self.click("settings-logout")
