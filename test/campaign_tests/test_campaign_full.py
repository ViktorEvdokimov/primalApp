"""Полное прохождение кампании — сквозной тест (Группа 7).

Механика приложения: каждое принятие наград главы увеличивает текущую главу на 1,
а уровни кузни и лаборатории растут только при принятии наград глав 4 и 8.

Действия выполняются вне `check`: если шаг прохождения не удался, продолжать нет смысла.
Проверки — внутри `with check(...)`, чтобы одна упавшая не останавливала весь сценарий.
"""

import allure
from pytest_check import check

from campaign_tests.campaign_steps import start_new_campaign, win_battle, win_battle_from_sheet
from pages.campaign_rewards_page import CampaignRewardsPage
from pages.campaign_sheet_page import CampaignSheetPage
from pages.create_campaign_page import Hunter
from pages.main_page import MainPage


def _open_not_completed(sheet: CampaignSheetPage, completed: set[int]) -> list[int]:
    # TODO(implementationTasks.md задача 42.3): выполненные задания остаются в списке открытых, поэтому
    #  здесь они исключаются вручную. После исправления сравнивать sheet.get_opened_quests() напрямую.
    return [q for q in sheet.get_opened_quests() if q not in completed]


@allure.feature("Campaign")
@allure.story("7.1 Полное прохождение кампании от Главы 1 до Главы 11")
def test_full_campaign_playthrough(driver):
    # TODO(implementationTasks.md задача 42.4): главы проходятся только боями с заданиями — не хватает
    #  проверки перехода главы после боя без задания: «Принять» после такого боя сейчас не работает.
    # TODO(implementationTasks.md задача 42.1): не хватает проверок условий главы 4 по достижениям
    #  («Народ Золотых гор» → задание 7, «Яд Пазиса» → «Получите награду 25») — условия не срабатывают.
    completed: set[int] = set()
    prep = start_new_campaign(driver, "FullRun", hunters=[Hunter.DAREON, Hunter.MIRA, Hunter.TOREG],
                              custom_names={Hunter.DAREON: "Боец"})

    with allure.step("Глава 1. Пролог: бой с Вираксеном"):
        with check("Босс пролога предзаполнен — Вираксен"):
            assert "Вираксен" in prep.get_selected_boss()
        chapter_rewards = win_battle(prep).click_continue()
        assert isinstance(chapter_rewards, CampaignRewardsPage), "После пролога открываются награды главы"

    with allure.step("Глава 1. Награды: КОСТИ 1, ЧЕШУЯ 1, КРОВЬ 2; растения; задания 1, 2, 36"):
        with check("Окно наград главы 1"):
            assert chapter_rewards.get_chapter() == 1
        with check("Материи главы 1"):
            assert chapter_rewards.get_materials() == {"Кости": 1, "Чешуя": 1, "Кровь": 2}
        with check("Растения главы 1"):
            assert chapter_rewards.get_plants() == {"Альбалацея": 1, "Антемон": 1, "Меллис": 1, "Ниллея": 2}
        with check("Задания главы 1"):
            assert chapter_rewards.get_opened_quests() == [1, 2, 36]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 2"):
            assert sheet.get_chapter() == 2
        with check("Охотники в листе"):
            assert sheet.get_hunters() == [("Боец", "Дареон"), ("Мира", "Мира"), ("Торег", "Торег")]

    with allure.step("Задание 1. Память пустыни: награды"):
        quest_rewards = win_battle_from_sheet(sheet, quest=1)
        with check("Материи задания 1"):
            assert quest_rewards.get_materials() == {"Кости": 2, "Златия": 2}
        completed.add(1)
        chapter_rewards = quest_rewards.accept()

    with allure.step("Глава 2. Открытые задания 3, 41, 46; задание 4 открыто заданием 1"):
        with check("Окно наград главы 2"):
            assert chapter_rewards.get_chapter() == 2
        with check("Задания главы 2"):
            assert sorted(chapter_rewards.get_opened_quests()) == [3, 41, 46]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 3"):
            assert sheet.get_chapter() == 3
        with check("Открытые задания после главы 2 (задание 4 открыто заданием 1)"):
            assert _open_not_completed(sheet, completed) == [2, 3, 4, 36, 41, 46]

    with allure.step("Задание 2. Полёт в вечную бурю (глава 3 — задание 5 не открывается)"):
        quest_rewards = win_battle_from_sheet(sheet, quest=2)
        with check("Материи задания 2"):
            assert quest_rewards.get_materials() == {"Кровь": 2, "Зимия": 1, "Иридия": 1}
        completed.add(2)
        chapter_rewards = quest_rewards.accept()

    with allure.step("Глава 3. Истекло время заданий 2 и 36"):
        with check("Окно наград главы 3"):
            assert chapter_rewards.get_chapter() == 3
        with check("Истекают задания 2 и 36"):
            assert chapter_rewards.get_expired_quests() == [2, 36]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 4"):
            assert sheet.get_chapter() == 4
        with check("Открытые задания после главы 3"):
            assert _open_not_completed(sheet, completed) == [3, 4, 41, 46]

    with allure.step("Задание 4. Вожак стаи: достижение «Тайны прошлого»"):
        # Задание 3 (Коровон) не подходит: стойка II без порога раны, одним уроном не победить
        quest_rewards = win_battle_from_sheet(sheet, quest=4)
        with check("Достижение задания 4"):
            assert quest_rewards.get_achievements() == ["Тайны прошлого"]
        completed.add(4)
        chapter_rewards = quest_rewards.accept()

    with allure.step("Глава 4. 2 уровень кузни и лаборатории; задания 11 и 8 (нет «Народ Золотых гор»)"):
        with check("Окно наград главы 4"):
            assert chapter_rewards.get_chapter() == 4
        with check("Истекающие задания главы 4"):
            assert chapter_rewards.get_expired_quests() == [1, 3, 4, 5, 31, 41, 46]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 5"):
            assert sheet.get_chapter() == 5
        with check("Кузня 2 уровня"):
            assert sheet.get_forge_level() == 2
        with check("Лаборатория 2 уровня"):
            assert sheet.get_lab_level() == 2
        with check("Открытые задания после главы 4"):
            assert _open_not_completed(sheet, completed) == [8, 11]
        with check("Достижения"):
            assert sheet.get_achievements() == ["Тайны прошлого"]

    with allure.step("Глава 8. 3 уровень кузни и лаборатории"):
        sheet.set_chapter(8)
        quest_rewards = win_battle_from_sheet(sheet, quest=11)
        completed.add(11)
        chapter_rewards = quest_rewards.accept()
        with check("Окно наград главы 8"):
            assert chapter_rewards.get_chapter() == 8
        sheet = chapter_rewards.accept()
        with check("Переход к главе 9"):
            assert sheet.get_chapter() == 9
        with check("Кузня 3 уровня"):
            assert sheet.get_forge_level() == 3
        with check("Лаборатория 3 уровня"):
            assert sheet.get_lab_level() == 3

    with allure.step("Глава 11. Финальный бой с Пробуждённым"):
        sheet.set_chapter(11)
        prep = sheet.start_battle().continue_without_quest()
        prep.select_boss("Пробуждённый")
        victory = win_battle(prep)
        main_page = victory.click_exit_to_menu()
        with check("После финальной победы — выход в главное меню"):
            assert main_page.is_element_visible(MainPage.CAMPAIGN)
