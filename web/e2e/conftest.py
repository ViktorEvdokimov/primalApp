import json
from pathlib import Path

import allure
import httpx
import pytest


CONFIG_PATH = Path(__file__).parent / "config.json"


@pytest.fixture(scope="session")
def config():
    """Настройки из config.json: адрес сайта (по умолчанию — локальный стек, doc/setup.md, раздел 2)."""
    with CONFIG_PATH.open(encoding="utf-8") as config_file:
        return json.load(config_file)


@pytest.fixture(scope="session")
def base_url(config):
    """Адрес сайта для Playwright: page.goto("/") открывает главную."""
    return config["base_url"]


@pytest.fixture(scope="session")
def browser_context_args(browser_context_args):
    """Контекст браузера как у телефона у игрового стола."""
    return {**browser_context_args, "locale": "ru-RU", "viewport": {"width": 412, "height": 915}}


@pytest.fixture(scope="session")
def api(config):
    """HTTP-клиент к API сайта (через Caddy, как у браузера)."""
    with httpx.Client(base_url=config["base_url"], timeout=10) as client:
        yield client


# Хук pytest: если тест упал, прикрепляем снимок экрана браузера в Allure
@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    if report.when == "call" and report.failed and "page" in item.fixturenames:
        try:
            page = item.funcargs["page"]
            allure.attach(page.screenshot(full_page=True), name="Снимок при падении",
                          attachment_type=allure.attachment_type.PNG)
        except Exception as error:  # noqa: BLE001 — снимок не должен мешать отчёту
            print(f"Не удалось сделать снимок экрана: {error}")
