from selenium.common import TimeoutException
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from appium.webdriver.common.appiumby import AppiumBy

class BasePage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 10)

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

    def scroll_to_element(self, locator: tuple[str, str]) -> 'WebElement':
        ui_selector = 'new UiScrollable(new UiSelector().scrollable(true))'

        match locator[0]:
            case AppiumBy.ANDROID_UIAUTOMATOR:
                selector = locator[1]
            case AppiumBy.LINK_TEXT:
                selector = f'{ui_selector}.scrollIntoView(new UiSelector().text("{locator[1]}"))'
            case AppiumBy.ID:
                selector = f'{ui_selector}.scrollIntoView(new UiSelector().resourceId("{locator[1]}"))'
            case AppiumBy.ACCESSIBILITY_ID:
                selector = f'{ui_selector}.scrollIntoView(new UiSelector().description("{locator[1]}"))'
            case _:
                raise ValueError(f"Тип локатора {locator[0]} не поддерживается для UiScrollable")

        return self.driver.find_element(
            AppiumBy.ANDROID_UIAUTOMATOR,
            selector
        )

    def scroll_to_text(self, text: str) -> 'WebElement':
        return self.scroll_to_element((AppiumBy.LINK_TEXT, text))

