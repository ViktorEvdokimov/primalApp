import allure
from playwright.sync_api import expect

from pages.base_page import BasePage


class LoginPage(BasePage):
    """Вход и регистрация по номеру телефона и паролю: номер — логин (задачи 8.1, 8.2)."""

    path = "/login"
    root_test_id = "page-login"

    PHONE = "login-phone"
    PASSWORD = "login-password"
    SUBMIT = "login-submit"
    ERROR = "login-error"

    @allure.step("Войти по номеру {phone}")
    def login(self, phone: str, password: str) -> None:
        self.by_test_id(self.PHONE).fill(phone)
        self.by_test_id(self.PASSWORD).fill(password)
        self.click(self.SUBMIT)

    @allure.step("Перейти к регистрации")
    def to_register(self) -> "LoginPage":
        self.by_test_id("login-mode").get_by_text("Регистрация", exact=True).click()
        expect(self.by_test_id("register-phone")).to_be_visible()
        return self

    @allure.step("Зарегистрироваться по номеру {phone}")
    def register(self, phone: str, password: str, name: str, repeat: str | None = None) -> None:
        """`repeat` — другой повтор пароля, чтобы проверить несовпадение."""
        self.by_test_id("register-phone").fill(phone)
        self.by_test_id("register-password").fill(password)
        self.by_test_id("register-password-repeat").fill(password if repeat is None else repeat)
        self.by_test_id("register-name").fill(name)
        self.click("register-submit")

    def error_text(self) -> str:
        expect(self.by_test_id(self.ERROR)).to_be_visible()
        return self.by_test_id(self.ERROR).inner_text()


class SettingsPage(BasePage):
    """Настройки: имя, номер телефона (логин), пароль, устройства, выход."""

    path = "/settings"
    root_test_id = "page-settings"

    def device_count(self) -> int:
        return self.by_test_id("settings-device").count()

    def account_text(self) -> str:
        return self.by_test_id("settings-account").inner_text()

    def phone(self) -> str:
        return self.by_test_id("settings-phone").input_value()

    @allure.step("Сохранить номер «{phone}»")
    def save_phone(self, phone: str) -> None:
        self.by_test_id("settings-phone").fill(phone)
        self.click("settings-phone-save")
        expect(self.by_test_id("settings-notice")).to_be_visible()

    @allure.step("Сменить пароль")
    def change_password(self, current: str, new: str) -> None:
        self.by_test_id("settings-password-current").fill(current)
        self.by_test_id("settings-password-new").fill(new)
        self.by_test_id("settings-password-repeat").fill(new)
        self.click("settings-password-save")

    def notice(self) -> str:
        expect(self.by_test_id("settings-notice")).to_be_visible()
        return self.by_test_id("settings-notice").inner_text()

    @allure.step("Отозвать текущее устройство")
    def revoke_current_device(self) -> None:
        current = self.page.locator("[data-testid='settings-device'][data-current='true']")
        current.get_by_test_id("settings-device-revoke").click()

    @allure.step("Выйти")
    def logout(self) -> None:
        self.click("settings-logout")
