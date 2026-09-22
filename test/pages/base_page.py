from selenium.common import NoSuchElementException, TimeoutException
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from appium.webdriver.common.appiumby import AppiumBy

class BasePage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 20)

    def click(self, locator: tuple[str, str]) -> None:
        element = self.wait.until(EC.element_to_be_clickable(locator))
        element.click()

    def type_text(self, locator: tuple[str, str], text: str) -> None:
        element = self.wait.until(EC.element_to_be_clickable(locator))
        element.clear()
        element.send_keys(text)

    def get_text(self, locator: tuple[str, str]) -> str:
        element = self.wait.until(EC.visibility_of_element_located(locator))
        return element.text


    def is_element_visible(self, locator:  tuple[str, str]) -> bool:
        try:
            self.wait.until(EC.visibility_of_element_located(locator))
            return True
        except TimeoutException:
            return False

    def is_element_visible_quick(self, locator: tuple[str, str], timeout: int = 2) -> bool:
        try:
            quick_wait = WebDriverWait(self.driver, timeout)
            quick_wait.until(EC.visibility_of_element_located(locator))
            return True
        except TimeoutException:
            return False

    def scroll_to_text(self, text: str, max_swipes: int = 30) -> None:
        ui_selector = (
            f'new UiScrollable(new UiSelector().scrollable(true))'
            f'.setMaxSearchSwipes({max_swipes})'
            f'.scrollIntoView(new UiSelector().text("{text}"))'
        )
        self.driver.find_element(AppiumBy.ANDROID_UIAUTOMATOR, ui_selector)

