import allure
from playwright.sync_api import Locator, Page, TimeoutError as PlaywrightTimeoutError, expect

from pages.base_page import BasePage


class CampaignSheetPage(BasePage):
    """Лист кампании (задача 4.6) — аналог CampaignSheetScreen мобильного приложения."""

    root_test_id = "page-campaign-sheet"

    MATERIAL_NAMES = ["Чешуя", "Кости", "Кровь", "Зимия", "Иридия", "Златия"]
    PLANT_NAMES = ["Ниллея", "Тармарет", "Альбалацея", "Меллис", "Антемон", "Селикорния"]
    ELEMENT_NAMES = ["Огонь", "Рог", "Коралл", "Кристалл", "Молния", "Металл", "Перо", "Яд", "Лёд"]

    def __init__(self, page: Page, campaign_id: int | None = None):
        super().__init__(page)
        if campaign_id is not None:
            self.path = f"/campaigns/{campaign_id}"

    def should_be_open(self) -> None:
        """Лист загружен: пока он грузится, на странице только индикатор загрузки. Под нагрузкой (второй
        браузер, service worker скачивает статику) — до 10 секунд."""
        expect(self.by_test_id("sheet-name")).to_be_visible(timeout=10000)

    def _shown(self, test_id: str, timeout: float = 5000) -> bool:
        """Элемент появился: вкладки переключаются не мгновенно, поэтому ждём, а не смотрим один раз."""
        try:
            self.by_test_id(test_id).wait_for(state="visible", timeout=timeout)
            return True
        except PlaywrightTimeoutError:
            return False

    # --- шапка ---

    def campaign_name(self) -> str:
        return self.by_test_id("sheet-name").text_content() or ""

    def chapter(self) -> int:
        return int(self.by_test_id("sheet-chapter").get_attribute("data-chapter") or "-1")

    def chapter_text(self) -> str:
        return self.by_test_id("sheet-chapter").text_content() or ""

    def forge_level(self) -> int:
        return int(self.by_test_id("sheet-forge").get_attribute("data-level") or "0")

    def lab_level(self) -> int:
        return int(self.by_test_id("sheet-lab").get_attribute("data-level") or "0")

    @allure.step("Изменить главу на {chapter}")
    def change_chapter(self, chapter: int, confirm: bool = True) -> "CampaignSheetPage":
        self.click("chapter-edit")
        self.by_test_id("chapter-input").fill(str(chapter))
        self.click("chapter-save" if confirm else "chapter-cancel")
        expect(self.by_test_id("chapter-input")).to_be_hidden()
        return self

    @allure.step("Вернуться к списку кампаний")
    def back_to_list(self) -> None:
        self.click("sheet-back")

    @allure.step("Удалить кампанию")
    def delete_campaign(self) -> None:
        self.click("sheet-delete")
        self.click("sheet-delete-confirm")

    @allure.step("Открыть вкладку «{name}»")
    def open_tab(self, name: str) -> "CampaignSheetPage":
        """Вкладки: hunters, quests, achievements, trophies, notes."""
        self.click(f"sheet-tab-{name}")
        return self

    # --- охотники и навыки ---

    def hunters(self) -> list[tuple[str, str]]:
        """(имя игрока, класс) охотников отряда по порядку."""
        options = self.by_test_id("hunter-option")
        expect(options.first).to_be_visible()
        return [(option.get_attribute("data-player") or "", option.get_attribute("data-class") or "")
                for option in options.all()]

    @allure.step("Выбрать охотника «{player}»")
    def select_hunter(self, player: str) -> None:
        self.page.locator(f'[data-testid="hunter-option"][data-player="{player}"]').click()

    def skill(self, label: str) -> Locator:
        """Кнопка навыка по подписи: «А1», «Д2»."""
        return self.by_test_id("skill-tree").locator(f'[data-skill="{label}"]')

    def skill_tree(self) -> dict[str, list[str]]:
        """Ветви древа: буква → подписи ступеней по порядку."""
        tree: dict[str, list[str]] = {}
        for button in self.by_test_id("skill-tree").locator("[data-skill]").all():
            label = button.get_attribute("data-skill") or ""
            tree.setdefault(label[0], []).append(label)
        return {letter: sorted(labels) for letter, labels in tree.items()}

    def is_skill_unlocked(self, label: str) -> bool:
        """Состояние читается из aria-pressed — как атрибут checked кнопки навыка в app (задача 42.2)."""
        return self.skill(label).get_attribute("aria-pressed") == "true"

    def is_skill_available(self, label: str) -> bool:
        skill = self.skill(label)
        return skill.is_enabled() and skill.get_attribute("aria-disabled") != "true"

    @allure.step("Нажать навык «{label}»")
    def click_skill(self, label: str, unlocked_after: bool) -> None:
        self.skill(label).click()
        expect(self.skill(label)).to_have_attribute("aria-pressed", "true" if unlocked_after else "false")

    # --- ресурсы ---

    def resource(self, name: str) -> Locator:
        return self.page.locator(f'[data-testid="resource"][data-name="{name}"]')

    def resource_value(self, name: str) -> int:
        return int(self.resource(name).get_by_test_id("resource-value").text_content() or "-1")

    def resources(self, names: list[str]) -> dict[str, int]:
        return {name: self.resource_value(name) for name in names}

    def is_decrement_enabled(self, name: str) -> bool:
        return self.resource(name).get_by_test_id("resource-decrement").is_enabled()

    @allure.step("«+» у ресурса «{name}»")
    def increment(self, name: str) -> "CampaignSheetPage":
        self.resource(name).get_by_test_id("resource-increment").click()
        return self

    @allure.step("«−» у ресурса «{name}»")
    def decrement(self, name: str) -> "CampaignSheetPage":
        self.resource(name).get_by_test_id("resource-decrement").click()
        return self

    @allure.step("Дождаться сохранения ресурсов")
    def wait_resources_saved(self) -> None:
        expect(self.by_test_id("resources")).to_have_attribute("data-saving", "false")

    # --- задания ---

    def _numbers(self, test_id: str) -> list[int]:
        return [int(item.get_attribute("data-number") or "0") for item in self.by_test_id(test_id).all()]

    def open_quests(self) -> list[int]:
        return self._numbers("quest-open")

    def completed_quests(self) -> list[int]:
        return self._numbers("quest-completed")

    def has_no_open_quests(self) -> bool:
        return self._shown("quests-empty")

    def open_quest(self, number: int) -> Locator:
        return self.page.locator(f'[data-testid="quest-open"][data-number="{number}"]')

    @allure.step("«Выполнено» у задания {number}")
    def complete_quest(self, number: int) -> None:
        self.open_quest(number).get_by_test_id("quest-complete").click()
        expect(self.page.locator(f'[data-testid="quest-completed"][data-number="{number}"]')).to_be_visible()

    @allure.step("«Отмена» у выполненного задания {number}")
    def reopen_quest(self, number: int) -> None:
        self.page.locator(f'[data-testid="quest-completed"][data-number="{number}"]').get_by_test_id("quest-reopen").click()
        expect(self.open_quest(number)).to_be_visible()

    @allure.step("Открыть редактор заданий")
    def edit_quests(self) -> "QuestEditorDialog":
        self.click("quests-edit")
        dialog = QuestEditorDialog(self.page)
        dialog.should_be_open()
        return dialog

    # --- достижения, трофеи, заметки ---

    def achievements(self) -> list[str]:
        return [item.get_attribute("data-name") or "" for item in self.by_test_id("achievement").all()]

    def has_no_achievements(self) -> bool:
        return self._shown("achievements-empty")

    @allure.step("Добавить достижение «{name}»")
    def add_achievement(self, name: str, shown_as: str | None = None) -> "CampaignSheetPage":
        field = self.by_test_id("achievement-input")
        field.fill(name)
        field.press("Escape")  # закрыть подсказки
        self.click("achievement-add")
        expect(self.page.locator(f'[data-testid="achievement"][data-name="{shown_as or name}"]')).to_be_visible()
        return self

    @allure.step("Удалить достижение «{name}»")
    def remove_achievement(self, name: str) -> "CampaignSheetPage":
        item = self.page.locator(f'[data-testid="achievement"][data-name="{name}"]')
        item.get_by_test_id("achievement-remove").click()
        expect(item).to_have_count(0)
        return self

    def has_no_trophies(self) -> bool:
        return self._shown("trophies-empty")

    @allure.step("Написать заметки «{text}» и дождаться сохранения")
    def set_notes(self, text: str) -> "CampaignSheetPage":
        self.by_test_id("notes-input").fill(text)
        expect(self.by_test_id("notes-status")).to_have_attribute("data-status", "saved")
        return self

    def notes(self) -> str:
        return self.by_test_id("notes-input").input_value()


class QuestEditorDialog(BasePage):
    """Редактор открытых заданий: флажок — задание открыто."""

    root_test_id = "quest-editor"

    def item(self, number: int) -> Locator:
        return self.page.locator(f'[data-testid="quest-editor-item"][data-number="{number}"]')

    def checked(self) -> list[int]:
        items = self.by_test_id("quest-editor-item")
        expect(items.first).to_be_visible()
        return [int(item.get_attribute("data-number") or "0") for item in items.all() if item.is_checked()]

    def is_checked(self, number: int) -> bool:
        return self.item(number).is_checked()

    def is_enabled(self, number: int) -> bool:
        return self.item(number).is_enabled()

    @allure.step("Задание {number}: открыто = {checked}")
    def set_checked(self, number: int, checked: bool) -> "QuestEditorDialog":
        self.item(number).set_checked(checked)
        return self

    @allure.step("Сохранить задания")
    def save(self) -> None:
        self.click("quest-editor-save")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()

    @allure.step("Отмена в редакторе заданий")
    def cancel(self) -> None:
        self.click("quest-editor-cancel")
        expect(self.by_test_id(self.root_test_id)).to_be_hidden()
