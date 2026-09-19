import json

import allure
import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options


@pytest.fixture(scope="session")
def config():
    # Загружаем настройки из файла config.json
    with open("config.json", "r") as config_file:
        return json.load(config_file)

@pytest.fixture(scope="function")
def driver(config):
    options = UiAutomator2Options().load_capabilities(config["capabilities"])

    driver = webdriver.Remote(command_executor=config["appium_url"], options=options)
    yield driver
    driver.quit()

# Хук Pytest: если тест упал, автоматически прикрепляем скриншот в Allure
@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    if rep.failed:
        try:
            if "driver" in item.fixturenames:
                web_driver = item.funcargs["driver"]
                allure.attach(
                    web_driver.get_screenshot_as_png(),
                    name="Screenshot_on_failure",
                    attachment_type=allure.attachment_type.PNG
                )
        except Exception as e:
            print(f"Не удалось сделать скриншот: {e}")