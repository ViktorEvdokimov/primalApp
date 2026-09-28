import re
import time

import httpx

CODE_PATTERN = re.compile(r"\b(\d{6})\b")


class MailpitClient:
    """Чтение писем из Mailpit через HTTP API (https://mailpit.axllent.org/docs/api-v1/)."""

    def __init__(self, base_url: str):
        self.client = httpx.Client(base_url=base_url, timeout=10)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.client.close()

    def delete_all(self) -> None:
        """Очистить ящик, чтобы тест видел только свои письма."""
        self.client.delete("/api/v1/messages").raise_for_status()

    def latest_login_code(self, email: str, timeout: float = 15) -> str:
        """Код входа из последнего письма на адрес email; ждёт письмо до timeout секунд."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            messages = self.client.get("/api/v1/search", params={"query": f"to:{email}"}).json()["messages"]
            if messages:
                message = self.client.get(f"/api/v1/message/{messages[0]['ID']}").json()
                match = CODE_PATTERN.search(message["Text"])
                if match:
                    return match.group(1)
            time.sleep(0.5)
        raise AssertionError(f"Письмо с кодом на {email} не пришло за {timeout} с")
