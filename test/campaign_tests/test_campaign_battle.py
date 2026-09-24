"""Тесты: награды за главу, задания, бой в кампании (Группы 4, 5, 6).

Действия выполняются вне `check`, проверки — внутри `with check(...)`, чтобы одна упавшая
проверка не скрывала остальные.
"""

import allure
from pytest_check import check

from campaign_tests.campaign_steps import (sheet_after_prologue, start_new_campaign, win_battle,
                                           win_battle_from_sheet, win_prologue)
from pages.campaign_sheet_page import CampaignSheetPage
from pages.create_campaign_page import Hunter
from pages.main_page import MainPage

CHAPTER1_MATERIALS = {"Кости": 1, "Чешуя": 1, "Кровь": 2}
CHAPTER1_PLANTS = {"Альбалацея": 1, "Антемон": 1, "Меллис": 1, "Ниллея": 2}
CHAPTER1_QUESTS = [1, 2, 36]


class TestChapterRewards:

    @allure.feature("Campaign")
    @allure.story("4.1–4.2 Окно наград за закрытие Главы 1")
    def test_chapter1_rewards_window(self, driver):
        rewards = win_prologue(driver, "Chapter1Test")
        with check("4.2 Заголовок окна"):
            assert rewards.get_title() == "Награды главы 1"
        with check("4.1 Материи"):
            assert rewards.get_materials() == CHAPTER1_MATERIALS
        with check("4.1 Растения"):
            assert rewards.get_plants() == CHAPTER1_PLANTS
        with check("4.1 Открываемые задания"):
            assert rewards.get_opened_quests() == CHAPTER1_QUESTS

    @allure.feature("Campaign")
    @allure.story("4.3, 3.24 Принятие наград главы 1: ресурсы, задания, поверженный босс")
    def test_accept_chapter_rewards(self, driver):
        sheet = win_prologue(driver, "AcceptTest").accept()
        with check("4.3 Переход к главе 2"):
            assert sheet.get_chapter() == 2
        for name, qty in {**CHAPTER1_MATERIALS, **CHAPTER1_PLANTS}.items():
            with check(f"4.3 Начислено: {name}"):
                assert sheet.get_resource_value(name) == qty
        with check("4.3 Открыты задания главы 1"):
            assert sheet.get_opened_quests() == CHAPTER1_QUESTS
        with check("3.24 Поверженный босс — Вираксен"):
            assert sheet.get_defeated_bosses() == ["Вираксен (Огонь)"]
        with check("3.24 Стихии поверженных боссов"):
            assert sheet.get_defeated_bosses_elements() == ["Огонь"]
        with check("3.24 За победу над боссом — 2 стихии «Огонь»"):
            assert sheet.get_resource_value("Огонь") == 2

    @allure.feature("Campaign")
    @allure.story("4.4 Отклонение наград главы 1")
    def test_decline_chapter_rewards(self, driver):
        sheet = win_prologue(driver, "DeclineTest").decline()
        with check("Глава не меняется"):
            assert sheet.get_chapter() == 1
        with check("Материи главы не начислены"):
            assert sheet.get_all_materials() == dict.fromkeys(CampaignSheetPage.MATERIAL_NAMES, 0)
        with check("Задания главы не открыты"):
            assert sheet.has_no_opened_quests()

    @allure.feature("Campaign")
    @allure.story("4.6–4.7 Глава 4: задания, истечение и 2 уровень кузницы и лаборатории")
    def test_chapter4_rewards(self, driver):
        # TODO(4.6, implementationTasks.md задача 42.1): не хватает двух проверок условий главы 4 по достижениям:
        #  - после задания 36 окно главы 4 показывает «Получите награду 25» (достижение «Яд Пазиса»);
        #  - после задания 5 глава 4 открывает задание 7 вместо 8 (достижение «Народ Золотых гор»).
        #  Сейчас задания выдают «Яд пазиса» / «Народ золотых гор», а глава проверяет написание с заглавной
        #  буквы (регистрозависимое сравнение) — условия никогда не срабатывают.
        sheet = sheet_after_prologue(driver, "Chapter4Test")
        sheet.set_chapter(4)
        chapter_rewards = win_battle_from_sheet(sheet, quest=1).accept()
        with check("Окно наград главы 4"):
            assert chapter_rewards.get_chapter() == 4
        with check("Открывается задание 11"):
            assert 11 in chapter_rewards.get_opened_quests()
        with check("Истекают задания 1, 3, 4, 5, 31, 41, 46"):
            assert chapter_rewards.get_expired_quests() == [1, 3, 4, 5, 31, 41, 46]

        sheet = chapter_rewards.accept()
        with check("Переход к главе 5"):
            assert sheet.get_chapter() == 5
        with check("4.7 Кузня 2 уровня"):
            assert sheet.get_forge_level() == 2
        with check("4.7 Лаборатория 2 уровня"):
            assert sheet.get_lab_level() == 2
        opened = sheet.get_opened_quests()
        with check(f"Без «Народ Золотых гор» открыто задание 8 и задание 11: {opened}"):
            assert {8, 11} <= set(opened)
        with check(f"Задание 7 не открыто, истёкшее задание 1 убрано: {opened}"):
            assert 7 not in opened and 1 not in opened


class TestQuestRewards:

    @allure.feature("Campaign")
    @allure.story("5.1, 6.7 Награды за победу в Задании 1 «Память пустыни»")
    def test_quest1_victory_rewards(self, driver):
        # TODO(implementationTasks.md задача 42.3): не хватает проверки, что выполненное победой задание 1
        #  пропадает из списка открытых в листе (qa 57) — сейчас оно остаётся.
        rewards = win_battle_from_sheet(sheet_after_prologue(driver, "Quest1Test"), quest=1)
        with check("6.7 «Продолжить» открывает награды выбранного задания"):
            assert rewards.get_quest_title() == "Задание 1: Память пустыни"
        with check("5.1 Материи"):
            assert rewards.get_materials() == {"Кости": 2, "Златия": 2}
        with check("5.1 Растения"):
            assert rewards.get_plants() == {"Ниллея": 2, "Тармарет": 1, "Альбалацея": 1, "Селикорния": 1}

        sheet = rewards.accept().accept()
        with check("5.9 В главе 1–2 открывается задание 4 (условное, в окне не показывается)"):
            assert 4 in sheet.get_opened_quests()
        with check("Кости: 1 за главу 1 + 2 за задание 1"):
            assert sheet.get_resource_value("Кости") == 1 + 2

    @allure.feature("Campaign")
    @allure.story("5.7 Награды за победу в Задании 5 «Серебряные когти»")
    def test_quest5_victory_rewards(self, driver):
        sheet = sheet_after_prologue(driver, "Quest5Test")
        sheet = win_battle_from_sheet(sheet, quest=2).accept().accept()  # задание 2 в главе 2 открывает задание 5
        rewards = win_battle_from_sheet(sheet, quest=5)
        with check("Материи"):
            assert rewards.get_materials() == {"Чешуя": 2, "Иридия": 1, "Златия": 1}
        with check("Растения"):
            assert rewards.get_plants() == {"Тармарет": 2, "Альбалацея": 1, "Антемон": 1, "Селикорния": 1}
        with check("Достижения"):
            assert sorted(rewards.get_achievements()) == ["Народ золотых гор", "Пыль аркеума"]

    @allure.feature("Campaign")
    @allure.story("5.12 Достижения за Задание 36 «Чудовище «Муары»»")
    def test_quest36_achievements(self, driver):
        # Сравнение без учёта регистра: написание «Яд пазиса» / «Яд Пазиса» расходится (задача 42.1)
        expected = ["лагерь в джунглях", "яд пазиса"]
        rewards = win_battle_from_sheet(sheet_after_prologue(driver, "Quest36Test"), quest=36)
        with check("Достижения в окне наград"):
            assert sorted(a.casefold() for a in rewards.get_achievements()) == expected
        sheet = rewards.accept().accept()
        with check("Достижения в листе после принятия"):
            assert sorted(a.casefold() for a in sheet.get_achievements()) == expected


class TestCampaignBattle:

    @allure.feature("Campaign")
    @allure.story("6.1–6.3, 6.5 Диалог выбора задания: список, отмена, выбор задания")
    def test_quest_selection(self, driver):
        sheet = sheet_after_prologue(driver, "BattleFlow")
        selection = sheet.start_battle()
        with check("6.1 Диалог «Выбор задания» открыт"):
            assert selection.is_displayed()
        with check("6.2 В списке только открытые задания"):
            assert selection.get_available_quest_numbers() == CHAPTER1_QUESTS

        sheet = selection.cancel()
        with check("6.5 «Отмена» возвращает на лист кампании"):
            assert sheet.is_displayed()

        prep = sheet.start_battle().select_quest(1)
        with check("6.3 Выбор задания открывает подготовку к бою"):
            assert prep.is_displayed()
        with check("6.3 Босс задания предзаполнен"):
            assert "Торамат" in prep.get_selected_boss()

    @allure.feature("Campaign")
    @allure.story("6.4 Продолжить без задания")
    def test_continue_without_quest(self, driver):
        # TODO(6.7, implementationTasks.md задача 42.4): не хватает проверки, что после победы без задания
        #  «Принять» в окне наград открывает награды главы (qa 61). Сейчас onQuestRewardsAccept прерывается
        #  ошибкой «У задания не указан босс».
        prep = sheet_after_prologue(driver, "NoQuest").start_battle().continue_without_quest()
        with check("Открывается подготовка к бою"):
            assert prep.is_displayed()

    @allure.feature("Campaign")
    @allure.story("6.6, 6.8 Экран победы и выход в меню")
    def test_victory_and_exit_to_menu(self, driver):
        victory = win_battle(start_new_campaign(driver, "VictoryTest", hunters=[Hunter.KARA, Hunter.HELEREN]))
        with check("6.6 «Монстр повержен!»"):
            assert victory.is_monster_defeated_displayed()
        main_page = victory.click_exit_to_menu()
        with check("6.8 «Выход в меню» возвращает в главное меню"):
            assert main_page.is_element_visible(MainPage.CAMPAIGN)

    @allure.feature("Campaign")
    @allure.story("6.9 Поражение в бою кампании (Задание 1)")
    def test_campaign_battle_defeat(self, driver):
        prep = sheet_after_prologue(driver, "DefeatTest").start_battle().select_quest(1)
        defeat = prep.start_battle().surrender().confirm()
        with check("Экран поражения"):
            assert defeat.is_displayed()
        rewards = defeat.click_continue()
        with check("Окно наград за поражение"):
            assert rewards.is_displayed()
        with check("5.2 Поражение в задании 1 открывает задание 6"):
            assert rewards.get_opened_quests() == [6]
