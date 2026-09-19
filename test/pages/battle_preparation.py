import allure
from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage

class BattlePreparation(BasePage):
    BOSS = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выберите босса"]')
    COMPLEXITY = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сложность"]')
    PLAYERS_COUNT = (AppiumBy.XPATH, '//android.widget.TextView[@text="Количество охотников"]/..')
    DAMAGE_TO_WOUND = (AppiumBy.XPATH, '//android.widget.TextView[@text="Урон для нанесения раны на игрока (пусто = нет порога раны)"]/..')
    STANCE_CHANGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Здоровье для смены стойки (пусто = по запросу)"]/..')

    def select_boss(self, name: str) -> None:
        with allure.step(f"Выбор босса {name}"):
            self.click(self.BOSS)
            element = self.scroll_to_text(name)
            element.click()

    @allure.step("Установить сложность")
    def set_complexity(self, complexity: str) -> None:
        self.click(self.COMPLEXITY)
        self.click((AppiumBy.XPATH, f'//android.widget.TextView[@text="{complexity}"]/..'))

    @allure.step("Установить количество игроков")
    def set_players_count(self, players_count: str) -> None:
        self.type_text(self.PLAYERS_COUNT, players_count)

    @allure.step("Получить текущее значение урона для нанесения раны")
    def get_damage_to_wound(self) -> str:
        return self.get_text(self.DAMAGE_TO_WOUND)

    @allure.step("Установить значение урона для нанесения раны")
    def set_damage_to_wound(self, damage: str) -> None:
        self.type_text(self.DAMAGE_TO_WOUND, damage)

    @allure.step("Получить текущее здоровье для смены стойки")
    def get_stance_change(self) -> str:
        return self.get_text(self.STANCE_CHANGE)

    @allure.step("Установить здоровье для смены стойки")
    def set_stance_change(self, damage: str) -> None:
        self.type_text(self.STANCE_CHANGE, damage)