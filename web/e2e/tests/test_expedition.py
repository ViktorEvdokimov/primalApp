"""Экспедиция без входа: подготовка и экран боя.

Сценарии перенесены из UI-тестов мобильного приложения (test/battle_tests). Действия выполняются вне
`check`: если шаг не удался, дальнейшие проверки теряют смысл. Проверки обёрнуты в `with check(...)`,
чтобы одна упавшая не скрывала остальные.
"""

import allure
import pytest
from playwright.sync_api import Page
from pytest_check import check

from pages.battle_page import BattlePage, RageSurgeDialog, ResultPage, StanceChangeDialog
from pages.expedition_page import ExpeditionPage
from pages.main_page import MainPage

VIRAXEN = "Огонь - Вираксен"
INITIAL_HEALTH = 10


def prepare_battle(page: Page, boss: str | None = VIRAXEN, difficulty: str = "0", players: str = "4",
                   damage_to_wound: str | None = None, stance_change: str | None = None) -> ExpeditionPage:
    """Подготовка экспедиции. boss=None — «Ввести данные вручную» (значения по умолчанию)."""
    main_page = MainPage(page).open()
    prep = main_page.select_expedition()
    if boss:
        prep.select_boss(boss)
        prep.set_difficulty(difficulty)
    prep.set_hunters(players)
    if damage_to_wound is not None:
        prep.set_toughness(damage_to_wound)
    if stance_change is not None:
        prep.set_stance_change(stance_change)
    return prep


def start_battle(page: Page, **kwargs) -> BattlePage:
    return prepare_battle(page, **kwargs).start_battle()


def start_manual_battle(page: Page, damage_to_wound: str = "2", players: str = "4") -> BattlePage:
    """Бой без босса из каталога, смена стойки «по запросу» — раны не вызывают автосмену стойки."""
    return start_battle(page, boss=None, players=players, damage_to_wound=damage_to_wound, stance_change="")


def reach_stance_threshold(battle: BattlePage) -> None:
    """Нанести ровно столько ран, чтобы здоровье опустилось до порога смены стойки."""
    wounds = battle.get_health_value() - battle.get_stance_change_value()
    battle.apply_damage_manual(str(wounds * battle.get_toughness_value()))


@allure.feature("Экспедиция — подготовка к бою")
class TestPreparation:

    BOSS_CHARACTERISTICS = [
        ("Огонь - Вираксен", "0", "2", "7"),
        ("Огонь - Вираксен", "1", "5", "7"),
        ("Огонь - Вираксен", "2", "10", "7"),
        ("Огонь - Вираксен", "3", "18", "7"),
        ("Рог - Дигоракс", "2", "9", "8"),
        ("Рог - Торамат", "2", "10", "7"),
        ("Пробуждённый", "3", "30", "8"),
    ]

    @allure.title("Характеристики боссов подставляются по боссу и сложности")
    def test_bosses_characteristics(self, page: Page):
        # подготовка
        prep = MainPage(page).open().select_expedition()

        # вызов и проверка
        for name, difficulty, damage_to_wound, stance_change in self.BOSS_CHARACTERISTICS:
            prep.select_boss(name)
            prep.set_difficulty(difficulty)
            with check(f"{name}, сложность {difficulty}: урон для раны {damage_to_wound}"):
                assert prep.get_toughness() == damage_to_wound
            with check(f"{name}, сложность {difficulty}: смена стойки при {stance_change}"):
                assert prep.get_stance_change() == stance_change

    @allure.title("У Пробуждённого единственная сложность — 3")
    def test_awakened_single_difficulty(self, page: Page):
        # подготовка
        prep = MainPage(page).open().select_expedition()

        # вызов
        prep.select_boss("Пробуждённый")

        # проверка
        with check("Колода реакций уровня 3"):
            assert prep.get_reaction_deck_level() == 3
        for level in ("0", "1", "2"):
            with check(f"Сложность {level} недоступна"):
                assert not prep.is_difficulty_enabled(level)

    @allure.title("Под сложностью — колода карт реакций выбранного уровня")
    def test_reaction_deck_hint(self, page: Page):
        # подготовка
        prep = MainPage(page).open().select_expedition()
        with check("По умолчанию сложность 0 — колода 0-го уровня"):
            assert prep.get_reaction_deck_level() == 0

        # вызов и проверка
        prep.select_boss(VIRAXEN)
        for difficulty in ("1", "2", "3", "0"):
            prep.set_difficulty(difficulty)
            with check(f"Сложность {difficulty} — колода {difficulty}-го уровня"):
                assert prep.get_reaction_deck_level() == int(difficulty)

    @allure.title("Без количества охотников бой не начинается")
    def test_players_count_required(self, page: Page):
        # подготовка
        prep = MainPage(page).open().select_expedition()
        with check("Количество охотников предзаполнено"):
            assert prep.get_hunters() == "4"
        prep.select_boss("Металл - Юром")

        # вызов
        prep.set_hunters("")
        prep.click_start_expecting_error()

        # проверка
        with check("Экран подготовки остаётся открытым"):
            prep.should_be_open()
        with check("Бой не начался"):
            assert not page.get_by_test_id(BattlePage.PHASE).is_visible()


@allure.feature("Экспедиция — экран боя")
class TestBattle:

    @allure.title("Начальное состояние боя: {players_count} охотника(ов)")
    @pytest.mark.parametrize("players_count", ["2", "3", "4", "7"], ids=lambda p: f"охотников: {p}")
    def test_initial_state(self, page: Page, players_count: str):
        # подготовка
        prep = prepare_battle(page, players=players_count)
        expected_toughness = int(players_count) * int(prep.get_toughness())
        expected_stance = int(prep.get_stance_change())

        # вызов
        battle = prep.start_battle()

        # проверка
        with check("Фаза I"):
            assert battle.get_phase_number() == 1
        with check("Раунд 1/10"):
            assert battle.text(BattlePage.ROUND) == "Раунд 1/10"
        with check("Здоровье 10"):
            assert battle.get_health_value() == INITIAL_HEALTH
        with check("Накопленный урон 0"):
            assert battle.get_accumulated_damage_value() == 0
        with check("Статус «Обычный»"):
            assert battle.get_status() == "Статус: Обычный"
        with check(f"Прочность = {players_count} × урон для раны = {expected_toughness}"):
            assert battle.get_toughness_value() == expected_toughness
        with check("Начальная ярость равна числу охотников"):
            assert battle.get_rage_value() == int(players_count)
        with check(f"Смена стойки при {expected_stance} HP"):
            assert battle.get_stance_change_value() == expected_stance
        for test_id in ("battle-quick-1", "battle-quick-5", "battle-quick-10", "battle-quick-50", BattlePage.DAMAGE_INPUT,
                        BattlePage.RAGE_MINUS_1, BattlePage.RAGE_PLUS_1, BattlePage.RAGE_PER_HUNTER,
                        BattlePage.END_ROUND, BattlePage.SURRENDER, BattlePage.EXIT_TO_MENU):
            with check(f"Элемент {test_id} виден"):
                assert battle.is_visible(test_id)

    @allure.title("Быстрые кнопки: урон применяется через 2 с или кнопкой «Применить урон сейчас»")
    def test_quick_damage_buttons(self, page: Page):
        # подготовка: сложность 3, 4 охотника — прочность 72, раны не наносятся
        battle = start_battle(page, difficulty="3")

        # вызов
        battle.press_quick(1)
        battle.press_quick(5)
        battle.press_quick(10)

        # проверка
        with check("Нажатия копятся: 1 + 5 + 10"):
            assert battle.get_pending_damage_value() == 16
        with check("До срабатывания таймера урон не применён"):
            assert battle.get_accumulated_damage_value() == 0
        battle.wait_pending_damage_applied()
        with check("Через 2 с урон применился автоматически"):
            assert battle.get_accumulated_damage_value() == 16

        battle.press_quick(50)
        battle.apply_pending_now()
        with check("«Применить урон сейчас» применяет урон сразу"):
            assert battle.get_accumulated_damage_value() == 66
        with check("Ожидающего урона не осталось"):
            assert battle.get_pending_damage_value() is None
        with check("Урон меньше прочности — здоровье не изменилось"):
            assert battle.get_health_value() == INITIAL_HEALTH

    @allure.title("Ручной ввод: «Отмена» сбрасывает ввод, урон накапливается")
    def test_manual_damage(self, page: Page):
        # подготовка
        battle = start_battle(page, difficulty="3")
        battle.by_test_id(BattlePage.DAMAGE_INPUT).fill("9")

        # вызов
        battle.click(BattlePage.CANCEL)

        # проверка
        with check("После «Отмена» урон не нанесён"):
            assert battle.get_accumulated_damage_value() == 0
        battle.apply_damage_manual("3")
        battle.apply_damage_manual("7")
        with check("Урон накапливается: 3 + 7"):
            assert battle.get_accumulated_damage_value() == 10
        battle.apply_damage_manual("50")
        with check("Урон накапливается: 10 + 50"):
            assert battle.get_accumulated_damage_value() == 60

    @allure.title("Раны, остаток урона и победа")
    def test_wounds_and_victory(self, page: Page):
        # подготовка: прочность 2 × 4 = 8, смена стойки по запросу
        battle = start_manual_battle(page)
        toughness = battle.get_toughness_value()
        with check("Прочность 8"):
            assert toughness == 8

        # вызов и проверка
        battle.apply_damage_manual(str(toughness))
        with check("Урон = прочности → 1 рана, накопленный урон 0"):
            assert battle.get_health_value() == 9 and battle.get_accumulated_damage_value() == 0
        battle.apply_damage_manual(str(toughness * 2 + 3))
        with check("2 × прочность + 3 → 2 раны, остаток 3"):
            assert battle.get_health_value() == 7 and battle.get_accumulated_damage_value() == 3
        battle.apply_damage_manual(str(toughness - 3))
        with check("Остаток + урон достигли прочности → рана"):
            assert battle.get_health_value() == 6 and battle.get_accumulated_damage_value() == 0
        battle.apply_damage_manual(str(battle.get_health_value() * toughness))
        result = ResultPage(page)
        result.should_be_open()
        with check("Здоровье 0 → «ПОБЕДА!»"):
            assert result.result() == "VICTORY"

    @allure.title("Кнопки ярости и выплеск ярости")
    def test_rage_buttons_and_surge(self, page: Page):
        # подготовка: 3 охотника, ярость 3, порог выплеска 9
        battle = start_battle(page, players="3")
        surge = RageSurgeDialog(page)

        # вызов и проверка
        battle.end_round()
        with check("Конец раунда: раунд 2, ярость 3 → 6"):
            assert battle.get_round_number() == 2 and battle.get_rage_value() == 6
        battle.rage(BattlePage.RAGE_MINUS_1)
        with check("«-1»: 6 → 5"):
            assert battle.get_rage_value() == 5
        battle.rage(BattlePage.RAGE_PLUS_1)
        with check("«+1»: 5 → 6"):
            assert battle.get_rage_value() == 6
        battle.rage(BattlePage.RAGE_PER_HUNTER_MINUS_1)
        with check("«+1/охот-1»: 6 → 8, выплеска нет"):
            assert battle.get_rage_value() == 8 and not surge.is_displayed(timeout=500)
        battle.rage(BattlePage.RAGE_PER_HUNTER)
        with check("«+1/охот»: 8 → 11 ≥ 9 → «Выплеск ярости»"):
            assert surge.is_displayed()
        with check("Окно напоминает про урон охотникам (R-8)"):
            assert surge.has_damage_hint()
        surge.click_ok()
        with check("Ярость сброшена до числа охотников"):
            assert battle.get_rage_value() == 3

    @allure.title("Выплеск ярости после конца раунда")
    def test_rage_surge_on_end_round(self, page: Page):
        # подготовка: 2 охотника, ярость 2 → 5
        battle = start_battle(page, players="2")
        for _ in range(3):
            battle.rage(BattlePage.RAGE_PLUS_1)
        surge = RageSurgeDialog(page)

        # вызов
        battle.end_round()

        # проверка
        with check("5 + 2 = 7 ≥ 6 — выплеск"):
            assert surge.is_displayed()
        surge.click_ok()
        with check("Ярость сброшена до 2"):
            assert battle.get_rage_value() == 2

    @allure.title("После 10-го раунда — поражение «Закончились раунды...»")
    def test_defeat_after_round_10(self, page: Page):
        # подготовка
        battle = start_battle(page, players="1")
        surge = RageSurgeDialog(page)
        for _ in range(9):
            battle.end_round()
            surge.dismiss_if_shown()
        with check("Раунд 10"):
            assert battle.get_round_number() == 10

        # вызов
        battle.end_round()

        # проверка
        result = ResultPage(page)
        result.should_be_open()
        with check("Поражение по раундам (D-14)"):
            assert result.result() == "DEFEAT" and result.reason() == "Закончились раунды..."

    @allure.title("«Отменить действие» после конца раунда возвращает раунд и ярость (D-4)")
    def test_undo_end_round(self, page: Page):
        # подготовка
        battle = start_battle(page, players="2")
        battle.end_round()
        with check("Раунд 2, ярость 4"):
            assert battle.get_round_number() == 2 and battle.get_rage_value() == 4

        # вызов
        battle.undo()

        # проверка
        with check("Раунд 1, ярость 2"):
            assert battle.get_round_number() == 1 and battle.get_rage_value() == 2

    @allure.title("«Затвердевший» сжигает остаток урона после раны")
    def test_hardened_status(self, page: Page):
        # подготовка: прочность 8
        battle = start_manual_battle(page)
        toughness = battle.get_toughness_value()

        # вызов и проверка
        battle.click_hardened()
        with check("Статус «Затвердевший», «Устойчивость» выключена"):
            assert battle.get_status() == "Статус: Затвердевший" and not battle.is_resilient_on()
        battle.apply_damage_manual(str(toughness + 5))
        with check("Рана нанесена, остаток 5 сгорел"):
            assert battle.get_health_value() == 9 and battle.get_accumulated_damage_value() == 0
        battle.click_hardened()
        with check("Статус снова «Обычный»"):
            assert battle.get_status() == "Статус: Обычный"
        battle.apply_damage_manual(str(toughness + 3))
        with check("Без статуса остаток 3 сохранился"):
            assert battle.get_health_value() == 8 and battle.get_accumulated_damage_value() == 3

    @allure.title("«Устойчивость стойки» сбрасывает перенесённый урон при смене стойки (R-3)")
    def test_resilient_status(self, page: Page):
        # подготовка: прочность 2 × 4 = 8, смена при 9 — 11 урона дают рану и 3 урона переноса
        battle = start_battle(page, boss=None, damage_to_wound="2", stance_change="9")
        stance_dialog = StanceChangeDialog(page)
        battle.click_resilient()
        with check("Статус «Устойчивость стойки»"):
            assert battle.get_status() == "Статус: Устойчивость стойки" and not battle.is_hardened_on()

        # вызов
        battle.apply_damage_manual("11")
        assert stance_dialog.is_displayed(), "Порог 9 HP — окно смены стойки"
        stance_dialog.set_damage_to_wound("2")
        stance_dialog.click_ok()

        # проверка
        with check("Фаза II, одна рана, перенесённые 3 урона сброшены"):
            assert battle.get_phase_number() == 2
            assert battle.get_health_value() == 9
            assert battle.get_accumulated_damage_value() == 0

    @allure.title("Кнопка «i» открывает описание статуса «{status}»")
    @pytest.mark.parametrize("status, fragment", [
        ("hardened", "сбрасывается"),
        ("resilient", "не переносится на новую карту стойки"),
    ], ids=["Затвердевший", "Устойчивость стойки"])
    def test_status_info(self, page: Page, status: str, fragment: str):
        # подготовка
        battle = start_manual_battle(page)

        # вызов
        info = battle.open_status_info(status)

        # проверка
        with check("Описание объясняет действие статуса"):
            assert fragment in info.text()
        info.close()
        with check("После «Понятно» — снова экран боя"):
            assert battle.get_health_value() == INITIAL_HEALTH

    @allure.title("«Заживить рану» — +1 здоровья, накопленный урон не меняется (R-4)")
    def test_heal_wound(self, page: Page):
        # подготовка
        battle = start_manual_battle(page)
        battle.apply_damage_manual(str(battle.get_toughness_value() + 3))

        # вызов и проверка
        battle.heal_wound()
        with check("Здоровье 10, накопленный урон 3"):
            assert battle.get_health_value() == INITIAL_HEALTH and battle.get_accumulated_damage_value() == 3
        battle.heal_wound()
        with check("Здоровье не поднимается выше начального"):
            assert battle.get_health_value() == INITIAL_HEALTH
            assert battle.text(BattlePage.MESSAGE) == "Здоровье уже максимальное"

    @allure.title("Отрицательный урон уменьшает только накопленный урон (D-3)")
    def test_negative_damage_reduces_only_accumulated(self, page: Page):
        # подготовка
        battle = start_manual_battle(page)
        with check("Под полем ввода есть подсказка про отрицательное значение"):
            assert battle.is_visible(BattlePage.NEGATIVE_HINT)

        # вызов и проверка
        battle.apply_damage_manual("5")
        battle.apply_damage_manual("-3")
        with check("5 − 3 = 2"):
            assert battle.get_accumulated_damage_value() == 2
        battle.apply_damage_manual("-7")
        with check("Не ниже 0, здоровье не изменилось"):
            assert battle.get_accumulated_damage_value() == 0 and battle.get_health_value() == INITIAL_HEALTH

    @allure.title("Выход в меню не сбрасывает бой, «Сдаться» с подтверждением — поражение")
    def test_exit_resume_and_surrender(self, page: Page):
        # подготовка
        battle = start_battle(page, difficulty="3")
        battle.apply_damage_manual("5")

        # вызов
        main_page = battle.exit_to_menu()

        # проверка
        with check("В меню есть «Вернуться к бою»"):
            assert main_page.is_resume_battle_visible()
        battle = main_page.resume_battle()
        with check("Бой сохранился"):
            assert battle.get_accumulated_damage_value() == 5
        result = battle.surrender().confirm()
        with check("Поражение с причиной «Вы сдались.» (D-14)"):
            assert result.result() == "DEFEAT" and result.reason() == "Вы сдались."


@allure.feature("Экспедиция — смена стойки")
class TestStanceChange:

    @allure.title("Вираксен: окно «Смена стойки!» с полями стойки II; «Отмена» откатывает урон")
    def test_stance_change_auto(self, page: Page):
        # подготовка
        battle = start_battle(page)
        stance_dialog = StanceChangeDialog(page)
        toughness_1 = battle.get_toughness_value()

        # вызов
        reach_stance_threshold(battle)

        # проверка
        assert stance_dialog.is_displayed(), "При пороге здоровья появляется «Смена стойки!»"
        with check("Поля заполнены из базы боссов"):
            assert stance_dialog.is_from_boss_data()
        prefilled = stance_dialog.get_damage_to_wound()
        stance_dialog.click_cancel()
        with check("«Отмена» откатывает урон: здоровье 10, фаза I"):
            assert battle.get_health_value() == INITIAL_HEALTH and battle.get_phase_number() == 1

        reach_stance_threshold(battle)
        assert stance_dialog.is_displayed(), "Окно появляется повторно"
        stance_dialog.click_ok()
        with check("OK — фаза II, прочность 4 × стойка II"):
            assert battle.get_phase_number() == 2
            assert battle.get_toughness_value() == 4 * int(prefilled)
            assert battle.get_toughness_value() != toughness_1

    @allure.title("Ручные данные: остаток урона переносится и наносится с новой прочностью (R-2)")
    def test_stance_change_dialog_manual_data(self, page: Page):
        # подготовка: прочность 2 × 4 = 8, порог 7
        battle = start_battle(page, boss=None, damage_to_wound="2", stance_change="7")
        stance_dialog = StanceChangeDialog(page)
        reach_stance_threshold(battle)
        assert stance_dialog.is_displayed(), "При пороге здоровья — окно смены стойки"
        with check("Данных о стойке нет — поля пустые"):
            assert stance_dialog.is_without_boss_data() and stance_dialog.get_damage_to_wound() == ""
        stance_dialog.click_cancel()

        # вызов: 3 раны до порога 7 + 5 урона переноса
        battle.apply_damage_manual("29")

        # проверка
        assert stance_dialog.is_displayed(), "Окно появляется повторно"
        with check("Окно сообщает о перенесённом уроне 5"):
            assert "Перенесённый урон 5 " in (stance_dialog.get_carried_damage_text() or "")
        stance_dialog.set_damage_to_wound("1")
        stance_dialog.set_stance_change_health("3")
        stance_dialog.click_ok()
        with check("Фаза II, прочность 1 × 4 = 4"):
            assert battle.get_phase_number() == 2 and battle.get_toughness_value() == 4
        with check("Перенесённые 5 урона → 1 рана, остаток 1"):
            assert battle.get_health_value() == 6 and battle.get_accumulated_damage_value() == 1

    @allure.title("Иекорос: смена стойки только по кнопке, поля — из базы боссов")
    def test_stance_change_manual_iekoros(self, page: Page):
        # подготовка
        battle = start_battle(page, boss="Молния - Иекорос")
        with check("Смена стойки «по запросу»"):
            assert battle.get_stance_change() == "Смена стойки: по запросу"

        # вызов
        dialog = battle.click_change_stance()

        # проверка
        assert dialog.is_displayed(), "«Сменить стойку» открывает окно"
        with check("Поля заполнены из базы боссов"):
            assert dialog.is_from_boss_data()
        dialog.click_ok()
        with check("Фаза II"):
            assert battle.get_phase_number() == 2

    @allure.title("Коровон: на стойке II урон копится и наносится при переходе на стойку III")
    def test_stance_change_korovon_second_stance(self, page: Page):
        # подготовка
        battle = start_battle(page, boss="Коралл - Коровон")
        dialog = StanceChangeDialog(page)
        with check("Стойка I: кнопки «Сменить стойку» нет"):
            assert not battle.is_visible(BattlePage.CHANGE_STANCE)

        # вызов и проверка
        reach_stance_threshold(battle)
        assert dialog.is_displayed(), "Окно смены на стойку II"
        with check("Стойка II из базы боссов: порога раны нет — поле пустое"):
            assert dialog.is_from_boss_data() and dialog.get_damage_to_wound() == ""
        dialog.click_ok()
        with check("Фаза II, прочность «нет», кнопка «Сменить стойку» видна"):
            assert battle.get_phase_number() == 2
            assert battle.text(BattlePage.TOUGHNESS) == "Прочность: нет"
            assert battle.is_visible(BattlePage.CHANGE_STANCE)

        health_before = battle.get_health_value()
        battle.apply_damage_manual("50")
        with check("Стойка II: урон копится, раны не наносятся"):
            assert battle.get_accumulated_damage_value() == 50 and battle.get_health_value() == health_before

        dialog = battle.click_change_stance()
        assert dialog.is_displayed(), "Окно смены на стойку III"
        with check("Окно сообщает о перенесённом уроне 50"):
            assert "Перенесённый урон 50 " in (dialog.get_carried_damage_text() or "")
        dialog.click_ok()
        with check("Фаза III: накопленный урон пересчитан в раны"):
            assert battle.get_phase_number() == 3
            assert battle.get_accumulated_damage_value() < 50 and battle.get_health_value() < health_before

    @allure.title("Пробуждённый: 5 стоек подряд с полями из базы боссов, затем победа")
    def test_stance_change_awakened_five_stances(self, page: Page):
        # подготовка
        battle = start_battle(page, boss="Пробуждённый", difficulty="3", players="1")
        dialog = StanceChangeDialog(page)

        # вызов и проверка
        for expected_phase in range(2, 6):
            reach_stance_threshold(battle)
            assert dialog.is_displayed(), f"Окно смены на фазу {expected_phase}"
            with check(f"Фаза {expected_phase}: поля из базы боссов"):
                assert dialog.is_from_boss_data()
            dialog.click_ok()
            with check(f"Фаза {expected_phase}"):
                assert battle.get_phase_number() == expected_phase

        battle.apply_damage_manual(str(battle.get_health_value() * battle.get_toughness_value()))
        result = ResultPage(page)
        result.should_be_open()
        with check("На последней стойке здоровье 0 → «ПОБЕДА!»"):
            assert result.result() == "VICTORY"


@allure.feature("Экспедиция — бой в браузере")
class TestBrowserBattle:

    @allure.title("Экспедиция без входа до победы с отменой действия")
    def test_expedition_to_victory_with_undo(self, page: Page):
        # подготовка
        battle = start_manual_battle(page)
        battle.apply_damage_manual("8")
        with check("Рана нанесена"):
            assert battle.get_health_value() == 9

        # вызов: отмена урона, затем победа
        battle.undo()
        with check("Отмена вернула здоровье 10"):
            assert battle.get_health_value() == INITIAL_HEALTH
        battle.apply_damage_manual("80")
        result = ResultPage(page)
        result.should_be_open()

        # проверка
        with check("Победа"):
            assert result.result() == "VICTORY"
        battle = result.undo()
        with check("Ошибочную победу можно отменить"):
            assert battle.get_health_value() == INITIAL_HEALTH
        battle.apply_damage_manual("80")
        ResultPage(page).new_battle()
        with check("«Новый бой» — подготовка экспедиции, незаконченного боя нет"):
            ExpeditionPage(page).should_be_open()
            assert not MainPage(page).open().is_resume_battle_visible()

    @allure.title("Перезагрузка страницы посреди боя возвращает бой с историей отмены")
    def test_reload_keeps_battle(self, page: Page):
        # подготовка
        battle = start_battle(page, difficulty="3")
        battle.apply_damage_manual("5")
        battle.end_round()

        # вызов
        page.reload()

        # проверка
        battle = BattlePage(page)
        battle.should_be_open()
        with check("Раунд 2, накопленный урон 5"):
            assert battle.get_round_number() == 2 and battle.get_accumulated_damage_value() == 5
        battle.undo()
        with check("История отмены сохранилась: раунд 1"):
            assert battle.get_round_number() == 1
