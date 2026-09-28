"""Лист кампании (задача 4.6): сценарии test/campaign_tests/test_campaign_sheet.py мобильного приложения на вебе.

Главы на сайте — главы книги: пролог — 0, а не 1, как в приложении (defects.md R-1). Действия выполняются
вне `check`, проверки — внутри, чтобы одна упавшая проверка не скрывала остальные.
"""

import re

import allure
import pytest
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaign_sheet_page import CampaignSheetPage
from pages.campaigns_page import CampaignsPage
from tests.test_login import login, unique_email

CAMPAIGN_NAME = "SheetTest"


@pytest.fixture
def sheet(page: Page, mailpit) -> CampaignSheetPage:
    """Новая кампания (пролог) с Дареоном «Боец» и Мирой, открытая в листе."""
    login(page, mailpit, unique_email())
    CampaignsPage(page).open().create().create(CAMPAIGN_NAME, ["DAREON", "MIRA"], player_names={"DAREON": "Боец"})
    # после создания открывается подготовка пролога (задача 5.4) — лист открываем по адресу
    expect(page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
    campaign_id = int(page.url.rstrip("/").split("/")[-3])
    sheet = CampaignSheetPage(page, campaign_id).open()
    sheet.should_be_open()
    return sheet


def reopen(sheet: CampaignSheetPage, tab: str | None = None) -> CampaignSheetPage:
    """Перезагрузка страницы: всё показанное должно прийти с сервера."""
    sheet.page.reload()
    sheet.should_be_open()
    return sheet.open_tab(tab) if tab else sheet


@allure.feature("Лист кампании")
class TestCampaignSheet:

    @allure.title("3.1–3.6 Шапка, охотники и древо навыков новой кампании")
    def test_initial_header(self, sheet: CampaignSheetPage):
        with check("3.1 Название кампании"):
            assert sheet.campaign_name() == CAMPAIGN_NAME
        with check("3.2 Начальная глава — пролог"):
            assert sheet.chapter() == 0 and sheet.chapter_text() == "Пролог"
        with check("3.3 Кузня и лаборатория 1 уровня"):
            assert (sheet.forge_level(), sheet.lab_level()) == (1, 1)
        with check("3.4 Охотники: имя игрока и класс"):
            assert sheet.hunters() == [("Боец", "Дареон"), ("Мира", "Мира")]
        with check("3.6 Древо навыков: 5 ветвей по 2 ступени"):
            assert sheet.skill_tree() == {letter: [f"{letter}1", f"{letter}2"] for letter in "АБВГД"}

        with check("3.5 В новой кампании навыки закрыты"):
            assert not sheet.is_skill_unlocked("А1") and not sheet.is_skill_unlocked("А2")
        with check("3.5 Ступень 2 недоступна без ступени 1"):
            assert not sheet.is_skill_available("А2")
        sheet.click_skill("А1", unlocked_after=True)
        with check("3.5 После «А1» доступна «А2»"):
            assert sheet.is_skill_available("А2")
        sheet.click_skill("А2", unlocked_after=True)
        with check("Открытая «А1» при открытой «А2» не снимается"):
            assert not sheet.is_skill_available("А1") and sheet.is_skill_unlocked("А1")

        sheet = reopen(sheet)
        with check("Навыки сохранились после перезагрузки"):
            assert sheet.is_skill_unlocked("А1") and sheet.is_skill_unlocked("А2")
        sheet.select_hunter("Мира")
        with check("У второго охотника свои навыки"):
            assert not sheet.is_skill_unlocked("А1")

    @allure.title("3.7, 3.11–3.13, 3.21 Пустые ресурсы, задания, достижения и трофеи новой кампании")
    def test_initial_sections(self, sheet: CampaignSheetPage):
        with check("3.7 Все материи равны 0"):
            assert sheet.resources(CampaignSheetPage.MATERIAL_NAMES) == dict.fromkeys(CampaignSheetPage.MATERIAL_NAMES, 0)
        with check("3.11 Все растения равны 0"):
            assert sheet.resources(CampaignSheetPage.PLANT_NAMES) == dict.fromkeys(CampaignSheetPage.PLANT_NAMES, 0)
        with check("3.12 Все стихии равны 0"):
            assert sheet.resources(CampaignSheetPage.ELEMENT_NAMES) == dict.fromkeys(CampaignSheetPage.ELEMENT_NAMES, 0)

        sheet.open_tab("quests")
        with check("3.13 «Нет открытых заданий.»"):
            assert sheet.has_no_open_quests() and sheet.open_quests() == []
        sheet.open_tab("achievements")
        with check("3.21 «Нет достижений.»"):
            assert sheet.has_no_achievements() and sheet.achievements() == []
        sheet.open_tab("trophies")
        with check("Трофеев нет"):
            assert sheet.has_no_trophies()

    @allure.title("3.8–3.9 Кнопки +/− ресурса, минимум 0, сохранение")
    def test_resource_buttons(self, sheet: CampaignSheetPage):
        with check("3.9 «−» недоступен при 0"):
            assert not sheet.is_decrement_enabled("Кости")

        sheet.increment("Кости").increment("Кости")
        with check("3.8 «+» увеличивает значение на 1"):
            assert sheet.resource_value("Кости") == 2

        sheet.decrement("Кости")
        with check("3.9 «−» уменьшает значение на 1"):
            assert sheet.resource_value("Кости") == 1

        sheet.wait_resources_saved()
        sheet = reopen(sheet)
        with check("Ресурсы сохранились после перезагрузки"):
            assert sheet.resource_value("Кости") == 1

    @allure.title("3.14–3.18 Редактор заданий: отметка, снятие, несколько заданий, отмена")
    def test_quest_editor(self, sheet: CampaignSheetPage):
        sheet.open_tab("quests")
        dialog = sheet.edit_quests()
        with check("3.14 В новой кампании ничего не отмечено"):
            assert dialog.checked() == []

        for number in (1, 2, 36):
            dialog.set_checked(number, True)
        with check("3.17 Отмеченные задания в диалоге"):
            assert dialog.checked() == [1, 2, 36]
        dialog.save()
        with check("3.15 Отмеченные задания появились в листе"):
            assert sheet.open_quests() == [1, 2, 36]

        sheet.edit_quests().set_checked(1, False).save()
        with check("3.16 Снятое задание исчезло из листа"):
            assert sheet.open_quests() == [2, 36]

        sheet.edit_quests().set_checked(5, True).cancel()
        with check("3.18 После «Отмена» список не изменился"):
            assert sheet.open_quests() == [2, 36]
        dialog = sheet.edit_quests()
        with check("3.18 Отменённая отметка не сохранилась в диалоге"):
            assert not dialog.is_checked(5)
        dialog.cancel()

        sheet = reopen(sheet, "quests")
        with check("3.19 Задания сохранились после перезагрузки"):
            assert sheet.open_quests() == [2, 36]

    @allure.title("3.17a «Выполнено» открывает зависимые задания без ресурсов, «Отмена» возвращает задание (qa 57, 70)")
    def test_complete_quest_button(self, sheet: CampaignSheetPage):
        sheet.change_chapter(1)
        sheet.open_tab("quests").edit_quests().set_checked(1, True).save()

        sheet.complete_quest(1)
        # открытое выполнением задание приходит со свежим листом — ждём его, а не читаем сразу
        expect(sheet.open_quest(4)).to_be_visible()
        opened = sheet.open_quests()
        with check(f"Задание 1 в главах 1–2 открывает задание 4: {opened}"):
            assert 4 in opened
        with check(f"Выполненное задание 1 пропадает из открытых: {opened}"):
            assert 1 not in opened
        with check("Задание 1 — в списке «Выполненные:»"):
            assert sheet.completed_quests() == [1]
        dialog = sheet.edit_quests()
        with check("Выполненное задание в редакторе неактивно"):
            assert not dialog.is_enabled(1)
        dialog.cancel()
        sheet.open_tab("hunters")
        with check("При ручном завершении ресурсы не начисляются"):
            assert sheet.resources(CampaignSheetPage.MATERIAL_NAMES) == dict.fromkeys(CampaignSheetPage.MATERIAL_NAMES, 0)

        sheet.open_tab("quests").reopen_quest(1)
        opened = sheet.open_quests()
        with check(f"«Отмена» возвращает задание 1 в открытые, задание 4 остаётся: {opened}"):
            assert 1 in opened and 4 in opened
        with check("Список «Выполненные:» пуст"):
            assert sheet.completed_quests() == []

    @allure.title("3.22–3.23 Добавление, удаление и сохранение достижений")
    def test_achievements(self, sheet: CampaignSheetPage):
        sheet.open_tab("achievements")
        sheet.add_achievement("Затишье").add_achievement("Гербарий")
        with check("3.22 Добавленные достижения в листе"):
            assert sheet.achievements() == ["Затишье", "Гербарий"]

        sheet.remove_achievement("Затишье")
        with check("3.22 Удалённое достижение исчезло"):
            assert sheet.achievements() == ["Гербарий"]

        sheet.add_achievement("голос  волтьяра", shown_as="Голос Волтьяра")
        with check("Написание приводится к каталогу"):
            assert sheet.achievements() == ["Гербарий", "Голос Волтьяра"]

        sheet = reopen(sheet, "achievements")
        with check("3.23 Достижения сохранились после перезагрузки"):
            assert sheet.achievements() == ["Гербарий", "Голос Волтьяра"]

    @allure.title("3.25–3.26 Заметки сохраняются сами")
    def test_notes(self, sheet: CampaignSheetPage):
        sheet.open_tab("notes").set_notes("Мои заметки")

        sheet = reopen(sheet, "notes")
        with check("3.26 Заметки сохранились после перезагрузки"):
            assert sheet.notes() == "Мои заметки"

    @allure.title("Ручная правка главы — после подтверждения, «Отмена» ничего не меняет")
    def test_change_chapter(self, sheet: CampaignSheetPage):
        sheet.change_chapter(3, confirm=False)
        with check("После «Отмена» глава та же"):
            assert sheet.chapter_text() == "Пролог"

        sheet.change_chapter(3)
        with check("Глава изменена"):
            expect(sheet.by_test_id("sheet-chapter")).to_have_text("Глава 3")

        sheet = reopen(sheet)
        with check("Глава сохранилась после перезагрузки"):
            assert sheet.chapter() == 3

    @allure.title("3.28 Возврат к списку кампаний и удаление кампании из листа")
    def test_back_and_delete(self, sheet: CampaignSheetPage):
        sheet.back_to_list()
        campaigns = CampaignsPage(sheet.page)
        campaigns.should_be_open()
        with check("3.28 Кампания в списке"):
            expect(campaigns.item(CAMPAIGN_NAME)).to_have_count(1)

        campaigns.item(CAMPAIGN_NAME).get_by_test_id("campaign-item-open").click()
        sheet.should_be_open()
        sheet.delete_campaign()
        campaigns.should_be_open()
        with check("Удалённой кампании нет в списке"):
            expect(campaigns.item(CAMPAIGN_NAME)).to_have_count(0)
