import allure
from playwright.sync_api import expect

from pages.base_page import BasePage


class ConsequencesDialog(BasePage):
    """Подтверждение «Отклонить» со списком того, что не будет применено."""

    root_test_id = "consequences-dialog"

    def consequences(self) -> list[str]:
        expect(self.by_test_id(self.root_test_id)).to_be_visible()
        return [item.inner_text() for item in self.by_test_id("consequence").all()]

    @allure.step("Окно «Отклонить»: «Вернуться»")
    def cancel(self) -> None:
        expect(self.by_test_id(self.root_test_id)).to_be_visible()
        self.click("consequences-cancel")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()

    @allure.step("Окно «Отклонить»: «Отклонить»")
    def confirm(self) -> None:
        # Окно открывается с анимацией: нажимаем, когда оно на месте, и ждём, пока закроется
        expect(self.by_test_id(self.root_test_id)).to_be_visible()
        self.click("consequences-confirm")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()


class OutcomePage(BasePage):
    """Награды за бой кампании: превью, «Принять», «Редактировать», «Отклонить»."""

    root_test_id = "outcome-preview"

    def resources(self) -> dict[str, int]:
        return {badge.get_attribute("data-code") or "": int(badge.get_attribute("data-quantity") or "0")
                for badge in self.by_test_id("rewards-resource").all()}

    def open_quests_text(self) -> str:
        return self.by_test_id("rewards-open-quests").inner_text()

    def lines(self) -> list[str]:
        return [line.inner_text() for line in self.by_test_id("rewards-line").all()]

    @allure.step("Награды боя: «Принять»")
    def accept(self) -> None:
        self.click("outcome-accept")

    @allure.step("Награды боя: «Отклонить»")
    def dismiss(self) -> ConsequencesDialog:
        self.click("outcome-dismiss")
        return ConsequencesDialog(self.page)


class TransitionPage(BasePage):
    """Награды главы: решение главы, награды, «Принять» / «Отклонить»."""

    root_test_id = "page-campaign-transition"

    def to_chapter(self) -> int:
        expect(self.by_test_id("transition-title")).to_be_visible()
        return int(self.by_test_id(self.root_test_id).get_attribute("data-to-chapter") or "0")

    def open_quests_text(self) -> str:
        return self.by_test_id("rewards-open-quests").inner_text()

    @allure.step("Решение главы: «{option}»")
    def answer(self, option: str) -> None:
        self.click(f"decision-option-{option}")
        expect(self.by_test_id("decision-dialog")).to_be_hidden()

    @allure.step("Награды главы: «Принять»")
    def accept(self) -> None:
        self.click("transition-accept")

    @allure.step("Награды главы: «Отклонить»")
    def reject(self) -> ConsequencesDialog:
        self.click("transition-reject")
        return ConsequencesDialog(self.page)
