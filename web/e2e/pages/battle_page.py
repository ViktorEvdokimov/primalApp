import re

import allure
from playwright.sync_api import expect

from pages.base_page import BasePage

ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9}


def _number(text: str) -> int | None:
    match = re.search(r"-?\d+", text)
    return int(match.group()) if match else None


class BattlePage(BasePage):
    """Экран боя (аналог BattlePage из test/pages): значения панели и действия."""

    path = "/battle"
    root_test_id = "battle-phase"

    PHASE = "battle-phase"
    ROUND = "battle-round"
    HEALTH = "battle-health"
    RAGE = "battle-rage"
    ACCUMULATED = "battle-accumulated"
    TOUGHNESS = "battle-toughness"
    STATUS = "battle-status"
    STANCE_CHANGE = "battle-stance-change"
    MESSAGE = "battle-message"
    PENDING = "battle-pending"
    DAMAGE_INPUT = "battle-damage-input"
    OK = "battle-damage-ok"
    CANCEL = "battle-damage-cancel"
    NEGATIVE_HINT = "battle-negative-hint"
    APPLY_NOW = "battle-apply-now"
    HEAL = "battle-heal"
    UNDO = "battle-undo"
    UNDO_DESCRIPTION = "battle-undo-description"
    RAGE_MINUS_1 = "battle-rage-minus-1"
    RAGE_PLUS_1 = "battle-rage-plus-1"
    RAGE_PER_HUNTER = "battle-rage-per-hunter"
    RAGE_PER_HUNTER_MINUS_1 = "battle-rage-per-hunter-minus-1"
    HARDENED = "battle-hardened"
    RESILIENT = "battle-resilient"
    CHANGE_STANCE = "battle-change-stance"
    END_ROUND = "battle-end-round"
    SURRENDER = "battle-surrender"
    EXIT_TO_MENU = "battle-menu"

    def text(self, test_id: str) -> str:
        return self.by_test_id(test_id).inner_text()

    def is_visible(self, test_id: str) -> bool:
        return self.by_test_id(test_id).is_visible()

    # --- панель ---

    def get_phase_number(self) -> int | None:
        return ROMAN.get(self.text(self.PHASE).replace("Фаза", "").strip())

    def get_round_number(self) -> int | None:
        return _number(self.text(self.ROUND))

    def get_health_value(self) -> int | None:
        return _number(self.text(self.HEALTH))

    def get_rage_value(self) -> int | None:
        return _number(self.text(self.RAGE))

    def get_accumulated_damage_value(self) -> int | None:
        return _number(self.text(self.ACCUMULATED))

    def get_toughness_value(self) -> int | None:
        """Прочность числом; `None` — «Прочность: нет»."""
        return _number(self.text(self.TOUGHNESS))

    def get_status(self) -> str:
        return self.text(self.STATUS)

    def get_stance_change(self) -> str:
        return self.text(self.STANCE_CHANGE)

    def get_stance_change_value(self) -> int | None:
        return _number(self.get_stance_change())

    def get_pending_damage_value(self) -> int | None:
        """Число из «Ожидание... N урона» или `None`, если урон не ждёт таймера."""
        pending = self.by_test_id(self.PENDING)
        return _number(pending.inner_text()) if pending.is_visible() else None

    # --- урон ---

    @allure.step("Нажать +{amount}")
    def press_quick(self, amount: int) -> None:
        self.click(f"battle-quick-{amount}")

    @allure.step("Нанести урон {value} через поле ввода")
    def apply_damage_manual(self, value: str) -> None:
        field = self.by_test_id(self.DAMAGE_INPUT)
        field.fill(value)
        field.press("Enter")

    @allure.step("Дождаться применения урона по таймеру")
    def wait_pending_damage_applied(self, timeout: int = 5000) -> None:
        expect(self.by_test_id(self.PENDING)).to_be_hidden(timeout=timeout)

    @allure.step("Нажать «Применить урон сейчас»")
    def apply_pending_now(self) -> None:
        self.click(self.APPLY_NOW)

    @allure.step("Нажать «Заживить рану»")
    def heal_wound(self) -> None:
        self.click(self.HEAL)

    @allure.step("Нажать «Отменить действие»")
    def undo(self) -> None:
        self.click(self.UNDO)

    # --- ярость, статусы, раунд ---

    @allure.step("Ярость: {test_id}")
    def rage(self, test_id: str) -> None:
        self.click(test_id)

    def _toggle(self, test_id: str) -> None:
        """Поле переключателя Mantine скрыто под дорожкой — нажимаем, как пользователь, на дорожку."""
        switch = self.by_test_id(test_id)
        checked = switch.is_checked()
        switch.click(force=True)
        expect(switch).to_be_checked(checked=not checked)

    @allure.step("Переключить «Затвердевший»")
    def click_hardened(self) -> None:
        self._toggle(self.HARDENED)

    @allure.step("Переключить «Устойчивость стойки»")
    def click_resilient(self) -> None:
        self._toggle(self.RESILIENT)

    def is_hardened_on(self) -> bool:
        return self.by_test_id(self.HARDENED).is_checked()

    def is_resilient_on(self) -> bool:
        return self.by_test_id(self.RESILIENT).is_checked()

    @allure.step("Открыть описание статуса «{status}»")
    def open_status_info(self, status: str) -> "StatusInfoDialog":
        self.click(f"battle-{status}-info")
        return StatusInfoDialog(self.page)

    @allure.step("Нажать «Сменить стойку»")
    def click_change_stance(self) -> "StanceChangeDialog":
        self.click(self.CHANGE_STANCE)
        return StanceChangeDialog(self.page)

    @allure.step("Нажать «Закончить раунд»")
    def end_round(self) -> None:
        self.click(self.END_ROUND)

    @allure.step("Нажать «Сдаться»")
    def surrender(self) -> "SurrenderDialog":
        self.click(self.SURRENDER)
        return SurrenderDialog(self.page)

    @allure.step("Нажать «Выход в меню»")
    def exit_to_menu(self):
        from pages.main_page import MainPage

        self.click(self.EXIT_TO_MENU)
        main_page = MainPage(self.page)
        main_page.should_be_open()
        return main_page


class StanceChangeDialog(BasePage):
    """Окно «Смена стойки!»."""

    root_test_id = "stance-dialog"

    def is_displayed(self) -> bool:
        try:
            expect(self.by_test_id(self.root_test_id)).to_be_visible(timeout=3000)
            return True
        except AssertionError:
            return False

    def is_from_boss_data(self) -> bool:
        return self.by_test_id("stance-dialog-source").get_attribute("data-from-catalog") == "true"

    def is_without_boss_data(self) -> bool:
        return self.by_test_id("stance-dialog-source").get_attribute("data-from-catalog") == "false"

    def get_carried_damage_text(self) -> str | None:
        carried = self.by_test_id("stance-dialog-carried")
        return carried.inner_text() if carried.is_visible() else None

    def get_damage_to_wound(self) -> str:
        return self.by_test_id("stance-toughness").input_value()

    @allure.step("Окно смены стойки: урон для раны «{value}»")
    def set_damage_to_wound(self, value: str) -> None:
        self.by_test_id("stance-toughness").fill(value)

    @allure.step("Окно смены стойки: смена при «{value}»")
    def set_stance_change_health(self, value: str) -> None:
        self.by_test_id("stance-mode").get_by_text("По здоровью", exact=True).click()
        self.by_test_id("stance-at-health").fill(value)

    @allure.step("Окно смены стойки: OK")
    def click_ok(self) -> None:
        self.click("stance-dialog-ok")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()

    @allure.step("Окно смены стойки: «Отмена»")
    def click_cancel(self) -> None:
        self.click("stance-dialog-cancel")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()


class RageSurgeDialog(BasePage):
    """Окно «Выплеск ярости»."""

    root_test_id = "rage-surge-dialog"

    def is_displayed(self, timeout: int = 3000) -> bool:
        try:
            expect(self.by_test_id(self.root_test_id)).to_be_visible(timeout=timeout)
            return True
        except AssertionError:
            return False

    def has_damage_hint(self) -> bool:
        return "урон, равный силе монстра" in self.by_test_id(self.root_test_id).inner_text()

    @allure.step("Выплеск ярости: OK")
    def click_ok(self) -> None:
        self.click("rage-surge-ok")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()

    def dismiss_if_shown(self, timeout: int = 1000) -> bool:
        """Окно открывается с анимацией: ждём его недолго, прежде чем решить, что выплеска нет."""
        if self.is_displayed(timeout=timeout):
            self.click_ok()
            return True
        return False


class StatusInfoDialog(BasePage):
    """Описание статуса по кнопке «i»."""

    root_test_id = "battle-status-info"

    def text(self) -> str:
        return self.by_test_id(self.root_test_id).inner_text()

    @allure.step("Закрыть описание статуса")
    def close(self) -> None:
        self.click("battle-status-info-close")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()


class SurrenderDialog(BasePage):
    """Подтверждение «Сдаться?»."""

    root_test_id = "battle-surrender-confirm"

    @allure.step("Подтвердить «Сдаться»")
    def confirm(self) -> "ResultPage":
        self.click(self.root_test_id)
        result = ResultPage(self.page)
        result.should_be_open()
        return result


class ResultPage(BasePage):
    """Экраны «ПОБЕДА!» и «ПОРАЖЕНИЕ»."""

    root_test_id = "battle-result"

    def result(self) -> str | None:
        return self.by_test_id(self.root_test_id).get_attribute("data-result")

    def reason(self) -> str:
        return self.by_test_id("battle-result-reason").inner_text()

    @allure.step("Отменить итог боя")
    def undo(self) -> BattlePage:
        self.click("battle-result-undo")
        battle = BattlePage(self.page)
        battle.should_be_open()
        return battle

    @allure.step("Нажать «Новый бой»")
    def new_battle(self) -> None:
        self.click("battle-new")
