from playwright.sync_api import Locator, Page, expect


class BasePage:
    """Общие действия со страницей. Элементы ищутся только по data-testid (doc/qa.md, решение 10)."""

    path = "/"
    root_test_id = ""

    def __init__(self, page: Page):
        self.page = page

    def open(self):
        self.page.goto(self.path)
        return self

    def by_test_id(self, test_id: str) -> Locator:
        return self.page.get_by_test_id(test_id)

    def click(self, test_id: str) -> None:
        self.by_test_id(test_id).click()

    def should_be_open(self) -> None:
        expect(self.by_test_id(self.root_test_id)).to_be_visible()
