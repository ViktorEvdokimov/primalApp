import allure
from playwright.sync_api import expect

from pages.base_page import BasePage


class CampaignsPage(BasePage):
    """Список кампаний."""

    path = "/campaigns"
    root_test_id = "page-campaigns"

    def item(self, name: str):
        return self.page.get_by_test_id("campaign-item").filter(has_text=name)

    def chapter_of(self, name: str) -> str:
        return self.item(name).get_by_test_id("campaign-item-chapter").text_content() or ""

    @allure.step("Открыть создание кампании")
    def create(self) -> "CampaignCreatePage":
        self.click("campaigns-create")
        page = CampaignCreatePage(self.page)
        page.should_be_open()
        return page

    @allure.step("«В главное меню»")
    def to_menu(self) -> None:
        self.click("campaigns-to-menu")

    @allure.step("Удалить кампанию «{name}»")
    def delete(self, name: str) -> None:
        self.item(name).get_by_test_id("campaign-item-delete").click()
        self.click("campaign-delete-confirm")
        expect(self.item(name)).to_have_count(0)


class CampaignCreatePage(BasePage):
    """Новая кампания: название и отряд."""

    path = "/campaigns/new"
    root_test_id = "page-campaign-new"

    @allure.step("Создать кампанию «{name}» с классами {classes}")
    def create(self, name: str, classes: list[str], player_names: dict[str, str] | None = None) -> None:
        """Имена игроков — по коду класса; без имени игрок называется по классу."""
        self.by_test_id("campaign-new-name").fill(name)
        for code in classes:
            self.select_class(code)
        for code, player in (player_names or {}).items():
            self.by_test_id(f"campaign-new-player-{code}").fill(player)
        self.click("campaign-new-submit")

    def select_class(self, code: str) -> None:
        """Поле выбора Mantine Chip скрыто — нажимаем на его подпись, как пользователь."""
        chip_id = self.by_test_id(f"campaign-new-class-{code}").get_attribute("id")
        self.page.locator(f"label[for='{chip_id}']").click()
