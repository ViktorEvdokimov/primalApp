"""Вход по коду из письма, запоминание браузера и защищённые экраны (задача 3.3)."""

import uuid

import allure
from playwright.sync_api import Browser, Page, expect
from pytest_check import check

from pages.expedition_page import ExpeditionPage
from pages.login_page import LoginPage, SettingsPage
from pages.main_page import MainPage


def unique_email() -> str:
    """Свой адрес у каждого теста: письма в Mailpit не перепутаются, лимит на адрес не мешает."""
    return f"e2e-{uuid.uuid4().hex[:12]}@example.com"


def login(page: Page, mailpit, email: str, name: str | None = "Охотник") -> None:
    """Вход через интерфейс с кодом из Mailpit; новый пользователь указывает имя."""
    login_page = LoginPage(page).open()
    login_page.request_code(email)
    login_page.enter_code(mailpit.latest_login_code(email))
    assert login_page.is_name_step(), "Новый пользователь после кода указывает имя"
    if name is None:
        login_page.skip_name()
    else:
        login_page.set_name(name)
    MainPage(page).should_be_open()


@allure.feature("Вход по коду из письма")
class TestLogin:

    @allure.title("Вход с кодом из письма: новый пользователь указывает имя")
    def test_login_with_code_from_mailpit(self, page: Page, mailpit):
        # подготовка
        email = unique_email()

        # вызов
        login(page, mailpit, email, name="Алиса")

        # проверка
        main_page = MainPage(page)
        with check("В меню — имя и «Настройки», кнопки «Войти» нет"):
            expect(main_page.by_test_id("menu-user")).to_have_text("Алиса")
            expect(main_page.by_test_id("menu-settings")).to_be_visible()
            expect(main_page.by_test_id("menu-login")).to_be_hidden()

    @allure.title("Второй код на тот же адрес раньше чем через минуту — сообщение, сколько ждать")
    def test_code_rate_limit_message(self, browser: Browser, base_url: str, mailpit):
        # подготовка: первый код уже отправлен из другого браузера
        email = unique_email()
        first = browser.new_context(base_url=base_url, locale="ru-RU")
        LoginPage(first.new_page()).open().request_code(email)
        first.close()
        second = browser.new_context(base_url=base_url, locale="ru-RU")
        page = second.new_page()
        login_page = LoginPage(page).open()

        # вызов
        login_page.by_test_id(LoginPage.EMAIL).fill(email)
        login_page.click(LoginPage.GET_CODE)

        # проверка
        with check("Сообщение о лимите с числом секунд, шаг ввода кода не открылся"):
            expect(page.get_by_test_id("login-error")).to_contain_text("Слишком много попыток. Повторите через")
            expect(page.get_by_test_id(LoginPage.CODE_SENT)).to_be_hidden()
        second.close()

    @allure.title("После перезапуска браузера вход не требуется")
    def test_login_survives_browser_restart(self, browser: Browser, base_url: str, mailpit):
        # подготовка: вход и сохранённые cookie браузера
        context = browser.new_context(base_url=base_url, locale="ru-RU")
        login(context.new_page(), mailpit, unique_email())
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

    @allure.title("Неверный код — сообщение с числом попыток")
    def test_wrong_code(self, page: Page, mailpit):
        # подготовка
        email = unique_email()
        login_page = LoginPage(page).open()
        login_page.request_code(email)
        code = mailpit.latest_login_code(email)
        wrong = "000000" if code != "000000" else "111111"

        # вызов
        login_page.enter_code(wrong)

        # проверка
        with check("Сообщение о неверном коде и оставшихся попытках"):
            expect(page.get_by_test_id("login-error")).to_have_text("Неверный код. Осталось попыток: 4.")


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
    def test_revoke_current_device(self, page: Page, mailpit):
        # подготовка
        login(page, mailpit, unique_email())
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
    def test_logout(self, page: Page, mailpit):
        # подготовка
        login(page, mailpit, unique_email())

        # вызов
        SettingsPage(page).open().logout()

        # проверка
        with check("В меню — «Войти»"):
            expect(page.get_by_test_id("menu-login")).to_be_visible()
