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
from pages.create_campaign_page import Hunter
from pages.main_page import MainPage


@allure.feature("Campaign")
@allure.story("7.1 Полное прохождение кампании от Главы 1 до финального боя")
def test_full_campaign_playthrough(driver):
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

    with allure.step("Задание 2. Полёт в вечную бурю (глава 2 — открывает задание 5)"):
        quest_rewards = win_battle_from_sheet(sheet, quest=2)
        with check("Материи задания 2"):
            assert quest_rewards.get_materials() == {"Кровь": 2, "Зимия": 1, "Иридия": 1}
        chapter_rewards = quest_rewards.accept()

    with allure.step("Глава 2. Открытые задания 3, 41, 46; задание 5 открыто заданием 2"):
        with check("Окно наград главы 2"):
            assert chapter_rewards.get_chapter() == 2
        with check("Задания главы 2"):
            assert sorted(chapter_rewards.get_opened_quests()) == [3, 41, 46]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 3"):
            assert sheet.get_chapter() == 3
        with check("Открытые задания после главы 2: выполненное задание 2 убрано из списка (задача 42.3)"):
            assert sheet.get_opened_quests() == [1, 3, 5, 36, 41, 46]
        with check("D-5 Выполненное задание 2 — в списке «Выполненные»"):
            assert sheet.get_completed_quests() == [2]
        editor = sheet.edit_quests()
        with check("D-5 В редакторе выполненное задание 2 недоступно для изменения"):
            assert not editor.is_quest_editable(2)
        with check("Открытое задание 1 в редакторе изменяемо"):
            assert editor.is_quest_editable(1)
        sheet = editor.cancel()

    with allure.step("Задание 36. Чудовище «Муары»: достижения «Лагерь в джунглях», «Яд Пазиса»"):
        quest_rewards = win_battle_from_sheet(sheet, quest=36)
        with check("Достижения задания 36"):
            assert sorted(quest_rewards.get_achievements()) == ["Лагерь в джунглях", "Яд Пазиса"]
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
            assert sheet.get_opened_quests() == [1, 3, 5, 41, 46]

    with allure.step("Задание 5. Серебряные когти: достижения «Пыль аркеума», «Народ Золотых гор»"):
        quest_rewards = win_battle_from_sheet(sheet, quest=5)
        with check("Достижения задания 5"):
            assert sorted(quest_rewards.get_achievements()) == ["Народ Золотых гор", "Пыль аркеума"]
        chapter_rewards = quest_rewards.accept()

    with allure.step("Глава 4. 2 уровень кузни и лаборатории; условия по достижениям (задача 42.1)"):
        with check("Окно наград главы 4"):
            assert chapter_rewards.get_chapter() == 4
        with check("Истекающие задания главы 4"):
            assert chapter_rewards.get_expired_quests() == [1, 3, 4, 5, 31, 41, 46]
        with check("«Яд Пазиса» → сообщение «Получите награду 25»"):
            assert chapter_rewards.has_message("Получите награду 25")
        sheet = chapter_rewards.accept()
        with check("Переход к главе 5"):
            assert sheet.get_chapter() == 5
        with check("Кузня 2 уровня"):
            assert sheet.get_forge_level() == 2
        with check("Лаборатория 2 уровня"):
            assert sheet.get_lab_level() == 2
        with check("«Народ Золотых гор» → открыто задание 7 (а не 8) и задание 11"):
            assert sheet.get_opened_quests() == [7, 11]
        with check("Достижения"):
            assert sorted(sheet.get_achievements()) == [
                "Лагерь в джунглях", "Народ Золотых гор", "Пыль аркеума", "Яд Пазиса"
            ]

    with allure.step("Глава 8. 3 уровень кузни и лаборатории"):
        sheet.set_chapter(8)
        quest_rewards = win_battle_from_sheet(sheet, quest=11)
        chapter_rewards = quest_rewards.accept()
        with check("Окно наград главы 8"):
            assert chapter_rewards.get_chapter() == 8
        with check("Условная награда главы 8: без «Голос Волтьяра» — «Достижения нет.»"):
            assert chapter_rewards.get_conditions("Условные награды:") == [
                ("Если есть достижение «Голос Волтьяра» — улучшение набора охотника", "Достижения нет.")
            ]
        sheet = chapter_rewards.accept()
        with check("Переход к главе 9"):
            assert sheet.get_chapter() == 9
        with check("Кузня 3 уровня"):
            assert sheet.get_forge_level() == 3
        with check("Лаборатория 3 уровня"):
            assert sheet.get_lab_level() == 3

    with allure.step("Глава 9. Бой без задания: «Принять» переводит к следующей главе (задача 42.4)"):
        prep = sheet.start_battle().continue_without_quest()
        prep.select_boss("Рог - Торамат")
        chapter_rewards = win_battle(prep).click_continue().accept()
        with check("Окно наград главы 9"):
            assert chapter_rewards.get_chapter() == 9
        sheet = chapter_rewards.accept()
        with check("Переход к главе 10"):
            assert sheet.get_chapter() == 10
        with check("Торамат среди поверженных боссов"):
            assert "Торамат (Рог)" in sheet.get_defeated_bosses()

    with allure.step("Глава 11. Истекает время всех заданий (R-6)"):
        sheet.set_chapter(11)
        prep = sheet.start_battle().continue_without_quest()
        prep.select_boss("Рог - Торамат")
        chapter_rewards = win_battle(prep).click_continue().accept()
        with check("Окно наград главы 11"):
            assert chapter_rewards.get_chapter() == 11
        with check("Сообщение: следующий бой — финальный"):
            assert chapter_rewards.has_message("Следующий бой — финальный: Пробуждённый")
        sheet = chapter_rewards.accept()
        with check("Переход к главе 12"):
            assert sheet.get_chapter() == 12
        with check("Все задания истекли"):
            assert sheet.get_opened_quests() == []

    with allure.step("Финальный бой с Пробуждённым: без выбора задания (R-6)"):
        prep = sheet.start_final_battle()
        with check("Босс финального боя — Пробуждённый"):
            assert "Пробуждённый" in prep.get_selected_boss()
        chapter_rewards = win_battle(prep).click_continue().accept()
        with check("Кампания пройдена"):
            assert chapter_rewards.has_message("Кампания пройдена! Пробуждённый повержен.")
        sheet = chapter_rewards.accept()
        with check("Пробуждённый среди поверженных боссов"):
            assert any(boss.startswith("Пробуждённый") for boss in sheet.get_defeated_bosses())
        main_page = sheet.go_to_main_menu()
        with check("После финала — главное меню"):
            assert main_page.is_element_visible(MainPage.CAMPAIGN)
