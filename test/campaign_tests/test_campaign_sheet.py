"""Тесты: лист кампании (CampaignSheetPage). Группа 3.

Действия выполняются вне `check`, проверки — внутри `with check(...)`, чтобы одна упавшая
проверка не скрывала остальные.
"""

import allure
import pytest
from pytest_check import check

from campaign_tests.campaign_steps import create_campaign_in_menu, open_sheet
from pages.campaign_sheet_page import CampaignSheetPage
from pages.create_campaign_page import Hunter
from pages.main_page import MainPage

CAMPAIGN_NAME = "SheetTest"


@pytest.fixture
def sheet(driver) -> CampaignSheetPage:
    """Свежая кампания (пролог не сыгран) с Дареоном «Боец» и Мирой, открытая в листе."""
    create_campaign_in_menu(driver, CAMPAIGN_NAME, hunters=[Hunter.DAREON, Hunter.MIRA],
                            custom_names={Hunter.DAREON: "Боец"})
    return open_sheet(driver, CAMPAIGN_NAME)


def _reopen_sheet(sheet: CampaignSheetPage) -> CampaignSheetPage:
    sheet.go_to_main_menu()
    return open_sheet(sheet.driver, CAMPAIGN_NAME)


class TestCampaignSheet:

    @allure.feature("Campaign")
    @allure.story("3.1–3.4, 3.6 Шапка листа, охотники и древо навыков новой кампании")
    def test_initial_header(self, sheet):
        # TODO(3.5, implementationTasks.md задача 42.2): не хватает проверок открытия навыка «А1» и того,
        #  что ступень 2 не открывается без ступени 1. Состояние навыка передаётся только цветом кнопки
        #  (SkillCheckbox) и недоступно через UiAutomator2 — нужна семантика состояния в приложении.
        with check("3.1 Название кампании"):
            assert sheet.get_campaign_name() == CAMPAIGN_NAME
        with check("3.2 Начальная глава"):
            assert sheet.get_chapter() == 1
        with check("3.3 Кузня 1 уровня"):
            assert sheet.get_forge_level() == 1
        with check("3.3 Лаборатория 1 уровня"):
            assert sheet.get_lab_level() == 1
        with check("3.4 Охотники: имя игрока и класс"):
            assert sheet.get_hunters() == [("Боец", "Дареон"), ("Мира", "Мира")]
        with check("3.6 Древо навыков: 5 ветвей по 2 ступени"):
            assert sheet.get_skill_tree() == {letter: [f"{letter}1", f"{letter}2"] for letter in "АБВГД"}

    @allure.feature("Campaign")
    @allure.story("3.7, 3.11–3.13, 3.21 Пустые ресурсы, задания и достижения новой кампании")
    def test_initial_sections(self, sheet):
        with check("3.7 Все материи равны 0"):
            assert sheet.get_all_materials() == dict.fromkeys(CampaignSheetPage.MATERIAL_NAMES, 0)
        with check("3.11 Все растения равны 0"):
            assert sheet.get_all_plants() == dict.fromkeys(CampaignSheetPage.PLANT_NAMES, 0)
        with check("3.12 Все стихии равны 0"):
            assert sheet.get_all_elements() == dict.fromkeys(CampaignSheetPage.ELEMENT_NAMES, 0)
        with check("3.13 «Нет открытых заданий.»"):
            assert sheet.has_no_opened_quests()
        with check("3.13 Список открытых заданий пуст"):
            assert sheet.get_opened_quests() == []
        with check("3.21 «Нет достижений.»"):
            assert sheet.has_no_achievements()
        with check("3.21 Список достижений пуст"):
            assert sheet.get_achievements() == []

    @allure.feature("Campaign")
    @allure.story("3.8–3.9 Кнопки +/− ресурса, минимум 0")
    def test_resource_buttons(self, sheet):
        sheet.resource_decrement("Кости")
        with check("3.9 «−» не опускает значение ниже 0"):
            assert sheet.get_resource_value("Кости") == 0

        sheet.resource_increment("Кости").resource_increment("Кости")
        with check("3.8 «+» увеличивает значение на 1"):
            assert sheet.get_resource_value("Кости") == 2

        sheet.resource_decrement("Кости")
        with check("3.9 «−» уменьшает значение на 1"):
            assert sheet.get_resource_value("Кости") == 1

    @allure.feature("Campaign")
    @allure.story("3.14–3.18 Редактор заданий: открытие, отметка, снятие, несколько заданий, отмена")
    def test_quest_editor(self, sheet):
        dialog = sheet.edit_quests()
        with check("3.14 Диалог «Редактирование заданий» открыт"):
            assert dialog.is_displayed()
        with check("3.14 В новой кампании ничего не отмечено"):
            assert dialog.get_completed_quests() == []

        for number in (1, 2, 36):
            dialog.set_quest_completed(number, True)
        with check("3.17 Отмеченные задания в диалоге"):
            assert dialog.get_completed_quests() == [1, 2, 36]
        dialog.save()
        with check("3.15 Отмеченные задания появились в листе"):
            assert sheet.get_opened_quests() == [1, 2, 36]

        sheet.edit_quests().set_quest_completed(1, False).save()
        with check("3.16 Снятое задание исчезло из листа"):
            assert sheet.get_opened_quests() == [2, 36]

        sheet.edit_quests().set_quest_completed(5, True).cancel()
        with check("3.18 После «Отмена» список не изменился"):
            assert sheet.get_opened_quests() == [2, 36]
        dialog = sheet.edit_quests()
        with check("3.18 Отменённая отметка не сохранилась в диалоге"):
            assert not dialog.is_quest_completed(5)
        dialog.cancel()

    @allure.feature("Campaign")
    @allure.story("3.17a Кнопка «Выполнено» открывает зависимые задания (behavior.md §7, qa 70)")
    def test_complete_quest_button(self, sheet):
        # TODO(implementationTasks.md задача 42.3): не хватает проверки, что выполненное задание 1 пропадает
        #  из списка открытых (qa 57). Сейчас «Выполнено» ставит только is_completed, лист фильтрует
        #  по isAvailable — задание остаётся в списке.
        sheet.edit_quests().set_quest_completed(1, True).save()
        sheet.complete_quest(1)
        with check("Задание 1 в главах 1–2 открывает задание 4"):
            assert 4 in sheet.get_opened_quests()
        with check("При ручном завершении ресурсы не начисляются"):
            assert sheet.get_all_materials() == dict.fromkeys(CampaignSheetPage.MATERIAL_NAMES, 0)

    @allure.feature("Campaign")
    @allure.story("3.19–3.20 Сохранение заданий после выхода и после перезапуска приложения")
    def test_quests_persistence(self, sheet, config):
        dialog = sheet.edit_quests()
        for number in (1, 2, 36):
            dialog.set_quest_completed(number, True)
        dialog.save()

        sheet = _reopen_sheet(sheet)
        with check("3.19 Задания сохранились после выхода в меню"):
            assert sheet.get_opened_quests() == [1, 2, 36]

        package = config["capabilities"]["appPackage"]
        sheet.driver.terminate_app(package)
        sheet.driver.activate_app(package)
        sheet = open_sheet(sheet.driver, CAMPAIGN_NAME)
        with check("3.20 Задания сохранились после перезапуска приложения"):
            assert sheet.get_opened_quests() == [1, 2, 36]

    @allure.feature("Campaign")
    @allure.story("3.22–3.23 Добавление, удаление и сохранение достижений")
    def test_achievements(self, sheet):
        dialog = sheet.edit_achievements()
        with check("3.22 Диалог «Редактирование достижений» открыт"):
            assert dialog.is_displayed()
        dialog.add_achievement("Затишье").add_achievement("Гербарий")
        dialog.done()
        with check("3.22 Добавленные достижения в листе"):
            assert sheet.get_achievements() == ["Затишье", "Гербарий"]

        sheet.edit_achievements().remove_achievement("Затишье").done()
        with check("3.22 Удалённое достижение исчезло"):
            assert sheet.get_achievements() == ["Гербарий"]

        sheet = _reopen_sheet(sheet)
        with check("3.23 Достижения сохранились после выхода в меню"):
            assert sheet.get_achievements() == ["Гербарий"]

    @allure.feature("Campaign")
    @allure.story("3.25–3.26 Заметки и их сохранение")
    def test_notes(self, sheet):
        sheet.set_notes("Мои заметки").save_notes()
        with check("3.25 Заметки сохранены"):
            assert sheet.get_notes() == "Мои заметки"

        sheet = _reopen_sheet(sheet)
        with check("3.26 Заметки сохранились после выхода в меню"):
            assert sheet.get_notes() == "Мои заметки"

    @allure.feature("Campaign")
    @allure.story("3.27–3.28 Прокрутка листа и переход в главное меню")
    def test_scroll_and_main_menu(self, sheet):
        for _ in range(10):
            sheet.scroll_down()
        with check("3.27 Внизу листа видна кнопка «В главное меню»"):
            assert sheet.is_element_visible(CampaignSheetPage.GO_TO_MAIN_MENU)
        for _ in range(10):
            sheet.scroll_up()
        with check("3.27 Вверху листа видна строка «Глава:»"):
            assert sheet.is_element_visible(CampaignSheetPage.CHAPTER_HEADER)

        main = sheet.go_to_main_menu()
        with check("3.28 Переход в главное меню"):
            assert main.is_element_visible(MainPage.CAMPAIGN)
