import allure
import pytest

from pages.main_page import MainPage
from pages.battle_page import BattlePage, RageSurgeDialog, DefeatDialog, VictoryDialog, StanceChangeDialog


def setup_battle(driver, boss="Огонь - Вираксен", complexity="0", players="4") -> BattlePage:
    prep = MainPage(driver).select_expedition()
    prep.select_boss(boss)
    prep.set_complexity(complexity)
    prep.set_players_count(players)
    return prep.start_battle()


# =================================================================================================
# Тесты окна боя (BattlePage)
# =================================================================================================

# region 1. НАЧАЛЬНОЕ СОСТОЯНИЕ ЭКРАНА БОЯ

@allure.feature("Экран боя — начальное состояние")
@pytest.mark.parametrize(
    "name, complexity, players_count",
    [
        pytest.param("Огонь - Вираксен", "0", "4", id="4 охотника"),
        pytest.param("Огонь - Вираксен", "0", "2", id="2 охотника"),
    ]
)
def test_initial_state(driver, name: str, complexity: str, players_count: str):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss(name)
    battle_preparation.set_complexity(complexity)
    battle_preparation.set_players_count(players_count)

    expected_toughness = int(players_count) * int(battle_preparation.get_damage_to_wound())
    expected_rage = int(players_count)
    expected_stance = int(battle_preparation.get_stance_change())

    battle_page = battle_preparation.start_battle()

    with allure.step("Фаза I"):
        assert battle_page.get_phase() == "Фаза I"

    with allure.step("Раунд 1/10"):
        assert battle_page.get_round() == "Раунд 1/10"
        assert battle_page.get_round_number() == 1

    with allure.step("Здоровье равно 10"):
        assert battle_page.get_health_value() == 10

    with allure.step("Накопленный урон равен 0"):
        assert battle_page.get_accumulated_damage_value() == 0

    with allure.step("Статус Обычный"):
        assert battle_page.get_status() == "Статус: Обычный"

    with allure.step(f"Прочность = {players_count} * урон_для_раны = {expected_toughness}"):
        assert battle_page.get_toughness_value() == expected_toughness

    with allure.step(f"Ярость = {expected_rage}"):
        assert battle_page.get_rage_value() == expected_rage

    with allure.step(f"Смена стойки = {expected_stance}"):
        assert battle_page.get_stance_change_value() == expected_stance

    with allure.step("Все кнопки управления видны"):
        for locator, name_loc in [
            (battle_page.DAMAGE_1, "+1"),
            (battle_page.DAMAGE_5, "+5"),
            (battle_page.DAMAGE_10, "+10"),
            (battle_page.DAMAGE_50, "+50"),
            (battle_page.ENTER_DAMAGE, "Ввести урон"),
            (battle_page.RAGE_MINUS_1, "-1 ярость"),
            (battle_page.RAGE_PLUS_1, "+1 ярость"),
            (battle_page.RAGE_PLUS_1_PER_HUNTER, "+1/охот"),
            (battle_page.END_ROUND, "Закончить раунд"),
            (battle_page.SURRENDER, "Сдаться"),
            (battle_page.EXIT_TO_MENU, "Выход в меню"),
        ]:
            assert battle_page.is_element_visible(locator), f"Кнопка '{name_loc}' не видна"


# endregion


# region 2. НАНЕСЕНИЕ УРОНА


@allure.feature("Экран боя — нанесение урона")
@pytest.mark.parametrize(
    "damage_fn, expected_damage, expected_health",
    [
        pytest.param(lambda bp: bp.apply_damage_manual("1"), 1, 10, id="+1"),
        pytest.param(lambda bp: bp.apply_damage_manual("5"), 5, 10, id="+5"),
        pytest.param(lambda bp: bp.apply_damage_manual("10"), 10, 10, id="+10"),
        pytest.param(lambda bp: bp.apply_damage_manual("50"), 50, 10, id="+50"),
    ]
)
def test_damage(driver, damage_fn, expected_damage: int, expected_health: int):
    """
    Наносит урон ручным вводом через диалог.
    Сложность 3 — урон_для_раны = 18, прочность = 4*18 = 72.
    Урон до 50 не превышает прочность, раны не наносятся.
    """
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("3")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    initial_damage = battle_page.get_accumulated_damage_value() or 0

    damage_fn(battle_page)

    assert battle_page.get_accumulated_damage_value() == initial_damage + expected_damage, \
        f"Накопленный урон: ожидалось {initial_damage + expected_damage}, получено {battle_page.get_accumulated_damage_value()}"
    assert battle_page.get_health_value() == expected_health, \
        f"Здоровье: ожидалось {expected_health}, получено {battle_page.get_health_value()}"


@allure.feature("Экран боя — нанесение урона")
def test_damage_cumulative(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("3")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    battle_page.apply_damage_manual("3")
    battle_page.apply_damage_manual("7")

    assert battle_page.get_accumulated_damage_value() == 10, \
        f"Накопленный урон после 3+7: {battle_page.get_accumulated_damage_value()}"
    assert battle_page.get_health_value() == 10


@allure.feature("Экран боя — нанесение урона")
def test_manual_damage_cancel(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()

    battle_page.click_enter_damage()
    assert battle_page.is_element_visible(battle_page.OK_BUTTON)
    assert battle_page.is_element_visible(battle_page.CANCEL_BUTTON)

    battle_page.click_cancel()
    assert battle_page.get_accumulated_damage_value() == 0


@allure.feature("Экран боя — нанесение урона")
def test_wound_infliction(driver):
    """Прочность = 4 * 2 = 8. Наносим 8 урона — одна рана."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    initial_health = battle_page.get_health_value()
    toughness = battle_page.get_toughness_value()
    assert initial_health is not None and toughness is not None

    battle_page.apply_damage_manual(str(toughness))

    assert battle_page.get_health_value() == initial_health - 1, \
        f"Здоровье должно уменьшиться на 1: {initial_health} -> {battle_page.get_health_value()}"
    assert battle_page.get_accumulated_damage_value() == 0, \
        f"Накопленный урон должен обнулиться после раны: {battle_page.get_accumulated_damage_value()}"


@allure.feature("Экран боя — нанесение урона")
def test_multiple_wounds(driver):
    """Прочность = 4 * 2 = 8. Наносим 16 → 2 раны, остаток 0."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    initial_health = battle_page.get_health_value()
    toughness = battle_page.get_toughness_value()
    assert initial_health is not None and toughness is not None

    battle_page.apply_damage_manual(str(toughness * 2))

    assert battle_page.get_health_value() == initial_health - 2, \
        f"Должно быть нанесено 2 раны: {initial_health} -> {battle_page.get_health_value()}"
    assert battle_page.get_accumulated_damage_value() == 0


@allure.feature("Экран боя — нанесение урона")
def test_damage_overflow_persists(driver):
    """Прочность = 4*2=8. Наносим 10 → 1 рана, остаток 2."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    initial_health = battle_page.get_health_value()
    toughness = battle_page.get_toughness_value()
    assert initial_health is not None and toughness is not None

    battle_page.apply_damage_manual("10")

    assert battle_page.get_health_value() == initial_health - 1
    assert battle_page.get_accumulated_damage_value() == 10 - toughness, \
        f"Остаток накопленного урона: {battle_page.get_accumulated_damage_value()}"


# endregion


# region 3. МЕХАНИКА ЯРОСТИ


@allure.feature("Экран боя — ярость")
@pytest.mark.parametrize(
    "players_count, expected_rage",
    [
        pytest.param("2", 2, id="2 охотника"),
        pytest.param("3", 3, id="3 охотника"),
        pytest.param("4", 4, id="4 охотника"),
    ]
)
def test_initial_rage(driver, players_count, expected_rage):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count(players_count)

    battle_page = battle_preparation.start_battle()

    assert battle_page.get_rage_value() == expected_rage


@allure.feature("Экран боя — ярость")
@pytest.mark.parametrize(
    "action_fn, delta",
    [
        pytest.param(lambda bp: bp.rage_plus_1(), 1, id="+1"),
        pytest.param(lambda bp: bp.rage_minus_1(), -1, id="-1"),
    ]
)
def test_rage_change(driver, action_fn, delta):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()

    initial_rage = battle_page.get_rage_value()
    assert initial_rage is not None

    action_fn(battle_page)

    assert battle_page.get_rage_value() == initial_rage + delta, \
        f"Ярость: ожидалось {initial_rage + delta}, получено {battle_page.get_rage_value()}"


@allure.feature("Экран боя — ярость")
def test_rage_per_hunter(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("3")

    battle_page = battle_preparation.start_battle()

    initial_rage = battle_page.get_rage_value()
    assert initial_rage is not None
    battle_page.rage_plus_1_per_hunter()

    assert battle_page.get_rage_value() == initial_rage + 3


@allure.feature("Экран боя — ярость")
def test_rage_per_hunter_minus_1(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("3")

    battle_page = battle_preparation.start_battle()

    initial_rage = battle_page.get_rage_value()
    assert initial_rage is not None
    battle_page.rage_plus_1_per_hunter_minus_1()

    assert battle_page.get_rage_value() == initial_rage + 2


@allure.feature("Экран боя — ярость")
def test_rage_end_round_increment(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("3")

    battle_page = battle_preparation.start_battle()

    initial_rage = battle_page.get_rage_value()
    assert initial_rage is not None
    battle_page.end_round()

    assert battle_page.get_rage_value() == initial_rage + 3


@allure.feature("Экран боя — ярость")
def test_rage_surge_on_end_round(driver):
    """Проверка, что при накоплении ярости в конце раунда появляется окно Rage Surge."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("2")

    battle_page = battle_preparation.start_battle()

    # 2 охотника: начальная ярость=2, порог surge=6 (2*3)
    # Добавляем +3 чтобы ярость стала 5, затем end_round даст +2 → 7 >= 6
    for _ in range(3):
        battle_page.rage_plus_1()

    assert not battle_page.is_element_visible_quick(RageSurgeDialog.MESSAGE), "Surge не должен появиться до конца раунда"

    battle_page.end_round()

    dialog = RageSurgeDialog(driver)
    assert dialog.is_displayed(), "Диалог Rage Surge должен появиться после конца раунда"
    dialog.click_ok()

    assert battle_page.get_rage_value() == 2, \
        f"Ярость должна сброситься до 2, получено {battle_page.get_rage_value()}"


@allure.feature("Экран боя — ярость")
@pytest.mark.parametrize(
    "players_count",
    [
        pytest.param("2", id="2 охотника"),
        pytest.param("4", id="4 охотника"),
    ]
)
def test_rage_surge(driver, players_count):
    """Проверка, что при rage >= hunters*3 появляется диалог Rage Surge
    и после подтверждения ярость сбрасывается до количества охотников."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count(players_count)

    battle_page = battle_preparation.start_battle()

    p = int(players_count)
    needed = p * 3 - p
    for _ in range(needed):
        battle_page.rage_plus_1()

    dialog = RageSurgeDialog(driver)
    assert dialog.is_displayed(), "Диалог Rage Surge не появился при достижении порога ярости"

    dialog.click_ok()

    assert battle_page.get_rage_value() == p, \
        f"После подтверждения surge ярость должна быть {p}, получено {battle_page.get_rage_value()}"


# endregion


# region 4. РАУНДЫ И ФАЗЫ


@allure.feature("Экран боя — раунды и фазы")
def test_end_round_advances_round(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()

    initial_round = battle_page.get_round_number()
    battle_page.end_round()

    assert battle_page.get_round_number() == initial_round + 1, \
        f"Раунд: ожидалось {initial_round + 1}, получено {battle_page.get_round_number()}"


@allure.feature("Экран боя — раунды и фазы")
def test_defeat_after_round_10(driver):
    """После 10 раундов наступает поражение."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("1")

    battle_page = battle_preparation.start_battle()

    surge = RageSurgeDialog(driver)

    for _ in range(9):
        battle_page.end_round()
        if surge.is_displayed():
            surge.click_ok()

    assert battle_page.get_round_number() == 10

    battle_page.end_round()

    defeat = DefeatDialog(driver)
    assert defeat.is_displayed(), "Окно ПОРАЖЕНИЕ должно отображаться после 10 раундов"


@allure.feature("Экран боя — раунды и фазы")
def test_victory_when_health_zero(driver):
    """После обнуления здоровья наступает победа."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()
    toughness = battle_page.get_toughness_value()
    health = battle_page.get_health_value()
    assert toughness is not None and health is not None

    battle_page.apply_damage_manual(str(health * toughness))

    victory = VictoryDialog(driver)
    assert victory.is_displayed(), "Окно ПОБЕДА! должно отображаться после обнуления здоровья"


# endregion


# region 5. СТАТУСЫ И УСТОЙЧИВОСТЬ


@allure.feature("Экран боя — статусы и устойчивость")
def test_toggle_hardened(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()
    assert battle_page.get_status() == "Статус: Обычный"

    for _ in range(2):
        battle_page.click_stability()

    assert battle_page.get_status() == "Статус: Обычный", \
        f"После двух кликов статус должен вернуться к Обычный: {battle_page.get_status()}"


@allure.feature("Экран боя — статусы и устойчивость")
def test_wound_normal_after_wound(driver):
    """Без hardened: после раны остаток накопленного урона сохраняется."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    toughness = battle_page.get_toughness_value()
    assert toughness is not None

    battle_page.apply_damage_manual(str(toughness + 3))

    assert battle_page.get_accumulated_damage_value() == 3, \
        f"Остаток накопленного урона: {battle_page.get_accumulated_damage_value()}"


@allure.feature("Экран боя — статусы и устойчивость")
def test_wound_hardened_burns_overflow(driver):
    """В режиме 'Ожесточение' урон свыше прочности сгорает (accumulatedDamage = 0)."""
    import time
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    with allure.step("Включаем режим Устойчивость"):
        battle_page.click_stability()
        status = battle_page.get_status()
        assert status == "Статус: Устойчивость", \
            f"Статус после переключения: {repr(status)}"

    toughness = battle_page.get_toughness_value()
    assert toughness is not None

    with allure.step(f"Наносим {toughness + 5} урона — 1 рана + 5 избытка"):
        battle_page.apply_damage_manual(str(toughness + 5))

    assert battle_page.get_accumulated_damage_value() == 0, \
        f"В режиме Устойчивость избыточный урон должен сгореть, получено {battle_page.get_accumulated_damage_value()}"


# endregion


# region 6. НАВИГАЦИЯ И ВЫХОД


@allure.feature("Экран боя — навигация и выход")
def test_exit_to_menu(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()

    returned_page = battle_page.exit_to_menu()

    assert returned_page.is_element_visible(returned_page.EXPEDITION), \
        "Должны вернуться на главный экран"


@allure.feature("Экран боя — навигация и выход")
def test_surrender(driver):
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")

    battle_page = battle_preparation.start_battle()

    dialog = battle_page.surrender()
    assert dialog.is_displayed(), "Окно подтверждения сдачи не отображается"

    defeat = dialog.confirm()

    assert defeat.is_displayed(), "Окно ПОРАЖЕНИЕ должно отображаться после подтверждения сдачи"


# endregion


# region 7. СМЕНА СТОЙКИ


@allure.feature("Экран боя — смена стойки")
def test_stance_change_auto_viraxen(driver):
    """
    Вираксен — авто-смена стойки при снижении здоровья до порога.
    Сложность 0: урон_для_раны=2, смена_стойки=7, 4 охотника.
    Наносим 24 урона (3 раны) → здоровье 10→7 → появляется StanceChangeDialog.
    """
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    toughness = battle_page.get_toughness_value()
    assert toughness is not None
    stance_threshold = battle_page.get_stance_change_value()
    assert stance_threshold is not None

    with allure.step(f"Наносим урон для снижения здоровья до {stance_threshold}"):
        current_health = battle_page.get_health_value()
        wounds_needed = current_health - stance_threshold
        if wounds_needed > 0:
            damage = wounds_needed * toughness
            battle_page.apply_damage_manual(str(damage))

    dialog = StanceChangeDialog(driver)
    assert dialog.is_displayed(), "StanceChangeDialog должен появиться при авто-смене стойки"

    with allure.step("Подтверждаем смену стойки"):
        dialog.click_ok()

    assert battle_page.get_phase_number() == 2, \
        f"После подтверждения смены ожидается Фаза II, получено {battle_page.get_phase()}"


@allure.feature("Экран боя — смена стойки")
def test_stance_change_cancel(driver):
    """Отмена смены стойки — диалог закрывается, бой продолжается."""
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Огонь - Вираксен")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    toughness = battle_page.get_toughness_value()
    stance_threshold = battle_page.get_stance_change_value()
    assert toughness is not None and stance_threshold is not None

    current_health = battle_page.get_health_value()
    wounds_needed = current_health - stance_threshold
    if wounds_needed > 0:
        battle_page.apply_damage_manual(str(wounds_needed * toughness))

    dialog = StanceChangeDialog(driver)
    assert dialog.is_displayed()

    dialog.click_cancel()

    assert battle_page.is_element_visible(battle_page.PHASE), "Экран боя должен остаться после отмены"


@allure.feature("Экран боя — смена стойки")
def test_stance_change_manual_iekorax(driver):
    """
    Иекорос — ручная смена стойки (healthForStanceChange = null).
    Нажимаем 'Сменить стойку', заполняем диалог, подтверждаем.
    """
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Молния - Иекорос")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    dialog = battle_page.click_change_stance()
    assert dialog.is_displayed(), "StanceChangeDialog должен открыться при ручной смене стойки"

    with allure.step("Подтверждаем смену стойки"):
        dialog.click_ok()

    assert battle_page.get_phase_number() == 2, \
        f"После ручной смены ожидается Фаза II, получено {battle_page.get_phase()}"


@allure.feature("Экран боя — смена стойки")
def test_stance_change_korowon_second_stance(driver):
    """
    Коралл - Коровон: на 1-й стойке авто-смена, кнопка 'Сменить стойку' отсутствует.
    На 2-й стойке нет порога ранения (damageForWound = null),
    появляется кнопка ручной смены стойки.
    Весь накопленный урон применяется при переходе на 3-ю стойку.
    """
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Коралл - Коровон")
    battle_preparation.set_complexity("0")
    battle_preparation.set_players_count("4")

    battle_page = battle_preparation.start_battle()

    assert battle_page.get_phase_number() == 1

    with allure.step("На 1-й стойке кнопка 'Сменить стойку' отсутствует"):
        assert not battle_page.is_element_visible_quick(battle_page.CHANGE_STANCE), \
            "На 1-й стойке кнопка 'Сменить стойку' не должна быть видна"

    with allure.step("Наносим урон до авто-смены стойки"):
        toughness = battle_page.get_toughness_value()
        stance_threshold = battle_page.get_stance_change_value()
        assert toughness is not None and stance_threshold is not None
        current_health = battle_page.get_health_value()
        wounds_needed = current_health - stance_threshold
        if wounds_needed > 0:
            battle_page.apply_damage_manual(str(wounds_needed * toughness))

    dialog = StanceChangeDialog(driver)
    assert dialog.is_displayed(), "StanceChangeDialog должен появиться для перехода на 2-ю стойку"
    dialog.click_ok()
    assert battle_page.get_phase_number() == 2

    with allure.step("На 2-й стойке кнопка 'Сменить стойку' видна"):
        assert battle_page.is_element_visible(battle_page.CHANGE_STANCE), \
            "На 2-й стойке кнопка 'Сменить стойку' должна быть видна"

    with allure.step("На 2-й стойке накапливаем урон (нет порога ранения)"):
        battle_page.apply_damage_manual("50")
        accumulated = battle_page.get_accumulated_damage_value()
        assert accumulated and accumulated > 0, "Урон должен накапливаться на 2-й стойке"

    with allure.step("Переходим на 3-ю стойку через ручную смену"):
        dialog = battle_page.click_change_stance()
        assert dialog.is_displayed()
        dialog.click_ok()
    assert battle_page.get_phase_number() == 3, \
        f"Ожидается Фаза III, получено {battle_page.get_phase()}"


@allure.feature("Экран боя — смена стойки")
@pytest.mark.parametrize(
    "complexity, players_count",
    [
        pytest.param("3", "1", id="1 охотник, сложность 3"),
    ]
)
def test_stance_change_awakened_five_stances(driver, complexity: str, players_count: str):
    """
    Пробуждённый — 5 стоек. Проверяем последовательное прохождение всех стоек.
    Сложность 3: урон_для_раны=30, смена_стойки=8.
    """
    main_page = MainPage(driver)
    battle_preparation = main_page.select_expedition()
    battle_preparation.select_boss("Пробуждённый")
    battle_preparation.set_complexity(complexity)
    battle_preparation.set_players_count(players_count)

    battle_page = battle_preparation.start_battle()

    assert battle_page.get_phase_number() == 1, "Должна быть Фаза I"

    for expected_phase in range(2, 6):
        toughness = battle_page.get_toughness_value()
        stance_threshold = battle_page.get_stance_change_value()
        assert toughness is not None and stance_threshold is not None

        current_health = battle_page.get_health_value()
        wounds_needed = current_health - stance_threshold if stance_threshold < current_health else 0

        with allure.step(f"Наносим {wounds_needed * toughness} урона для перехода на фазу {expected_phase}"):
            if wounds_needed > 0:
                battle_page.apply_damage_manual(str(wounds_needed * toughness))

        dialog = StanceChangeDialog(driver)
        assert dialog.is_displayed(), f"StanceChangeDialog должен появиться для смены на фазу {expected_phase}"

        with allure.step(f"Подтверждаем смену на фазу {expected_phase}"):
            dialog.click_ok()

        actual_phase = battle_page.get_phase_number()
        assert actual_phase == expected_phase, \
            f"Ожидается Фаза {expected_phase}, получено {battle_page.get_phase()}"

    with allure.step("После 5-й стойки проверяем состояние боя"):
        assert battle_page.is_element_visible(battle_page.PHASE), "Экран боя должен оставаться активным"


# endregion