"""Администрирование (задача 9.5): роль назначается скриптом deploy/grant-admin.sh, остальным раздел не виден."""

import shutil
import subprocess
from pathlib import Path

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.admin_page import AdminPage
from tests.test_login import register

WEB_DIR = Path(__file__).resolve().parents[2]


def grant_admin(login: str) -> None:
    """Тот же скрипт, что на сервере (doc/setup.md §3.13): тест работает с локальным стеком."""
    # shutil.which: в Windows просто "bash" запустил бы bash из WSL, а не Git Bash
    bash = shutil.which("bash") or "bash"
    subprocess.run([bash, "deploy/grant-admin.sh", login], cwd=WEB_DIR, check=True, capture_output=True)


def reset(page: Page, path: str) -> None:
    """Вернуть исходное через API — каталог общий для всех тестов (path: quests/1, forge/FIRE_01…)."""
    token = next((c["value"] for c in page.context.cookies() if c["name"] == "XSRF-TOKEN"), "")
    page.request.delete(f"/api/v1/admin/{path}", headers={"X-XSRF-TOKEN": token})


def open_admin(page: Page) -> AdminPage:
    """Новый игрок, назначенный администратором скриптом; открыт раздел «Администрирование»."""
    login = register(page)
    grant_admin(login)
    page.reload()
    page.get_by_test_id("menu-admin").click()
    admin = AdminPage(page)
    admin.should_show_stats()
    admin.open_section("Задания")
    admin.should_be_open()
    return admin


@allure.feature("Администрирование")
class TestAdmin:

    @allure.title("Обычному игроку: нет пункта меню, /admin — «Страница не найдена»")
    def test_hidden_for_players(self, page: Page):
        # подготовка
        register(page)

        # проверка
        with check("В меню нет «Администрирование»"):
            expect(page.get_by_test_id("menu-admin")).to_have_count(0)
        page.goto("/admin")
        with check("/admin как несуществующая страница"):
            expect(page.get_by_test_id("page-not-found")).to_be_visible()
        with check("API отвечает 404"):
            assert page.request.get("/api/v1/admin/catalog").status == 404

    @allure.title("Администратор: правка последствий задания 1 видна игрокам; «Вернуть исходные»")
    def test_edit_quest(self, page: Page):
        # подготовка
        admin = open_admin(page)

        try:
            # вызов
            admin.add_message("admin-expired", "Проверка E2E").save()

            # проверка
            with check("Текст для игроков обновился"):
                expect(page.get_by_test_id("admin-preview-line").filter(has_text="Проверка E2E")).to_be_visible()
            with check("Отметка «изменено»"):
                expect(page.get_by_test_id("admin-edited").first).to_be_visible()
            with check("Каталог для всех — с правкой"):
                expired = page.request.get("/api/v1/catalog/quests/1").json()["expired"]
                assert "Проверка E2E" in expired["messages"]

            # вызов
            admin.reset()

            # проверка
            with check("Снова из YAML"):
                expect(page.get_by_test_id("admin-edited")).to_have_count(0)
                assert page.request.get("/api/v1/catalog/quests/1").json()["expired"]["messages"] == []
        finally:
            reset(page, "quests/1")

    @allure.title("Администратор: цена «Языка пламени» — 2 златии на 1-м уровне; видна в справочнике кузни")
    def test_edit_forge_price(self, page: Page):
        # подготовка
        admin = open_admin(page)

        try:
            # вызов
            admin.open_section("Кузница")
            item = page.locator('[data-testid="admin-forge-item"][data-code="FIRE_01"]')
            level = item.locator('[data-testid="admin-forge-level"][data-level="1"]')
            level.get_by_role("button", name="Убрать").first.click()
            level.get_by_role("button", name="Убрать").first.click()
            level.get_by_test_id("admin-forge-add").click()
            level.get_by_test_id("admin-forge-material").click()
            page.get_by_role("option", name="Златия").click()
            level.get_by_test_id("admin-forge-quantity").fill("2")
            item.get_by_test_id("admin-save").click()

            # проверка
            expect(page.get_by_text("Награды сохранены — действуют для всех кампаний.")).to_be_visible()
            with check("Справочник кузни — новая цена"):
                forge = page.request.get("/api/v1/catalog/forge").json()
                assert forge[0]["items"][0]["costs"][0]["materials"] == {"ZLATIA": 2}
        finally:
            reset(page, "forge/FIRE_01")

    @allure.title("Администратор: стойки монстра — прочность первой стойки Коровона на 0-м уровне")
    def test_edit_boss(self, page: Page):
        # подготовка
        admin = open_admin(page)

        try:
            # вызов
            admin.open_section("Монстры")
            level = page.locator('[data-testid="admin-boss-level"][data-level="0"]')
            level.get_by_test_id("admin-stance-toughness").first.fill("7")
            page.get_by_test_id("admin-boss-form").get_by_test_id("admin-save").click()

            # проверка
            expect(page.get_by_text("Награды сохранены — действуют для всех кампаний.")).to_be_visible()
            with check("Справочник боссов — новая прочность"):
                bosses = page.request.get("/api/v1/catalog/bosses").json()
                korovon = next(boss for boss in bosses if boss["code"] == "KOROVON")
                assert korovon["difficulties"]["0"][0]["toughnessPerHunter"] == 7
        finally:
            reset(page, "bosses/KOROVON")


    @allure.title("Администратор: статистика — новая учётная запись и кампания учтены")
    def test_stats(self, page: Page):
        # подготовка
        login = register(page)
        grant_admin(login)
        page.request.post("/api/v1/campaigns", data={"name": "Статистика", "hunters": [{"class": "DAREON"}, {"class": "MIRA"}]},
                          headers={"X-XSRF-TOKEN": next((c["value"] for c in page.context.cookies() if c["name"] == "XSRF-TOKEN"), "")})

        # вызов
        page.reload()
        page.get_by_test_id("menu-admin").click()
        admin = AdminPage(page)

        # проверка
        admin.should_show_stats()
        with check("Учётные записи и кампании за 7 дней — не меньше одной"):
            assert admin.stats_row("admin-stats-row-accounts")[3] >= 1
            assert admin.stats_row("admin-stats-row-campaigns")[3] >= 1


@allure.feature("Дополнения")
class TestExpansions:

    @allure.title("Настройки: убрать «Перо» — сохраняется в аккаунте")
    def test_choose_expansions(self, page: Page):
        # подготовка
        register(page)
        page.get_by_test_id("menu-settings").click()
        feather = page.locator('[data-testid="settings-expansion"][data-expansion="FEATHER"]')

        # проверка: по умолчанию все
        expect(page.get_by_test_id("settings-expansion")).to_have_count(4)
        expect(feather).to_be_checked()

        # вызов
        feather.click()

        # проверка
        expect(page.get_by_text("Дополнения сохранены.")).to_be_visible()
        page.reload()
        expect(feather).not_to_be_checked()
        with check("«Кто я» — без «Пера»"):
            assert page.request.get("/api/v1/auth/me").json()["user"]["expansions"] == ["NIGHTMARE", "POISON", "ICE"]
