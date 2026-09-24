"""Тесты экрана «Подготовка к бою» (режим экспедиции).

Действия выполняются вне `check`, проверки — внутри `with check(...)`, чтобы одна упавшая
проверка не скрывала остальные.
"""

import allure
from pytest_check import check

from pages.battle_page import BattlePage
from pages.main_page import MainPage

# Босс, сложность → (урон для раны, здоровье для смены стойки)
BOSS_CHARACTERISTICS = [
    ("Огонь - Вираксен", "0", "2", "7"),
    ("Огонь - Вираксен", "1", "5", "7"),
    ("Огонь - Вираксен", "2", "10", "7"),
    ("Огонь - Вираксен", "3", "18", "7"),
    ("Рог - Дигоракс", "2", "9", "8"),
    ("Рог - Торамат", "2", "10", "7"),
    ("Пробуждённый", "3", "30", "8"),
]


@allure.feature('Проверка окна "Подготовка к бою"')
@allure.story("Характеристики боссов подставляются по боссу и сложности")
def test_bosses_characteristics(driver):
    prep = MainPage(driver).select_expedition()
    for name, complexity, damage_to_wound, stance_change in BOSS_CHARACTERISTICS:
        prep.select_boss(name)
        prep.set_complexity(complexity)
        with check(f"{name}, сложность {complexity}: урон для раны {damage_to_wound}"):
            assert prep.get_damage_to_wound() == damage_to_wound
        with check(f"{name}, сложность {complexity}: смена стойки при {stance_change}"):
            assert prep.get_stance_change() == stance_change


@allure.feature('Проверка обязательности поля "Количество охотников"')
@allure.story("Без количества охотников бой не начинается")
def test_players_count_required(driver):
    prep = MainPage(driver).select_expedition()
    with check("Без выбора босса количество охотников предзаполнено"):
        assert prep.get_players_count() != ""

    prep.select_boss("Металл - Юром")
    prep.set_complexity("0")
    with check("С выбранным боссом количество охотников предзаполнено"):
        assert prep.get_players_count() != ""

    prep.clear_players_count()
    prep.click_start_battle_expecting_error()
    with check("С пустым полем экран подготовки остаётся открытым"):
        assert prep.is_displayed()
    with check("Бой не начался"):
        assert not prep.is_element_visible_quick(BattlePage.PHASE)
