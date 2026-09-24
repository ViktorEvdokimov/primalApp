from selenium.common import NoSuchElementException, TimeoutException
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

    def is_element_visible_quick(self, locator: tuple[str, str], timeout: int = 2) -> bool:
        try:
            quick_wait = WebDriverWait(self.driver, timeout)
            quick_wait.until(EC.visibility_of_element_located(locator))
            return True
        except TimeoutException:
            return False

    def scroll_into_view(self, text: str, partial: bool = False, max_swipes: int = 15) -> None:
        """Скроллит основной экран, пока элемент с текстом не станет реально видимым.

        В отличие от scroll_to_text не доверяет UiScrollable: Compose отдаёт узлы за пределами экрана,
        и UiScrollable считает их найденными без прокрутки. Сначала листает вниз, затем вверх.
        """
        selector = f'new UiSelector().{"textStartsWith" if partial else "text"}("{text}")'
        for direction in ("down", "up"):
            previous_source = None
            for _ in range(max_swipes):
                if any(el.is_displayed() for el in self.driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, selector)):
                    return
                source = self.driver.page_source
                if source == previous_source:
                    break  # экран не изменился — достигнут край списка
                previous_source = source
                scrollables = self.driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().scrollable(true)')
                if not scrollables:
                    break
                # Возвращаемый флаг «можно скроллить дальше» для Compose ненадёжен, поэтому край определяется по page_source
                self.driver.execute_script("mobile: scrollGesture", {
                    "elementId": scrollables[0].id, "direction": direction, "percent": 0.5
                })
        if not any(el.is_displayed() for el in self.driver.find_elements(AppiumBy.ANDROID_UIAUTOMATOR, selector)):
            raise NoSuchElementException(f"Элемент с текстом «{text}» не найден при прокрутке")

    def scroll_to_text(self, text: str, max_swipes: int = 30) -> None:
        ui_selector = (
            f'new UiScrollable(new UiSelector().scrollable(true))'
            f'.setMaxSearchSwipes({max_swipes})'
            f'.scrollIntoView(new UiSelector().text("{text}"))'
        )
        self.driver.find_element(AppiumBy.ANDROID_UIAUTOMATOR, ui_selector)

