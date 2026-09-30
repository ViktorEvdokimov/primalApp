"""Кампании: создание, список, удаление (задача 4.1)."""

import re

import allure
from playwright.sync_api import Page, expect
from pytest_check import check

from pages.campaigns_page import CampaignsPage
from pages.main_page import MainPage
from tests.test_login import register


@allure.feature("Кампании")
class TestCampaigns:

    @allure.title("Созданная кампания видна в списке с главой «Пролог» и удаляется после подтверждения")
    def test_create_list_delete(self, page: Page):
        # подготовка
        register(page)
        campaigns = CampaignsPage(page).open()

        # вызов
        campaigns.create().create("Кампания E2E", ["DAREON", "MIRA", "KARA"])

        # проверка
        expect(page).to_have_url(re.compile(r"/campaigns/\d+/battle/new$"))
        campaigns = CampaignsPage(page).open()
        with check("Кампания в списке с главой «Пролог»"):
            assert campaigns.chapter_of("Кампания E2E") == "Пролог"
        with check("Отряд из трёх охотников с названиями классов"):
            expect(campaigns.item("Кампания E2E").get_by_test_id("campaign-item-hunters")).to_have_text(
                "Дареон — Дареон, Мира — Мира, Кара — Кара")
        campaigns.delete("Кампания E2E")

    @allure.title("Из списка кампаний — в главное меню")
    def test_back_to_menu(self, page: Page):
        # подготовка
        register(page)
        campaigns = CampaignsPage(page).open()
        campaigns.should_be_open()

        # вызов
        campaigns.to_menu()

        # проверка
        MainPage(page).should_be_open()
        expect(page).to_have_url(re.compile(r"/$"))
