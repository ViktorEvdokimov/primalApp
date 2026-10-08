"""«Инфо» (задача 9.8): ключевые слова, символы реакций, жетоны окружения; правка администратором."""

import re

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.admin_page import AdminPage
from pages.expedition_page import ExpeditionPage
from tests.test_admin import grant_admin, reset
from tests.test_login import register

# Текст статей — свой, в разметке статей из правил
RULES = """# Правила
##### Ключевые слова
**Тестовая защита**
Жетон снимает одну единицу урона.
**Примечание.** Жетоны складываются.

**Тестовая выносливость**
Сбросьте карту.
(См. также **«Тестовая защита»** .)
#### Алфавитный указатель
"""


def xsrf(page: Page) -> dict:
    return {"X-XSRF-TOKEN": next((c["value"] for c in page.context.cookies() if c["name"] == "XSRF-TOKEN"), "")}


def delete_test_entries(page: Page) -> None:
    """Каталог «Инфо» общий для всех тестов — удалить свои статьи."""
    for section in page.request.get("/api/v1/info").json()["sections"]:
        for entry in section["entries"]:
            if (entry["title"] or entry["body"]).startswith("Тестов"):
                page.request.delete(f"/api/v1/admin/info/entries/{entry['id']}", headers=xsrf(page))


@allure.feature("Инфо")
class TestInfo:

    @allure.title("Из боя экспедиции без входа: «Инфо» — три раздела, «Назад» возвращает в бой")
    def test_from_battle(self, page: Page):
        # подготовка
        expedition = ExpeditionPage(page).open()
        expedition.should_be_open()
        expedition.select_boss("Огонь - Вираксен")
        expedition.start_battle()

        # вызов
        page.get_by_test_id("battle-info").click()

        # проверка
        sections = page.get_by_test_id("info-section")
        expect(sections).to_have_count(3)
        with check("Разделы по порядку"):
            assert [s.get_attribute("data-section") for s in sections.all()] == ["KEYWORDS", "REACTIONS", "TOKENS"]
        page.get_by_test_id("info-back").click()
        expect(page).to_have_url(re.compile(r"/battle$"))

    @allure.title("Администратор импортирует ключевые слова из файла; ссылка «См. также» открывает статью; поиск")
    def test_import_and_links(self, page: Page):
        # подготовка
        login = register(page)
        grant_admin(login)
        page.reload()
        page.get_by_test_id("menu-admin").click()
        admin = AdminPage(page)
        admin.should_show_stats()

        try:
            # вызов: импорт
            admin.open_section("Инфо")
            page.locator('input[type="file"][accept^=".md"]').set_input_files(
                files=[{"name": "rules.md", "mimeType": "text/markdown", "buffer": RULES.encode("utf-8")}])

            # проверка
            expect(page.get_by_test_id("admin-info-import-result")).to_contain_text("добавлено: 2")

            # вызов: поиск и ссылка
            page.goto("/info")
            page.get_by_test_id("info-search").fill("сбросьте карту")
            # в базе могут быть и настоящие ключевые слова — нажимаем свою статью
            page.get_by_test_id("info-result").filter(has_text="Тестовая выносливость").click()
            stamina = page.locator('[data-testid="info-entry"]', has_text="Тестовая выносливость")
            stamina.get_by_test_id("info-link").click()

            # проверка
            expect(page).to_have_url(re.compile(r"/info/keywords#entry-\d+$"))
            protection = page.locator('[data-testid="info-entry"]', has_text="Тестовая защита")
            with check("Статья по ссылке раскрыта: виден жирный «Примечание.»"):
                expect(protection.get_by_text("Жетон снимает одну единицу урона.")).to_be_visible()
        finally:
            delete_test_entries(page)

    @allure.title("Администратор: статья жетона с картинкой; на сайте — карточка с картинкой без раскрытия")
    def test_entry_with_image(self, page: Page):
        # подготовка
        login = register(page)
        grant_admin(login)
        page.reload()
        page.get_by_test_id("menu-admin").click()
        admin = AdminPage(page)
        admin.should_show_stats()
        png = bytes.fromhex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d4944415478da63f8cfc0f01f0005000"
            "1ff89993d1d0000000049454e44ae426082")

        try:
            # вызов
            admin.open_section("Инфо")
            page.get_by_test_id("admin-info-section").get_by_text("Жетоны окружения").click()
            page.get_by_test_id("admin-info-new").click()
            page.get_by_test_id("admin-info-title").fill("Тестовый камень")
            page.get_by_test_id("admin-info-target").select_option("TOKENS")
            page.get_by_test_id("admin-info-body").fill("Препятствие на поле.")
            page.get_by_test_id("admin-info-save").click()
            expect(page.get_by_text("Статья сохранена.")).to_be_visible()
            page.locator('input[type="file"][accept^="image"]').set_input_files(
                files=[{"name": "stone.png", "mimeType": "image/png", "buffer": png}])
            expect(page.get_by_test_id("admin-info-image")).to_be_visible()

            # проверка
            page.goto("/info/tokens")
            entry = page.locator('[data-testid="info-entry"]', has_text="Тестовый камень")
            # жетоны — карточки: картинка и название видны без раскрытия (qa № 144)
            expect(entry.get_by_test_id("info-entry-image")).to_be_visible()
            expect(entry.get_by_test_id("info-card-title")).to_have_text("Тестовый камень")
            with check("Картинка отдаётся сервером"):
                src = entry.get_by_test_id("info-entry-image").get_attribute("src")
                assert page.request.get(src).headers["content-type"] == "image/png"
        finally:
            delete_test_entries(page)

    @allure.title("Символ реакции: без названия — картинка и описание; на сайте — карточка")
    def test_reaction_without_title(self, page: Page):
        # подготовка
        login = register(page)
        grant_admin(login)
        page.reload()
        page.get_by_test_id("menu-admin").click()
        admin = AdminPage(page)
        admin.should_show_stats()

        try:
            # вызов
            admin.open_section("Инфо")
            page.get_by_test_id("admin-info-section").get_by_text("Символы реакций монстров").click()
            page.get_by_test_id("admin-info-new").click()
            with check("Поля «Название» нет"):
                expect(page.get_by_test_id("admin-info-title")).to_have_count(0)
            page.get_by_test_id("admin-info-body").fill("Тестовая реакция: монстр разворачивается.")
            page.get_by_test_id("admin-info-save").click()
            expect(page.get_by_text("Статья сохранена.")).to_be_visible()

            # проверка
            page.goto("/info/reactions")
            card = page.locator('[data-testid="info-entry"]', has_text="Тестовая реакция")
            expect(card).to_be_visible()
            with check("Без заголовка-раскрывашки"):
                expect(card.get_by_test_id("info-entry-title")).to_have_count(0)
        finally:
            delete_test_entries(page)

