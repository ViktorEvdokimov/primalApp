"""Тесты: создание кампании и лист кампаний (Группы 1, 2).

Действия выполняются вне `check`: если шаг не удался, дальнейшие проверки теряют смысл и тест
должен упасть. Проверки обёрнуты в `with check(...)`, чтобы одна упавшая не скрывала остальные.
"""

import allure
from pytest_check import check

from campaign_tests.campaign_steps import create_campaign_in_menu, start_new_campaign
from pages.campaign_list_page import CampaignListPage
from pages.create_campaign_page import CreateCampaignPage, Hunter
from pages.main_page import MainPage


def _open_create_page(driver) -> CreateCampaignPage:
    page = MainPage(driver).select_campaign()
    if isinstance(page, CampaignListPage):
        page = page.new_campaign()
    assert isinstance(page, CreateCampaignPage)
    return page


def _create_saved_campaign(driver, name: str) -> None:
    create_campaign_in_menu(driver, name, hunters=[Hunter.DAREON])


def _open_campaign_list(driver) -> CampaignListPage:
    page = MainPage(driver).select_campaign()
    assert isinstance(page, CampaignListPage), "Ожидался список кампаний"
    return page


class TestCampaignCreation:

    @allure.feature("Campaign")
    @allure.story("1.1, 1.3, 1.10 Экран создания: открытие без кампаний, ошибка без названия, «Назад»")
    def test_create_page_navigation_and_validation(self, driver):
        page = MainPage(driver).select_campaign()
        # Без экрана создания остальные проверки невозможны — это жёсткий assert
        assert isinstance(page, CreateCampaignPage), "1.1 Без кампаний открывается экран создания, а не список"
        with check("1.1 Заголовок «Новая кампания»"):
            assert page.is_title_displayed()

        page.select_hunter(Hunter.DAREON)
        page.click(page.START_BUTTON)
        with check("1.3 Ошибка «Введите название кампании»"):
            assert page.is_error_displayed(CreateCampaignPage.ERROR_EMPTY_NAME)
        with check("1.3 Кампания не создана — экран создания остаётся открытым"):
            assert page.is_title_displayed()

        main_page = page.go_back()
        with check("1.10 «Назад» возвращает в главное меню"):
            assert main_page.is_element_visible(MainPage.CAMPAIGN)

    @allure.feature("Campaign")
    @allure.story("1.2 Лимит кампаний")
    def test_campaigns_limit(self, driver):
        limit = 10
        for i in range(1, limit + 1):
            _create_saved_campaign(driver, f"Limit{i}")
        list_page = _open_campaign_list(driver)
        with check("Счётчик кампаний"):
            assert list_page.get_campaigns_count() == limit
        with check("Лимит кампаний"):
            assert list_page.get_campaigns_limit() == limit
        with check("Кнопка «Новая кампания» неактивна при достижении лимита"):
            assert not list_page.is_new_campaign_enabled()

    @allure.feature("Campaign")
    @allure.story("1.4, 1.8 Ввод названия кампании и имени игрока")
    def test_input_fields(self, driver):
        page = _open_create_page(driver)
        page.set_campaign_name("TestCampaign")
        page.select_hunter(Hunter.DAREON, custom_name="Боец")
        with check("1.4 Название кампании"):
            assert page.get_campaign_name() == "TestCampaign"
        with check("1.8 Имя игрока охотника"):
            assert page.get_hunter_custom_name(Hunter.DAREON) == "Боец"

    @allure.feature("Campaign")
    @allure.story("1.5, 1.7 Выбор и снятие охотников")
    def test_select_and_deselect_hunters(self, driver):
        page = _open_create_page(driver)
        page.select_hunter(Hunter.DAREON).select_hunter(Hunter.MIRA)
        with check("1.5 Дареон отмечен"):
            assert page.is_hunter_selected(Hunter.DAREON)
        with check("1.5 Мира отмечена"):
            assert page.is_hunter_selected(Hunter.MIRA)
        with check("1.5 Невыбранный Торег не отмечен"):
            assert not page.is_hunter_selected(Hunter.TOREG)

        page.select_hunter(Hunter.DAREON)
        with check("1.7 Повторный клик снимает отметку с Дареона"):
            assert not page.is_hunter_selected(Hunter.DAREON)
        with check("1.7 Мира остаётся отмеченной"):
            assert page.is_hunter_selected(Hunter.MIRA)

    @allure.feature("Campaign")
    @allure.story("1.6 Выбор всех 8 охотников")
    def test_select_all_hunters(self, driver):
        page = _open_create_page(driver)
        for hunter in Hunter:
            page.select_hunter(hunter)
        for hunter in Hunter:
            with check(f"{hunter.value} отмечен"):
                assert page.is_hunter_selected(hunter)

    @allure.feature("Campaign")
    @allure.story("1.9 Создание кампании через create_campaign с удалением дубликата")
    def test_create_campaign_duplicate(self, driver):
        _create_saved_campaign(driver, "Other")
        _create_saved_campaign(driver, "Dup")
        list_page = _open_campaign_list(driver)
        assert list_page.get_campaign_names().count("Dup") == 1, "Предусловие: одна кампания «Dup»"
        list_page.go_back_to_menu()

        start_new_campaign(driver, "Dup", hunters=[Hunter.DAREON]).exit_to_menu()

        names = _open_campaign_list(driver).get_campaign_names()
        with check(f"Дубликат удалён: {names}"):
            assert names.count("Dup") == 1
        with check(f"Посторонняя кампания не удалена: {names}"):
            assert "Other" in names


class TestCampaignList:

    @allure.feature("Campaign")
    @allure.story("2.1–2.3 Список кампаний, счётчик и удаление")
    def test_campaign_list_counter_and_delete(self, driver):
        for name in ("ListA", "ListB", "ListC"):
            _create_saved_campaign(driver, name)
        list_page = _open_campaign_list(driver)
        with check("2.1 Все созданные кампании в списке"):
            assert sorted(list_page.get_campaign_names()) == ["ListA", "ListB", "ListC"]
        with check("2.2 Счётчик «Кампаний: 3 / 10»"):
            assert list_page.get_campaigns_count() == 3

        list_page.delete_campaign("ListB")
        with check("2.3 Удалённая кампания исчезла из списка"):
            assert sorted(list_page.get_campaign_names()) == ["ListA", "ListC"]
        with check("2.3 Счётчик уменьшился"):
            assert list_page.get_campaigns_count() == 2

    @allure.feature("Campaign")
    @allure.story("2.4–2.6 Открытие кампании, «Назад в меню», «Новая кампания»")
    def test_campaign_list_navigation(self, driver):
        _create_saved_campaign(driver, "OpenMe")
        sheet = _open_campaign_list(driver).open_campaign("OpenMe")
        with check("2.4 Открывается лист выбранной кампании"):
            assert sheet.get_campaign_name() == "OpenMe"
        sheet.go_to_main_menu()

        main_page = _open_campaign_list(driver).go_back_to_menu()
        with check("2.6 «Назад в меню» возвращает в главное меню"):
            assert main_page.is_element_visible(MainPage.CAMPAIGN)

        page = _open_campaign_list(driver).new_campaign()
        with check("2.5 «Новая кампания» открывает экран создания"):
            assert page.is_title_displayed()
        with check("2.5 Поле названия пустое"):
            assert page.get_campaign_name() == ""
