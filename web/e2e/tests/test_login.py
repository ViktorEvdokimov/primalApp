"""Регистрация и вход по номеру телефона и паролю, запоминание браузера и защищённые экраны (задачи 3.3, 8.1, 8.2)."""

import random

import allure
from playwright.sync_api import Browser, Page, expect
from pytest_check import check

from pages.expedition_page import ExpeditionPage
from pages.login_page import LoginPage, SettingsPage
from pages.main_page import MainPage

PASSWORD = "e2e-password"


def unique_phone() -> str:
    """Свой номер у каждого теста: номера уникальны, лимит попыток на номер не мешает."""
    return f"+79{random.randrange(10**9):09d}"


def register(page: Page, phone: str | None = None, name: str = "Охотник", password: str = PASSWORD) -> str:
    """Регистрация через интерфейс; после неё открывается главное меню. Возвращает номер (+7…)."""
    phone = phone or unique_phone()
    LoginPage(page).open().to_register().register(phone, password, name=name)
    MainPage(page).should_be_open()
    return phone


def spaced(phone: str) -> str:
    """Тот же номер в привычном написании: 8 912 345-67-89."""
    digits = phone.removeprefix("+7")
    return f"8 {digits[:3]} {digits[3:6]}-{digits[6:8]}-{digits[8:]}"


@allure.feature("Регистрация и вход")
class TestLogin:

    @allure.title("Регистрация: имя в меню, «Настройки» вместо «Войти», номер в настройках")
    def test_register(self, page: Page):
        # вызов
        phone = register(page, name="Алиса")

        # проверка
        main_page = MainPage(page)
        with check("В меню — имя и «Настройки», кнопки «Войти» нет"):
            expect(main_page.by_test_id("menu-user")).to_have_text("Алиса")
            expect(main_page.by_test_id("menu-settings")).to_be_visible()
            expect(main_page.by_test_id("menu-login")).to_be_hidden()
        settings = SettingsPage(page).open()
        with check("В настройках — номер, по которому выполнен вход"):
            assert settings.account_text() == f"Вы вошли по номеру {phone}"
            assert settings.phone() == phone

    @allure.title("Вход в другом браузере по номеру в любом написании и паролю")
    def test_login_in_other_browser(self, page: Page, browser: Browser, base_url: str):
        # подготовка
        phone = register(page, name="Борис")
        other = browser.new_context(base_url=base_url, locale="ru-RU")
        other_page = other.new_page()

        # вызов
        LoginPage(other_page).open().login(spaced(phone), PASSWORD)

        # проверка
        with check("Вход выполнен: в меню имя пользователя"):
            MainPage(other_page).should_be_open()
            expect(other_page.get_by_test_id("menu-user")).to_have_text("Борис")
        other_page.goto("/settings")
        with check("У аккаунта два устройства"):
            expect(other_page.get_by_test_id("settings-device")).to_have_count(2)
        other.close()

    @allure.title("Неверный пароль — сообщение, вход не выполнен")
    def test_wrong_password(self, page: Page, browser: Browser, base_url: str):
        # подготовка
        phone = register(page)
        other = browser.new_context(base_url=base_url, locale="ru-RU")
        login_page = LoginPage(other.new_page()).open()

        # вызов
        login_page.login(phone, "not-the-password")

        # проверка
        with check("Сообщение о неверном номере или пароле"):
            assert login_page.error_text() == "Неверный номер телефона или пароль."
            login_page.should_be_open()
        other.close()

    @allure.title("Номер уже зарегистрирован и несовпадающие пароли — сообщения у полей")
    def test_register_errors(self, page: Page, browser: Browser, base_url: str):
        # подготовка
        phone = register(page)
        other = browser.new_context(base_url=base_url, locale="ru-RU")
        other_page = other.new_page()
        login_page = LoginPage(other_page).open().to_register()

        # вызов: пароли не совпадают
        login_page.register(unique_phone(), PASSWORD, name="Вера", repeat=PASSWORD + "!")

        # проверка
        with check("Пароли не совпадают"):
            expect(other_page.get_by_text("Пароли не совпадают.")).to_be_visible()

        # вызов: номер уже зарегистрирован (в другом написании)
        login_page.register(spaced(phone), PASSWORD, name="Вера")

        # проверка
        with check("Номер занят — сообщение у поля"):
            expect(other_page.get_by_text(f"Номер {phone} уже зарегистрирован. Войдите по нему.")).to_be_visible()
            login_page.should_be_open()
        other.close()

    @allure.title("Смена номера и пароля: вход только по новым")
    def test_change_phone_and_password(self, page: Page, browser: Browser, base_url: str):
        # подготовка
        old_phone = register(page)
        new_phone = unique_phone()
        settings = SettingsPage(page).open()

        # вызов
        settings.save_phone(new_phone)
        settings.change_password(PASSWORD, "brand-new-password")

        # проверка
        with check("Сообщение «Пароль изменён.» после «Номер сохранён»"):
            expect(page.get_by_test_id("settings-notice")).to_have_text("Пароль изменён.")
        other = browser.new_context(base_url=base_url, locale="ru-RU")
        login_page = LoginPage(other.new_page()).open()
        login_page.login(old_phone, "brand-new-password")
        with check("Старый номер больше не логин"):
            assert login_page.error_text() == "Неверный номер телефона или пароль."
        login_page.login(new_phone, PASSWORD)
        with check("Старый пароль не подходит"):
            assert login_page.error_text() == "Неверный номер телефона или пароль."
        login_page.login(new_phone, "brand-new-password")
        with check("Новые номер и пароль подходят"):
            MainPage(login_page.page).should_be_open()
        other.close()

    @allure.title("После перезапуска браузера вход не требуется")
    def test_login_survives_browser_restart(self, browser: Browser, base_url: str):
        # подготовка: регистрация и сохранённые cookie браузера
        context = browser.new_context(base_url=base_url, locale="ru-RU")
        register(context.new_page())
        state = context.storage_state()
        context.close()

        # вызов: «новый запуск» браузера с теми же cookie
        restarted = browser.new_context(base_url=base_url, locale="ru-RU", storage_state=state)
        page = restarted.new_page()
        page.goto("/settings")

        # проверка
        with check("Настройки открываются без входа"):
            SettingsPage(page).should_be_open()
        restarted.close()


@allure.feature("Доступ без входа")
class TestAccess:

    @allure.title("Кампании без входа ведут на вход, экспедиция доступна")
    def test_campaigns_require_login(self, page: Page):
        # вызов
        MainPage(page).open().click(MainPage.CAMPAIGNS)

        # проверка
        with check("Открылся вход с возвратом к кампаниям"):
            LoginPage(page).should_be_open()
            assert "next=%2Fcampaigns" in page.url
        page.goto("/expedition/new")
        with check("Экспедиция открывается без входа"):
            ExpeditionPage(page).should_be_open()

    @allure.title("Отзыв текущего устройства открывает вход; экспедиция по-прежнему доступна")
    def test_revoke_current_device(self, page: Page):
        # подготовка
        register(page)
        settings = SettingsPage(page).open()
        expect(page.get_by_test_id("settings-device")).to_have_count(1)

        # вызов
        settings.revoke_current_device()

        # проверка
        with check("После отзыва — экран входа"):
            LoginPage(page).should_be_open()
        page.goto("/settings")
        with check("Настройки снова требуют вход"):
            LoginPage(page).should_be_open()
        battle = MainPage(page).open().select_expedition().start_battle()
        with check("Экспедиция начинается без входа"):
            assert battle.get_health_value() == 10

    @allure.title("«Выйти» — меню снова предлагает «Войти»")
    def test_logout(self, page: Page):
        # подготовка
        register(page)

        # вызов
        SettingsPage(page).open().logout()

        # проверка
        with check("В меню — «Войти»"):
            expect(page.get_by_test_id("menu-login")).to_be_visible()
