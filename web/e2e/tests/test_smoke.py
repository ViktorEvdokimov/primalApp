import allure
from playwright.sync_api import Page, expect

from pages.main_page import MainPage


@allure.feature("Запуск сайта")
class TestSmoke:

    @allure.title("Главная страница открывается и предлагает экспедицию и кампании")
    def test_main_page_opens(self, page: Page):
        """Сайт отдаётся через Caddy, SPA загружается, в меню есть экспедиция и кампании."""
        # подготовка
        main_page = MainPage(page)

        # вызов
        main_page.open()

        # проверка
        main_page.should_be_open()
        expect(main_page.by_test_id(MainPage.EXPEDITION)).to_be_visible()
        expect(main_page.by_test_id(MainPage.CAMPAIGNS)).to_be_visible()

    @allure.title("Прямой адрес экрана открывается без перехода с главной")
    def test_deep_link_opens(self, page: Page):
        """Caddy отдаёт index.html на любой адрес SPA, роутер показывает нужный экран."""
        # вызов
        page.goto("/expedition/new")

        # проверка
        expect(page.get_by_test_id("page-expedition-new")).to_be_visible()

    @allure.title("Сервер здоров: /api/actuator/health через Caddy отвечает UP")
    def test_backend_health(self, api):
        """Запросы /api/* проходят через Caddy в бэкенд, бэкенд подключён к БД."""
        # вызов
        response = api.get("/api/actuator/health")

        # проверка
        assert response.status_code == 200
        assert response.json()["status"] == "UP"

    @allure.title("Заголовки безопасности и номер запроса")
    def test_security_headers(self, api):
        """Caddy добавляет заголовки безопасности, бэкенд — X-Request-Id (задача 7.3)."""
        # вызов
        page = api.get("/")
        health = api.get("/api/actuator/health")

        # проверка
        assert page.headers["x-content-type-options"] == "nosniff"
        assert page.headers["referrer-policy"] == "no-referrer"
        assert "frame-ancestors 'none'" in page.headers["content-security-policy"]
        assert page.headers["strict-transport-security"].startswith("max-age=")
        assert "server" not in page.headers
        assert len(health.headers["x-request-id"]) > 0
