import allure
import pytest

from pages.battle_preparation import BattlePreparation
from pages.main_page import MainPage


@allure.feature('Проверка окна "Подготовка к бою"')
@pytest.mark.parametrize(
    "name, complexity, damage_to_wound, stance_change",
    [
        pytest.param("Огонь - Вираксен", "0", "2", "7"),
        pytest.param("Огонь - Вираксен", "1", "5", "7"),
        pytest.param("Огонь - Вираксен", "2", "10", "7"),
        pytest.param("Огонь - Вираксен", "3", "18", "7"),
        pytest.param("Рог - Дигоракс", "2", "9", "8"),
        pytest.param("Рог - Торамат", "2", "10", "7"),
        pytest.param("Пробуждённый", "3", "30", "8")
    ]
)
def test_bosses_characteristics(driver, name: str, complexity: str, damage_to_wound: str, stance_change: str):
    allure.dynamic.story(f'Проверка характеристик "{name}"')
    main_page = MainPage(driver)

    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss(name)
    battle_preparation.set_complexity(complexity)
    assert battle_preparation.get_damage_to_wound() == damage_to_wound
    assert battle_preparation.get_stance_change() == stance_change
