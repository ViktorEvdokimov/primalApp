import allure
from playwright.sync_api import Locator, Page, expect

from pages.base_page import BasePage


class AdminPage(BasePage):
    """Администрирование (задачи 9.5–9.6): награды заданий и глав, цены кузни и лаборатории, монстры."""

    root_test_id = "page-admin"
    path = "/admin"

    def should_be_open(self) -> None:
        expect(self.by_test_id("admin-quest-form")).to_be_visible(timeout=10000)

    def should_show_stats(self) -> None:
        """Раздел по умолчанию — «Статистика»."""
        expect(self.by_test_id("admin-stats")).to_be_visible(timeout=10000)

    def stats_row(self, test_id: str) -> list:
        """Строка таблицы: [подпись, всего, за 30 дней, за 7 дней]."""
        cells = [cell.inner_text() for cell in self.by_test_id(test_id).locator("td").all()]
        return [cells[0], *[int(value) for value in cells[1:]]]

    def section(self, test_id: str) -> Locator:
        """Список эффектов: admin-victory, admin-expired, admin-chapter-effects."""
        return self.by_test_id(test_id)

    def preview(self) -> list[str]:
        return [line.inner_text().lstrip("• ") for line in self.by_test_id("admin-preview-line").all()]

    @allure.step("Раздел «{name}»")
    def open_section(self, name: str) -> "AdminPage":
        self.by_test_id("admin-mode").get_by_text(name, exact=True).click()
        return self

    @allure.step("Выбрать задание {number}")
    def select_quest(self, number: int, label: str) -> "AdminPage":
        self.by_test_id("admin-quest").click()
        self.page.get_by_role("option", name=label).click()
        expect(self.page.locator(f'[data-testid="admin-quest-form"][data-number="{number}"]')).to_be_visible()
        return self

    @allure.step("Добавить сообщение «{text}»")
    def add_message(self, section: str, text: str) -> "AdminPage":
        self.section(section).get_by_test_id("effect-add").last.click()
        self.by_test_id("effect-add-message").click()
        self.section(section).get_by_test_id("effect-message").last.fill(text)
        return self

    @allure.step("Сохранить награды")
    def save(self) -> None:
        self.click("admin-save")
        expect(self.page.get_by_text("Награды сохранены — действуют для всех кампаний.")).to_be_visible()

    @allure.step("Вернуть исходные награды")
    def reset(self) -> None:
        self.click("admin-reset")
        self.click("admin-reset-confirm")
        expect(self.page.get_by_text("Возвращены исходные награды.")).to_be_visible()
