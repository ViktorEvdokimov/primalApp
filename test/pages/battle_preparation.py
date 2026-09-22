import allure
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common import TimeoutException

from pages.base_page import BasePage
from pages.battle_page import BattlePage

class BattlePreparation(BasePage):
    BOSS_LABEL = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выберите босса"]')
    BOSS_VALUE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Выберите босса"]/parent::android.widget.EditText')
    COMPLEXITY = (AppiumBy.XPATH, '//android.widget.TextView[@text="Сложность"]')
    PLAYERS_COUNT = (AppiumBy.XPATH, '//android.widget.TextView[@text="Количество охотников"]/..')
    DAMAGE_TO_WOUND = (AppiumBy.XPATH, '//android.widget.TextView[@text="Урон для нанесения раны на игрока (пусто = нет порога раны)"]/..')
    STANCE_CHANGE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Здоровье для смены стойки (пусто = по запросу)"]/..')
    START_BATTLE = (AppiumBy.XPATH, '//android.widget.TextView[@text="Начать бой"]')

    def select_boss(self, name: str) -> None:
        with allure.step(f"Выбор босса {name}"):
            self.click(self.BOSS_LABEL)
            self.scroll_to_text(name)

            boss_item = (AppiumBy.XPATH, f'//android.widget.TextView[@text="{name}"]/..')
            self.click(boss_item)

            if not self.is_element_visible_quick(self.START_BATTLE):
                self.scroll_to_text(name)
                self.click(boss_item)
                if not self.is_element_visible_quick(self.START_BATTLE):
                    raise TimeoutException(
                        f"Окно выбора босса не закрылось после двух попыток. "
                        f"Босс: '{name}', START_BATTLE не виден на странице."
                    )

        if self.get_selected_boss() != name:
            raise ValueError(
                f"Босс выбран некорректно: ожидаемое значение {name}, "
                f"фактическое значение {self.get_selected_boss()}"
            )

    def get_selected_boss(self) -> str:
        return self.get_text(self.BOSS_VALUE)

    @allure.step("Установить сложность")
    def set_complexity(self, complexity: str) -> None:
        if self.is_element_visible(self.COMPLEXITY):
            self.click(self.COMPLEXITY)
        else:
            self.scroll_to_text("Сложность")
            self.click(self.COMPLEXITY)
        self.click((AppiumBy.XPATH, f'//android.widget.TextView[@text="{complexity}"]/..'))

    @allure.step("Установить количество охотников")
    def set_players_count(self, players_count: str) -> None:
        self.type_text(self.PLAYERS_COUNT, players_count)

    @allure.step("Получить текущее количество охотников")
    def get_players_count(self) -> str:
        return self.get_text(self.PLAYERS_COUNT)

    @allure.step("Получить количество охотников числом")
    def get_players_count_value(self) -> int | None:
        text = self.get_players_count().strip()
        return int(text) if text else None

    @allure.step("Очистить поле Количество охотников")
    def clear_players_count(self) -> None:
        self.set_players_count("")

    @allure.step("Получить текущее значение урона для нанесения раны")
    def get_damage_to_wound(self) -> str:
        return self.get_text(self.DAMAGE_TO_WOUND)

    @allure.step("Получить значение урона для нанесения раны числом")
    def get_damage_to_wound_value(self) -> int | None:
        text = self.get_damage_to_wound().strip()
        return int(text) if text else None

    @allure.step("Установить значение урона для нанесения раны")
    def set_damage_to_wound(self, damage: str) -> None:
        self.type_text(self.DAMAGE_TO_WOUND, damage)

    @allure.step("Получить текущее здоровье для смены стойки")
    def get_stance_change(self) -> str:
        return self.get_text(self.STANCE_CHANGE)

    @allure.step("Получить здоровье для смены стойки числом")
    def get_stance_change_value(self) -> int | None:
        text = self.get_stance_change().strip()
        return int(text) if text else None

    @allure.step("Установить здоровье для смены стойки")
    def set_stance_change(self, damage: str) -> None:
        self.type_text(self.STANCE_CHANGE, damage)

    @allure.step("Нажать кнопку Начать бой")
    def start_battle(self) -> BattlePage:
        self.click(self.START_BATTLE)
        return BattlePage(self.driver)

    @allure.step("Нажать кнопку Начать бой (ожидается ошибка — поле обязательно)")
    def click_start_battle_expecting_error(self) -> "BattlePreparation":
        self.click(self.START_BATTLE)
        return self