from playwright.sync_api import Page, expect

from pages.base_page import BasePage


class JoinPage(BasePage):
    """Вход по ссылке-приглашению: «Вас пригласили…» и окно «Кто вы в этой кампании?» с персонажами отряда."""

    root_test_id = "page-join"
    HUNTER = "join-hunter"

    def __init__(self, page: Page, path: str = "/"):
        super().__init__(page)
        self.path = path

    def campaign_text(self) -> str:
        return self.by_test_id("join-campaign").inner_text()

    def hunters(self) -> list[str]:
        """Имена игроков в окне выбора — окно открывается само."""
        expect(self.by_test_id(self.HUNTER).first).to_be_visible()
        return self.by_test_id(self.HUNTER).evaluate_all("nodes => nodes.map(n => n.dataset.player)")

    def has_name_field(self) -> bool:
        return self.page.get_by_role("textbox").count() > 0

    def choose(self, player: str) -> None:
        """Выбрать своего персонажа — вход в кампанию."""
        self.page.locator(f'[data-testid="{self.HUNTER}"][data-player="{player}"]').click()
