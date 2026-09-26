from enum import Enum

import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from pages.base_page import BasePage


class BattleSource(Enum):
    EXPEDITION = "expedition"
    CAMPAIGN = "campaign"


class BattlePage(BasePage):
    def __init__(self, driver, source: BattleSource = BattleSource.EXPEDITION):
        super().__init__(driver)
        self.source = source

    PHASE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Фаза")]')
    ROUND = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Раунд")]')
    HEALTH = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Здоровье:")]')
    RAGE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Ярость: ")]')
    ACCUMULATED_DAMAGE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Накопленный урон:")]')
    TOUGHNESS = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Прочность:")]')
    STATUS = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Статус:")]')
    STANCE_CHANGE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Смена стойки:")]')

    DAMAGE_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нанести урон:"]/following::android.widget.TextView[@text="+1"]')
    DAMAGE_5 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нанести урон:"]/following::android.widget.TextView[@text="+5"]')
    DAMAGE_10 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нанести урон:"]/following::android.widget.TextView[@text="+10"]')
    DAMAGE_50 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Нанести урон:"]/following::android.widget.TextView[@text="+50"]')
    ENTER_DAMAGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ввести урон"]')
    DAMAGE_INPUT = (AppiumBy.CLASS_NAME, "android.widget.EditText")
    OK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="OK"]')
    CANCEL_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отмена"]')

    RAGE_MINUS_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ярость:"]/following::android.widget.TextView[@text="-1"]')
    RAGE_PLUS_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ярость:"]/following::android.widget.TextView[@text="+1"]')
    RAGE_PLUS_1_PER_HUNTER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ярость:"]/following::android.widget.TextView[@text="+1/охот"]')
    RAGE_PLUS_1_PER_HUNTER_MINUS_1 = (AppiumBy.XPATH, '//android.widget.TextView[@text="Ярость:"]/following::android.widget.TextView[@text="+1/охот-1"]')

    # Два независимых статуса монстра (defects.md R-3): переключатель — последний checkable-сосед подписи
    HARDENED_SWITCH = (AppiumBy.XPATH, '//android.widget.TextView[@text="Затвердевший:"]/following-sibling::android.view.View[@checkable="true"]')
    RESILIENT_SWITCH = (AppiumBy.XPATH, '//android.widget.TextView[@text="Устойчивость стойки:"]/following-sibling::android.view.View[@checkable="true"]')
    HARDENED_INFO = (AppiumBy.XPATH, '//*[@content-desc="Описание: Затвердевший"]')
    RESILIENT_INFO = (AppiumBy.XPATH, '//*[@content-desc="Описание: Устойчивость стойки"]')
    HEAL_WOUND = (AppiumBy.XPATH, '//android.widget.TextView[@text="Заживить рану (+1 здоровья)"]')
    NEGATIVE_DAMAGE_HINT = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отрицательное значение отменяет накопленный урон"]')
    END_ROUND = (AppiumBy.XPATH, '//android.widget.TextView[@text="Закончить раунд"]')
    CHANGE_STANCE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сменить стойку"]')
    SURRENDER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сдаться"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')
    PENDING_DAMAGE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Ожидание... ")]')
    APPLY_NOW = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Применить урон сейчас")]')
    UNDO = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отменить действие"]')

    @allure.step("Получить урон, ожидающий применения (быстрые кнопки)")
    def get_pending_damage_value(self) -> int | None:
        """Число из «Ожидание... N урона» или None, если ожидающего урона нет."""
        elements = self.driver.find_elements(*self.PENDING_DAMAGE)
        return int(elements[0].text.split()[1]) if elements else None

    @allure.step("Дождаться автоприменения урона по таймеру быстрых кнопок")
    def wait_pending_damage_applied(self, timeout: int = 5) -> None:
        WebDriverWait(self.driver, timeout).until(EC.invisibility_of_element_located(self.PENDING_DAMAGE))

    @allure.step("Нажать «Применить урон сейчас»")
    def apply_pending_now(self) -> None:
        self.click(self.APPLY_NOW)

    @allure.step("Нажать «Отменить действие»")
    def undo(self) -> None:
        self.click(self.UNDO)

    @allure.step("Получить текущую фазу")
    def get_phase(self) -> str:
        return self.get_text(self.PHASE)

    @allure.step("Получить номер фазы числом")
    def get_phase_number(self) -> int | None:
        text = self.get_text(self.PHASE).replace("Фаза ", "").strip()
        roman = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9}
        return roman.get(text)

    @allure.step("Получить номер раунда")
    def get_round(self) -> str:
        return self.get_text(self.ROUND)

    @allure.step("Получить номер раунда числом")
    def get_round_number(self) -> int | None:
        text = self.get_text(self.ROUND).replace("Раунд ", "").split("/")[0].strip()
        return int(text) if text else None

    @allure.step("Получить здоровье")
    def get_health(self) -> str:
        return self.get_text(self.HEALTH)

    @allure.step("Получить числовое значение здоровья")
    def get_health_value(self) -> int | None:
        text = self.get_text(self.HEALTH).replace("Здоровье: ", "").strip()
        return int(text) if text else None

    @allure.step("Получить ярость")
    def get_rage(self) -> str:
        return self.get_text(self.RAGE)

    @allure.step("Получить накопленный урон")
    def get_accumulated_damage(self) -> str:
        return self.get_text(self.ACCUMULATED_DAMAGE)

    @allure.step("Получить числовое значение накопленного урона")
    def get_accumulated_damage_value(self) -> int | None:
        text = self.get_text(self.ACCUMULATED_DAMAGE).replace("Накопленный урон: ", "").strip()
        return int(text) if text else None

    @allure.step("Получить прочность")
    def get_toughness(self) -> str:
        return self.get_text(self.TOUGHNESS)

    @allure.step("Получить числовое значение прочности")
    def get_toughness_value(self) -> int | None:
        text = self.get_text(self.TOUGHNESS).replace("Прочность: ", "").strip()
        return int(text) if text else None

    @allure.step("Получить статус")
    def get_status(self) -> str:
        return self.get_text(self.STATUS)

    @allure.step("Получить числовое значение ярости")
    def get_rage_value(self) -> int | None:
        text = self.get_text(self.RAGE).replace("Ярость: ", "").strip()
        return int(text) if text else None

    @allure.step("Получить условие смены стойки")
    def get_stance_change(self) -> str:
        return self.get_text(self.STANCE_CHANGE)

    @allure.step("Получить числовое значение здоровья для смены стойки")
    def get_stance_change_value(self) -> int | None:
        text = self.get_text(self.STANCE_CHANGE)
        text = text.replace("Смена стойки: при ", "").replace(" HP", "").strip()
        return int(text) if text else None

    @allure.step("Нанести урон +1")
    def apply_damage_1(self) -> None:
        self.click(self.DAMAGE_1)

    @allure.step("Нанести урон +5")
    def apply_damage_5(self) -> None:
        self.click(self.DAMAGE_5)

    @allure.step("Нанести урон +10")
    def apply_damage_10(self) -> None:
        self.click(self.DAMAGE_10)

    @allure.step("Нанести урон +50")
    def apply_damage_50(self) -> None:
        self.click(self.DAMAGE_50)

    @allure.step("Открыть диалог ввода урона")
    def click_enter_damage(self) -> None:
        self.click(self.ENTER_DAMAGE)

    @allure.step("Нанести урон ручным вводом")
    def apply_damage_manual(self, value: str) -> None:
        self.click_enter_damage()
        input_element = self.wait.until(EC.visibility_of_element_located(self.DAMAGE_INPUT))
        input_element.clear()
        input_element.send_keys(value)
        self.click_ok()

    @allure.step("Подтвердить")
    def click_ok(self) -> None:
        self.click(self.OK_BUTTON)

    @allure.step("Отменить")
    def click_cancel(self) -> None:
        self.click(self.CANCEL_BUTTON)

    @allure.step("Уменьшить ярость на 1")
    def rage_minus_1(self) -> None:
        self.click(self.RAGE_MINUS_1)

    @allure.step("Увеличить ярость на 1")
    def rage_plus_1(self) -> None:
        self.click(self.RAGE_PLUS_1)

    @allure.step("Увеличить ярость на 1 за каждого охотника")
    def rage_plus_1_per_hunter(self) -> None:
        self.click(self.RAGE_PLUS_1_PER_HUNTER)

    @allure.step("Увеличить ярость на 1 за каждого охотника минус 1")
    def rage_plus_1_per_hunter_minus_1(self) -> None:
        self.click(self.RAGE_PLUS_1_PER_HUNTER_MINUS_1)

    @allure.step("Переключить «Затвердевший»")
    def click_hardened(self) -> None:
        self.click(self.HARDENED_SWITCH)

    @allure.step("Переключить «Устойчивость стойки»")
    def click_resilient(self) -> None:
        self.click(self.RESILIENT_SWITCH)

    @allure.step("Проверить, включён ли «Затвердевший»")
    def is_hardened_on(self) -> bool:
        return self.driver.find_element(*self.HARDENED_SWITCH).get_attribute("checked") == "true"

    @allure.step("Проверить, включена ли «Устойчивость стойки»")
    def is_resilient_on(self) -> bool:
        return self.driver.find_element(*self.RESILIENT_SWITCH).get_attribute("checked") == "true"

    @allure.step("Открыть описание статуса «{title}» (кнопка «i»)")
    def open_status_info(self, title: str) -> "StatusInfoDialog":
        locator = self.HARDENED_INFO if title == "Затвердевший" else self.RESILIENT_INFO
        self.click(locator)
        return StatusInfoDialog(self.driver)

    @allure.step("Заживить рану (+1 здоровья)")
    def heal_wound(self) -> None:
        self.click(self.HEAL_WOUND)


    @allure.step("Сменить стойку вручную")
    def click_change_stance(self) -> "StanceChangeDialog":
        self.click(self.CHANGE_STANCE)
        return StanceChangeDialog(self.driver)

    @allure.step("Закончить раунд")
    def end_round(self) -> None:
        self.click(self.END_ROUND)

    def _reveal_bottom_button(self, text: str) -> None:
        """Кнопки внизу экрана боя: после ручного ввода их может закрывать клавиатура, ниже — прокрутка."""
        selector = (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().text("{text}")')
        if any(el.is_displayed() for el in self.driver.find_elements(*selector)):
            return
        # Плавающая клавиатура не закрывает кнопки, а BACK при ней сворачивает приложение — поэтому
        # клавиатура закрывается только если кнопка не видна, и одним BACK (hide_keyboard() повторяет BACK)
        if self.driver.is_keyboard_shown():
            self.driver.press_keycode(4)
            WebDriverWait(self.driver, 3).until(lambda d: not d.is_keyboard_shown())
        self.scroll_into_view(text)

    @allure.step("Сдаться")
    def surrender(self) -> "SurrenderDialog":
        self._reveal_bottom_button("Сдаться")
        self.click(self.SURRENDER)
        return SurrenderDialog(self.driver)

    @allure.step("Выйти в меню")
    def exit_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self._reveal_bottom_button("Выход в меню")
        self.click(self.EXIT_TO_MENU)
        return MainPage(self.driver)

    @allure.step("Получить диалог победы в зависимости от источника боя")
    def get_victory_dialog(self) -> "VictoryDialog | CampaignVictoryDialog":
        if self.source == BattleSource.CAMPAIGN:
            return CampaignVictoryDialog(self.driver)
        return VictoryDialog(self.driver)


class RageSurgeDialog(BasePage):
    MESSAGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выплеск ярости"]')
    DAMAGE_HINT = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "урон, равный силе монстра")]')
    OK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="OK"]')

    @allure.step("Проверить, что окно «Выплеск ярости» отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.MESSAGE)

    @allure.step("Проверить, что окно напоминает об уроне охотникам")
    def has_damage_hint(self) -> bool:
        return self.is_element_visible(self.DAMAGE_HINT)

    @allure.step("Закрыть окно «Выплеск ярости», если оно появилось")
    def dismiss_if_shown(self, timeout: int = 2) -> bool:
        if self.is_element_visible_quick(self.MESSAGE, timeout=timeout):
            self.click_ok()
            return True
        return False

    @allure.step("Нажать OK в окне «Выплеск ярости»")
    def click_ok(self) -> None:
        self.click(self.OK_BUTTON)


class SurrenderDialog(BasePage):
    MESSAGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сдаться?"]')
    CONFIRM = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сдаться"]')
    CANCEL = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отмена"]')

    @allure.step("Проверить, что окно подтверждения сдачи отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.MESSAGE)

    @allure.step("Подтвердить сдачу")
    def confirm(self) -> "DefeatDialog":
        self.click(self.CONFIRM)
        return DefeatDialog(self.driver)

    @allure.step("Отменить сдачу")
    def cancel(self) -> None:
        self.click(self.CANCEL)


class DefeatDialog(BasePage):
    MESSAGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="ПОРАЖЕНИЕ"]')
    ROUNDS_OVER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Закончились раунды..."]')
    SURRENDERED = (AppiumBy.XPATH, '//android.widget.TextView[@text="Вы сдались."]')
    NEW_BATTLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Новый бой"]')
    CONTINUE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Продолжить"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')

    @allure.step("Проверить, что окно поражения отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.MESSAGE)

    @allure.step("Проверить причину поражения «Вы сдались.»")
    def is_surrender_reason_displayed(self) -> bool:
        return self.is_element_visible(self.SURRENDERED)

    @allure.step("Проверить причину поражения «Закончились раунды...»")
    def is_rounds_over_reason_displayed(self) -> bool:
        return self.is_element_visible(self.ROUNDS_OVER)

    @allure.step("Нажать Продолжить (поражение в кампании)")
    def click_continue(self) -> "QuestRewardsPage":
        from pages.campaign_rewards_page import QuestRewardsPage
        self.click(self.CONTINUE)
        return QuestRewardsPage(self.driver)

    @allure.step("Нажать Новый бой")
    def click_new_battle(self) -> "BattlePreparation":
        from pages.battle_preparation import BattlePreparation
        self.click(self.NEW_BATTLE)
        return BattlePreparation(self.driver)

    @allure.step("Нажать Выход в меню")
    def click_exit_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.EXIT_TO_MENU)
        return MainPage(self.driver)


class VictoryDialog(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="ПОБЕДА!"]')
    NEW_BATTLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Новый бой"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')

    @allure.step("Проверить, что экран победы отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Нажать Новый бой")
    def click_new_battle(self) -> "BattlePreparation":
        from pages.battle_preparation import BattlePreparation
        self.click(self.NEW_BATTLE)
        return BattlePreparation(self.driver)

    @allure.step("Нажать Выход в меню")
    def click_exit_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.EXIT_TO_MENU)
        return MainPage(self.driver)


class CampaignVictoryDialog(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="ПОБЕДА!"]')
    MONSTER_DEFEATED = (AppiumBy.XPATH, '//android.widget.TextView[@text="Монстр повержен!"]')
    CONTINUE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Продолжить"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')

    @allure.step("Проверить, что экран победы отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Проверить надпись «Монстр повержен!»")
    def is_monster_defeated_displayed(self) -> bool:
        return self.is_element_visible(self.MONSTER_DEFEATED)

    @allure.step("Нажать Продолжить — перейти к наградам главы (пролог) или наградам задания")
    def click_continue(self) -> "CampaignRewardsPage | QuestRewardsPage":
        from pages.campaign_rewards_page import CampaignRewardsPage, QuestRewardsPage
        self.click(self.CONTINUE)
        self.wait.until(lambda _: self.driver.find_elements(*CampaignRewardsPage.TITLE)
                        or self.driver.find_elements(*QuestRewardsPage.TITLE))
        if self.driver.find_elements(*CampaignRewardsPage.TITLE):
            return CampaignRewardsPage(self.driver)
        return QuestRewardsPage(self.driver)

    @allure.step("Нажать Выход в меню")
    def click_exit_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.EXIT_TO_MENU)
        return MainPage(self.driver)


class StatusInfoDialog(BasePage):
    """Описание статуса монстра по кнопке «i» (defects.md R-3)."""
    CLOSE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Понятно"]')

    @allure.step("Проверить, что открыто описание «{title}»")
    def is_displayed(self, title: str) -> bool:
        return self.is_element_visible((AppiumBy.XPATH, f'//android.widget.TextView[@text="{title}"]'))

    @allure.step("Проверить, что описание содержит «{text}»")
    def has_text(self, text: str) -> bool:
        return self.is_element_visible((AppiumBy.XPATH, f'//android.widget.TextView[contains(@text, "{text}")]'))

    @allure.step("Закрыть описание статуса")
    def close(self) -> None:
        self.click(self.CLOSE)
        self.wait.until(EC.invisibility_of_element_located(self.CLOSE))


class StanceChangeDialog(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Смена стойки!"]')
    FROM_BOSS_DATA = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Параметры заполнены из базы боссов")]')
    NO_BOSS_DATA = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Данных о стойке в базе боссов нет")]')
    CARRIED_DAMAGE = (AppiumBy.XPATH, '//android.widget.TextView[starts-with(@text, "Перенесённый урон")]')
    DAMAGE_TO_WOUND = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Урон для нанесения раны на игрока")]/..')
    STANCE_CHANGE_HEALTH = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Здоровье для смены стойки")]/..')
    BOSS_HEALTH = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Здоровье босса в новой стойке")]/..')
    OK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="OK"]')
    CANCEL_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отмена"]')

    @allure.step("Проверить, что окно смены стойки отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Проверить, что поля заполнены из базы боссов")
    def is_from_boss_data(self) -> bool:
        return self.is_element_visible_quick(self.FROM_BOSS_DATA)

    @allure.step("Проверить, что данных о стойке в базе боссов нет")
    def is_without_boss_data(self) -> bool:
        return self.is_element_visible_quick(self.NO_BOSS_DATA)

    @allure.step("Получить строку о перенесённом уроне")
    def get_carried_damage_text(self) -> str | None:
        elements = self.driver.find_elements(*self.CARRIED_DAMAGE)
        return elements[0].text if elements else None

    @allure.step("Установить урон для нанесения раны на игрока")
    def set_damage_to_wound(self, value: str) -> None:
        self.type_text(self.DAMAGE_TO_WOUND, value)

    @allure.step("Получить урон для нанесения раны на игрока")
    def get_damage_to_wound(self) -> str:
        return self.get_text(self.DAMAGE_TO_WOUND)

    @allure.step("Установить здоровье для смены стойки")
    def set_stance_change_health(self, value: str) -> None:
        self.type_text(self.STANCE_CHANGE_HEALTH, value)

    @allure.step("Установить здоровье босса в новой стойке")
    def set_boss_health(self, value: str) -> None:
        self.type_text(self.BOSS_HEALTH, value)

    @allure.step("Нажать OK")
    def click_ok(self) -> None:
        self.click(self.OK_BUTTON)

    @allure.step("Нажать Отмена")
    def click_cancel(self) -> None:
        self.click(self.CANCEL_BUTTON)