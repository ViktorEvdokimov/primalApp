"""Тесты экрана боя (BattlePage), режим экспедиции.

Действия выполняются вне `check`: если шаг не удался, дальнейшие проверки теряют смысл и тест
должен упасть. Проверки обёрнуты в `with check(...)`, чтобы одна упавшая не скрывала остальные.
"""

import allure
import pytest
from pytest_check import check

from pages.battle_page import BattlePage, DefeatDialog, RageSurgeDialog, StanceChangeDialog, VictoryDialog
from pages.battle_preparation import BattlePreparation
from pages.main_page import MainPage

VIRAXEN = "Огонь - Вираксен"
INITIAL_HEALTH = 10


def prepare_battle(driver, boss: str | None = VIRAXEN, complexity: str = "0", players: str = "4",
                   damage_to_wound: str | None = None, stance_change: str | None = None) -> BattlePreparation:
    """Экран подготовки к экспедиции. boss=None — «Ввести данные вручную» (значения по умолчанию)."""
    prep = MainPage(driver).select_expedition()
    if boss:
        prep.select_boss(boss)
        prep.set_complexity(complexity)
    prep.set_players_count(players)
    if damage_to_wound is not None:
        prep.set_damage_to_wound(damage_to_wound)
    if stance_change is not None:
        prep.set_stance_change(stance_change)
    return prep


def start_battle(driver, **kwargs) -> BattlePage:
    return prepare_battle(driver, **kwargs).start_battle()


def start_manual_battle(driver, damage_to_wound: str = "2", players: str = "4") -> BattlePage:
    """Бой без босса из каталога, смена стойки «по запросу» — раны не вызывают автосмену стойки."""
    return start_battle(driver, boss=None, players=players, damage_to_wound=damage_to_wound, stance_change="")


def reach_stance_threshold(battle: BattlePage) -> None:
    """Нанести ровно столько ран, чтобы здоровье опустилось до порога смены стойки."""
    wounds = battle.get_health_value() - battle.get_stance_change_value()
    battle.apply_damage_manual(str(wounds * battle.get_toughness_value()))


# region 1. НАЧАЛЬНОЕ СОСТОЯНИЕ

@allure.feature("Экран боя — начальное состояние")
@pytest.mark.parametrize("players_count", ["2", "3", "4", "7"], ids=lambda p: f"охотников: {p}")  # 7 — без ограничения (D-12)
def test_initial_state(driver, players_count: str):
    prep = prepare_battle(driver, players=players_count)
    expected_toughness = int(players_count) * int(prep.get_damage_to_wound())
    expected_stance = int(prep.get_stance_change())
    battle = prep.start_battle()

    with check("Фаза I"):
        assert battle.get_phase() == "Фаза I"
    with check("Раунд 1/10"):
        assert battle.get_round() == "Раунд 1/10"
    with check("Здоровье 10"):
        assert battle.get_health_value() == INITIAL_HEALTH
    with check("Накопленный урон 0"):
        assert battle.get_accumulated_damage_value() == 0
    with check("Статус «Обычный»"):
        assert battle.get_status() == "Статус: Обычный"
    with check(f"Прочность = {players_count} × урон для раны = {expected_toughness}"):
        assert battle.get_toughness_value() == expected_toughness
    with check("Начальная ярость равна числу охотников (qa 74)"):
        assert battle.get_rage_value() == int(players_count)
    with check(f"Смена стойки при {expected_stance} HP"):
        assert battle.get_stance_change_value() == expected_stance
    for locator, name in [
        (battle.DAMAGE_1, "+1"), (battle.DAMAGE_5, "+5"), (battle.DAMAGE_10, "+10"), (battle.DAMAGE_50, "+50"),
        (battle.ENTER_DAMAGE, "Ввести урон"), (battle.RAGE_MINUS_1, "-1 ярость"), (battle.RAGE_PLUS_1, "+1 ярость"),
        (battle.RAGE_PLUS_1_PER_HUNTER, "+1/охот"), (battle.END_ROUND, "Закончить раунд"),
        (battle.SURRENDER, "Сдаться"), (battle.EXIT_TO_MENU, "Выход в меню"),
    ]:
        with check(f"Кнопка «{name}» видна"):
            assert battle.is_element_visible(locator)

# endregion


# region 2. НАНЕСЕНИЕ УРОНА

@allure.feature("Экран боя — нанесение урона")
def test_quick_damage_buttons(driver):
    """Быстрые кнопки: урон копится и применяется по таймеру 2 с или кнопкой «Применить урон сейчас».
    Сложность 3, 4 охотника: прочность 72 — раны не наносятся."""
    battle = start_battle(driver, complexity="3")

    battle.apply_damage_1()
    battle.apply_damage_5()
    battle.apply_damage_10()
    with check("Нажатия копятся в ожидающем уроне: 1 + 5 + 10"):
        assert battle.get_pending_damage_value() == 16
    with check("До срабатывания таймера урон не применён"):
        assert battle.get_accumulated_damage_value() == 0
    battle.wait_pending_damage_applied()
    with check("Через 2 с урон применился автоматически"):
        assert battle.get_accumulated_damage_value() == 16

    battle.apply_damage_50()
    battle.apply_pending_now()
    with check("«Применить урон сейчас» применяет ожидающий урон сразу"):
        assert battle.get_accumulated_damage_value() == 66
    with check("Ожидающего урона не осталось"):
        assert battle.get_pending_damage_value() is None
    with check("Урон меньше прочности — здоровье не изменилось"):
        assert battle.get_health_value() == INITIAL_HEALTH


@allure.feature("Экран боя — нанесение урона")
def test_manual_damage(driver):
    """Ручной ввод: «Отмена» сбрасывает ввод, урон накапливается. Сложность 3: прочность 72."""
    battle = start_battle(driver, complexity="3")

    battle.click_enter_damage()
    with check("Кнопки OK и «Отмена» видны"):
        assert battle.is_element_visible(battle.OK_BUTTON) and battle.is_element_visible(battle.CANCEL_BUTTON)
    battle.click_cancel()
    with check("После «Отмена» урон не нанесён"):
        assert battle.get_accumulated_damage_value() == 0

    battle.apply_damage_manual("3")
    battle.apply_damage_manual("7")
    with check("Урон накапливается: 3 + 7"):
        assert battle.get_accumulated_damage_value() == 10
    battle.apply_damage_manual("50")
    with check("Урон накапливается: 10 + 50"):
        assert battle.get_accumulated_damage_value() == 60
    with check("Урон меньше прочности — здоровье не изменилось"):
        assert battle.get_health_value() == INITIAL_HEALTH


@allure.feature("Экран боя — нанесение урона")
def test_wounds_and_victory(driver):
    """Раны, остаток урона и победа. Ручные данные: урон для раны 2 × 4 охотника = прочность 8,
    смена стойки «по запросу» (без автосмены)."""
    battle = start_manual_battle(driver)
    toughness = battle.get_toughness_value()
    with check("Прочность 8"):
        assert toughness == 8

    battle.apply_damage_manual(str(toughness))
    with check("Урон = прочности → 1 рана"):
        assert battle.get_health_value() == INITIAL_HEALTH - 1
    with check("После раны накопленный урон обнулился"):
        assert battle.get_accumulated_damage_value() == 0

    battle.apply_damage_manual(str(toughness * 2 + 3))
    with check("Урон = 2 × прочность + 3 → 2 раны за один удар"):
        assert battle.get_health_value() == INITIAL_HEALTH - 3
    with check("Остаток 3 сохраняется в накопленном уроне"):
        assert battle.get_accumulated_damage_value() == 3

    battle.apply_damage_manual(str(toughness - 3))
    with check("Остаток + новый урон достигли прочности → рана"):
        assert battle.get_health_value() == INITIAL_HEALTH - 4
    with check("Накопленный урон снова 0"):
        assert battle.get_accumulated_damage_value() == 0

    battle.apply_damage_manual(str(battle.get_health_value() * toughness))
    with check("Здоровье 0 → экран «ПОБЕДА!»"):
        assert VictoryDialog(driver).is_displayed()

# endregion


# region 3. ЯРОСТЬ И РАУНДЫ

@allure.feature("Экран боя — ярость")
def test_rage_buttons_and_surge(driver):
    """3 охотника: начальная ярость 3, порог всплеска 3 × 3 = 9."""
    battle = start_battle(driver, players="3")

    battle.end_round()
    with check("«Закончить раунд» — следующий раунд"):
        assert battle.get_round_number() == 2
    with check("Конец раунда: +1 ярость за каждого охотника (3 → 6)"):
        assert battle.get_rage_value() == 6

    battle.rage_minus_1()
    with check("«-1»: 6 → 5"):
        assert battle.get_rage_value() == 5
    battle.rage_plus_1()
    with check("«+1»: 5 → 6"):
        assert battle.get_rage_value() == 6
    battle.rage_plus_1_per_hunter_minus_1()
    with check("«+1/охот-1»: 6 → 8"):
        assert battle.get_rage_value() == 8
    with check("Ниже порога 9 выплеска нет"):
        assert not battle.is_element_visible_quick(RageSurgeDialog.MESSAGE)

    battle.rage_plus_1_per_hunter()
    surge = RageSurgeDialog(driver)
    with check("«+1/охот»: 8 → 11 ≥ 9 → окно «Выплеск ярости»"):
        assert surge.is_displayed()
    with check("Окно напоминает: каждый охотник получает урон, равный силе монстра (R-8)"):
        assert surge.has_damage_hint()
    surge.click_ok()
    with check("После выплеска ярость сбрасывается до числа охотников"):
        assert battle.get_rage_value() == 3


@allure.feature("Экран боя — ярость")
def test_rage_surge_on_end_round(driver):
    """2 охотника: ярость 2 → 5 кнопками, конец раунда +2 → 7 ≥ 6 — выплеск."""
    battle = start_battle(driver, players="2")
    for _ in range(3):
        battle.rage_plus_1()
    with check("Ярость 5 ниже порога 6 — выплеска нет"):
        assert not battle.is_element_visible_quick(RageSurgeDialog.MESSAGE)

    battle.end_round()
    surge = RageSurgeDialog(driver)
    with check("Выплеск после конца раунда"):
        assert surge.is_displayed()
    surge.click_ok()
    with check("Ярость сброшена до 2"):
        assert battle.get_rage_value() == 2


@allure.feature("Экран боя — раунды и фазы")
def test_defeat_after_round_10(driver):
    battle = start_battle(driver, players="1")
    surge = RageSurgeDialog(driver)
    for _ in range(9):
        battle.end_round()
        surge.dismiss_if_shown()
    with check("Раунд 10"):
        assert battle.get_round_number() == 10

    battle.end_round()
    surge.dismiss_if_shown()
    defeat = DefeatDialog(driver)
    with check("После 10-го раунда — экран «ПОРАЖЕНИЕ»"):
        assert defeat.is_displayed()
    with check("Причина: «Закончились раунды...» (D-14)"):
        assert defeat.is_rounds_over_reason_displayed()


@allure.feature("Экран боя — раунды и фазы")
def test_undo_end_round(driver):
    """«Отменить действие» после «Закончить раунд» возвращает номер раунда и ярость (D-4)."""
    battle = start_battle(driver, players="2")
    battle.end_round()
    with check("Раунд 2, ярость 4"):
        assert battle.get_round_number() == 2 and battle.get_rage_value() == 4

    battle.undo()
    with check("Отмена возвращает раунд 1"):
        assert battle.get_round_number() == 1
    with check("Отмена возвращает ярость 2"):
        assert battle.get_rage_value() == 2

# endregion


# region 4. СТАТУСЫ МОНСТРА И ЗАЖИВЛЕНИЕ РАНЫ

@allure.feature("Экран боя — статусы монстра")
def test_hardened_status(driver):
    """«Затвердевший» сжигает остаток урона после раны; без него остаток сохраняется. Прочность 8."""
    battle = start_manual_battle(driver)
    toughness = battle.get_toughness_value()

    battle.click_hardened()
    with check("Переключатель включает статус «Затвердевший»"):
        assert battle.get_status() == "Статус: Затвердевший"
    with check("«Устойчивость стойки» не включается"):
        assert not battle.is_resilient_on()
    battle.apply_damage_manual(str(toughness + 5))
    with check("Затвердевший: рана нанесена"):
        assert battle.get_health_value() == INITIAL_HEALTH - 1
    with check("Затвердевший: остаток 5 сгорел"):
        assert battle.get_accumulated_damage_value() == 0

    battle.click_hardened()
    with check("Повторное переключение возвращает статус «Обычный»"):
        assert battle.get_status() == "Статус: Обычный"
    battle.apply_damage_manual(str(toughness + 3))
    with check("Обычный статус: рана нанесена"):
        assert battle.get_health_value() == INITIAL_HEALTH - 2
    with check("Обычный статус: остаток 3 сохранился"):
        assert battle.get_accumulated_damage_value() == 3


@allure.feature("Экран боя — статусы монстра")
def test_resilient_status(driver):
    """«Устойчивость стойки»: перенесённый урон сбрасывается при смене стойки (R-3).
    Ручные данные: прочность 2 × 4 = 8, смена стойки при 9 HP — 11 урона дают рану и 3 урона переноса."""
    battle = start_battle(driver, boss=None, damage_to_wound="2", stance_change="9")
    stance_dialog = StanceChangeDialog(driver)

    battle.click_resilient()
    with check("Статус «Устойчивость стойки»"):
        assert battle.get_status() == "Статус: Устойчивость стойки"
    with check("«Затвердевший» не включается"):
        assert not battle.is_hardened_on()

    battle.apply_damage_manual("11")
    assert stance_dialog.is_displayed(), "Порог 9 HP — диалог смены стойки (данных стойки нет)"
    stance_dialog.set_damage_to_wound("2")
    stance_dialog.click_ok()
    with check("Фаза II"):
        assert battle.get_phase_number() == 2
    with check("Здоровье 9 — одна рана"):
        assert battle.get_health_value() == INITIAL_HEALTH - 1
    with check("Перенесённые 3 урона сброшены"):
        assert battle.get_accumulated_damage_value() == 0


@allure.feature("Экран боя — статусы монстра")
@pytest.mark.parametrize("title, fragment", [
    ("Затвердевший", "сбрасывается"),
    ("Устойчивость стойки", "не переносится на новую карту стойки"),
], ids=["Затвердевший", "Устойчивость стойки"])
def test_status_info(driver, title: str, fragment: str):
    """Кнопка «i» в сером кружке открывает описание статуса (R-3)."""
    battle = start_manual_battle(driver)
    info = battle.open_status_info(title)
    with check(f"Открыто описание «{title}»"):
        assert info.is_displayed(title)
    with check("Описание объясняет действие статуса"):
        assert info.has_text(fragment)
    info.close()
    with check("После «Понятно» — снова экран боя"):
        assert battle.get_health_value() == INITIAL_HEALTH


@allure.feature("Экран боя — заживление раны")
def test_heal_wound(driver):
    """«Заживить рану (+1 здоровья)» — +1 здоровья, жетоны урона не меняются, не выше 10 (R-4)."""
    battle = start_manual_battle(driver)
    battle.apply_damage_manual(str(battle.get_toughness_value() + 3))
    with check("Рана нанесена, 3 урона накоплено"):
        assert battle.get_health_value() == INITIAL_HEALTH - 1 and battle.get_accumulated_damage_value() == 3

    battle.heal_wound()
    with check("Здоровье +1"):
        assert battle.get_health_value() == INITIAL_HEALTH
    with check("Накопленный урон не изменился"):
        assert battle.get_accumulated_damage_value() == 3

    battle.heal_wound()
    with check("Здоровье не поднимается выше начального"):
        assert battle.get_health_value() == INITIAL_HEALTH


@allure.feature("Экран боя — заживление раны")
def test_negative_damage_reduces_only_accumulated(driver):
    """Отрицательный урон уменьшает только накопленный урон; при 0 — ничего не делает (D-3)."""
    battle = start_manual_battle(driver)
    with check("Под полем ввода есть подсказка про отрицательное значение"):
        assert battle.is_element_visible(battle.NEGATIVE_DAMAGE_HINT)
    battle.apply_damage_manual("5")
    battle.apply_damage_manual("-3")
    with check("Накопленный урон 5 − 3 = 2"):
        assert battle.get_accumulated_damage_value() == 2
    battle.apply_damage_manual("-7")
    with check("Накопленный урон не уходит ниже 0"):
        assert battle.get_accumulated_damage_value() == 0
    with check("Здоровье не изменилось"):
        assert battle.get_health_value() == INITIAL_HEALTH

# endregion


# region 5. НАВИГАЦИЯ И ВЫХОД

@allure.feature("Экран боя — навигация и выход")
def test_exit_resume_and_surrender(driver):
    """Выход в меню не сбрасывает бой («Вернуться к бою»), «Сдаться» с подтверждением — поражение."""
    battle = start_battle(driver, complexity="3")
    battle.apply_damage_manual("5")

    main_page = battle.exit_to_menu()
    with check("«Выход в меню» — главное меню"):
        assert main_page.is_element_visible(main_page.EXPEDITION)
    with check("В меню есть «Вернуться к бою»"):
        assert main_page.is_resume_battle_visible()

    battle = main_page.resume_battle()
    with check("Состояние боя сохранилось после возврата"):
        assert battle.get_accumulated_damage_value() == 5

    dialog = battle.surrender()
    with check("Окно подтверждения «Сдаться?»"):
        assert dialog.is_displayed()
    defeat = dialog.confirm()
    with check("После подтверждения — экран «ПОРАЖЕНИЕ»"):
        assert defeat.is_displayed()
    with check("Причина: «Вы сдались.» вместо «Закончились раунды...» (D-14)"):
        assert defeat.is_surrender_reason_displayed()

# endregion


# region 6. СМЕНА СТОЙКИ

@allure.feature("Экран боя — смена стойки")
def test_stance_change_auto(driver):
    """Вираксен, сложность 0: при пороге здоровья открывается «Смена стойки!» с полями стойки II
    из базы боссов. «Отмена» откатывает урон, «OK» применяет стойку II."""
    battle = start_battle(driver)
    stance_dialog = StanceChangeDialog(driver)
    toughness_1 = battle.get_toughness_value()

    reach_stance_threshold(battle)
    assert stance_dialog.is_displayed(), "При пороге здоровья появляется «Смена стойки!»"
    with check("Поля заполнены из базы боссов"):
        assert stance_dialog.is_from_boss_data()
    prefilled_dfw = stance_dialog.get_damage_to_wound().strip()
    with check(f"Урон для раны стойки II предзаполнен: «{prefilled_dfw}»"):
        assert prefilled_dfw.isdigit()
    stance_dialog.click_cancel()
    with check("«Отмена» откатывает урон: здоровье 10, фаза I"):
        assert battle.get_health_value() == INITIAL_HEALTH and battle.get_phase_number() == 1

    reach_stance_threshold(battle)
    assert stance_dialog.is_displayed(), "Диалог появляется повторно"
    stance_dialog.click_ok()
    with check("OK — фаза II"):
        assert battle.get_phase_number() == 2
    with check(f"Прочность стойки II = 4 охотника × {prefilled_dfw}"):
        assert battle.get_toughness_value() == 4 * int(prefilled_dfw or 0)
    with check("Прочность стойки II отличается от стойки I"):
        assert battle.get_toughness_value() != toughness_1


@allure.feature("Экран боя — смена стойки")
def test_stance_change_dialog_manual_data(driver):
    """Ручные данные (прочность 2 × 4 = 8, порог 7): параметры новой стойки неизвестны — диалог.
    Цикл ран останавливается на пороге, остаток переносится и наносится с новой прочностью (R-2)."""
    battle = start_battle(driver, boss=None, damage_to_wound="2", stance_change="7")
    stance_dialog = StanceChangeDialog(driver)

    reach_stance_threshold(battle)
    with check("При пороге здоровья появляется «Смена стойки!»"):
        assert stance_dialog.is_displayed()
    with check("Данных о стойке нет — поля пустые"):
        assert stance_dialog.is_without_boss_data()
    stance_dialog.click_cancel()
    with check("«Отмена» откатывает урон: здоровье 10"):
        assert battle.get_health_value() == INITIAL_HEALTH
    with check("«Отмена» оставляет фазу I"):
        assert battle.get_phase_number() == 1

    battle.apply_damage_manual("29")  # 3 раны до порога 7 + 5 урона переноса
    assert stance_dialog.is_displayed(), "Диалог появляется повторно"
    with check("Диалог сообщает о перенесённом уроне 5"):
        assert "Перенесённый урон 5 " in (stance_dialog.get_carried_damage_text() or "")
    stance_dialog.set_damage_to_wound("1")
    stance_dialog.set_stance_change_health("3")
    stance_dialog.click_ok()
    with check("OK — фаза II, прочность 1 × 4 = 4"):
        assert battle.get_phase_number() == 2 and battle.get_toughness_value() == 4
    with check("Перенесённые 5 урона → 1 рана с новой прочностью, остаток 1"):
        assert battle.get_health_value() == 6 and battle.get_accumulated_damage_value() == 1


@allure.feature("Экран боя — смена стойки")
def test_stance_change_manual_iekoros(driver):
    """Иекорос — смена стойки только по кнопке (порог здоровья не задан); поля — из базы боссов."""
    battle = start_battle(driver, boss="Молния - Иекорос")
    with check("Смена стойки «по запросу»"):
        assert battle.get_stance_change() == "Смена стойки: по запросу"

    dialog = battle.click_change_stance()
    assert dialog.is_displayed(), "«Сменить стойку» открывает диалог"
    with check("Поля заполнены из базы боссов"):
        assert dialog.is_from_boss_data()
    dialog.click_ok()
    with check("Фаза II"):
        assert battle.get_phase_number() == 2


@allure.feature("Экран боя — смена стойки")
def test_stance_change_korowon_second_stance(driver):
    """Коровон: на 2-й стойке нет порога раны — урон только копится и наносится при переходе на 3-ю.
    Параметры стоек подтверждаются в диалоге, поля заполнены из базы боссов."""
    battle = start_battle(driver, boss="Коралл - Коровон")
    dialog = StanceChangeDialog(driver)
    with check("1-я стойка: кнопки «Сменить стойку» нет"):
        assert not battle.is_element_visible_quick(battle.CHANGE_STANCE)

    reach_stance_threshold(battle)
    assert dialog.is_displayed(), "Диалог смены на 2-ю стойку"
    with check("2-я стойка из базы боссов: порога раны нет — поле пустое"):
        assert dialog.is_from_boss_data() and dialog.get_damage_to_wound().strip() == ""
    dialog.click_ok()
    with check("Фаза II"):
        assert battle.get_phase_number() == 2
    with check("2-я стойка: кнопка «Сменить стойку» видна"):
        assert battle.is_element_visible(battle.CHANGE_STANCE)
    with check("2-я стойка: прочность «нет»"):
        assert battle.get_toughness() == "Прочность: нет"

    health_before = battle.get_health_value()
    battle.apply_damage_manual("50")
    with check("2-я стойка: урон копится"):
        assert battle.get_accumulated_damage_value() == 50
    with check("2-я стойка: раны не наносятся"):
        assert battle.get_health_value() == health_before

    dialog = battle.click_change_stance()
    assert dialog.is_displayed(), "Диалог смены на 3-ю стойку"
    with check("Диалог сообщает о перенесённом уроне 50"):
        assert "Перенесённый урон 50 " in (dialog.get_carried_damage_text() or "")
    dialog.click_ok()
    with check("Фаза III"):
        assert battle.get_phase_number() == 3
    with check("При переходе на 3-ю стойку накопленный урон пересчитан в раны"):
        assert battle.get_accumulated_damage_value() < 50 and battle.get_health_value() < health_before


@allure.feature("Экран боя — смена стойки")
def test_stance_change_awakened_five_stances(driver):
    """Пробуждённый (сложность 3, 1 охотник): 5 стоек подряд, поля каждой — из базы боссов, затем победа."""
    battle = start_battle(driver, boss="Пробуждённый", complexity="3", players="1")
    dialog = StanceChangeDialog(driver)
    with check("Фаза I"):
        assert battle.get_phase_number() == 1

    for expected_phase in range(2, 6):
        reach_stance_threshold(battle)
        assert dialog.is_displayed(), f"Диалог смены на фазу {expected_phase}"
        with check(f"Фаза {expected_phase}: поля из базы боссов"):
            assert dialog.is_from_boss_data()
        dialog.click_ok()
        with check(f"Фаза {expected_phase}"):
            assert battle.get_phase_number() == expected_phase

    battle.apply_damage_manual(str(battle.get_health_value() * battle.get_toughness_value()))
    with check("На последней стойке здоровье 0 → «ПОБЕДА!»"):
        assert VictoryDialog(driver).is_displayed()

# endregion
