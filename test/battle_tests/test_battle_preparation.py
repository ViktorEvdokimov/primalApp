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


@allure.feature('Проверка обязательности поля "Количество охотников"')
@pytest.mark.parametrize(
    "boss_name",
    [
        pytest.param(None, id="Без выбора босса (Ввести данные вручную)"),
        pytest.param("Металл - Юром", id="С выбранным боссом Металл - Юром"),
    ]
)
def test_players_count_required(driver, boss_name):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()

    if boss_name:
        allure.dynamic.story(f'Поле "Количество охотников" обязательно для заполнения, босс: {boss_name}')
        battle_preparation.select_boss(boss_name)
        battle_preparation.set_complexity("0")
    else:
        allure.dynamic.story('Поле "Количество охотников" обязательно для заполнения, босс не выбран')

    default_count = battle_preparation.get_players_count()
    assert default_count != "", "Ожидается, что значение количества игроков предзаполненно по умолчанию"

    battle_preparation.clear_players_count()
    battle_preparation.click_start_battle_expecting_error()

    assert battle_preparation.is_element_visible(battle_preparation.START_BATTLE), \
        "Страница не должна измениться — поле 'Количество охотников' обязательно"
