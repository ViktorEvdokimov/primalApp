import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage


class BattlePage(BasePage):
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

    STABILITY = (AppiumBy.XPATH, '//android.widget.TextView[@text="Устойчивость:"]/following-sibling::android.view.View[@clickable="true"]')
    END_ROUND = (AppiumBy.XPATH, '//android.widget.TextView[@text="Закончить раунд"]')
    CHANGE_STANCE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сменить стойку"]')
    SURRENDER = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сдаться"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')

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

    @allure.step("Кликнуть Устойчивость")
    def click_stability(self) -> None:
        self.click(self.STABILITY)

    @allure.step("Сменить стойку вручную")
    def click_change_stance(self) -> "StanceChangeDialog":
        self.click(self.CHANGE_STANCE)
        return StanceChangeDialog(self.driver)

    @allure.step("Закончить раунд")
    def end_round(self) -> None:
        self.click(self.END_ROUND)

    @allure.step("Сдаться")
    def surrender(self) -> "SurrenderDialog":
        self.click(self.SURRENDER)
        return SurrenderDialog(self.driver)

    @allure.step("Выйти в меню")
    def exit_to_menu(self) -> "MainPage":
        from pages.main_page import MainPage
        self.click(self.EXIT_TO_MENU)
        return MainPage(self.driver)


class RageSurgeDialog(BasePage):
    MESSAGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Всплеск ярости"]')
    OK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="OK"]')

    @allure.step("Проверить, что окно Всплеск ярости отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.MESSAGE)

    @allure.step("Нажать OK в окне Всплеск ярости")
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
    NEW_BATTLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Новый бой"]')
    EXIT_TO_MENU = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выход в меню"]')

    @allure.step("Проверить, что окно поражения отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.MESSAGE)

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


class StanceChangeDialog(BasePage):
    TITLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Смена стойки!"]')
    DAMAGE_TO_WOUND = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Урон для нанесения раны на игрока")]/..')
    STANCE_CHANGE_HEALTH = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Здоровье для смены стойки")]/..')
    BOSS_HEALTH = (AppiumBy.XPATH, '//android.widget.TextView[contains(@text, "Здоровье босса в новой стойке")]/..')
    OK_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="OK"]')
    CANCEL_BUTTON = (AppiumBy.XPATH, '//android.widget.TextView[@text="Отмена"]')

    @allure.step("Проверить, что окно смены стойки отображается")
    def is_displayed(self) -> bool:
        return self.is_element_visible(self.TITLE)

    @allure.step("Установить урон для нанесения раны на игрока")
    def set_damage_to_wound(self, value: str) -> None:
        self.type_text(self.DAMAGE_TO_WOUND, value)

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