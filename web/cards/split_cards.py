"""Нарезка печатных PDF карт снаряжения на отдельные карты для кнопки «Описание» (web qa № 151, 152).

    pip install pymupdf pillow
    python web/cards/split_cards.py <файл.pdf или папка> [...] [--dpi 250] [--quality 85]

Одна страница PDF — одна карта (так устроены печатные файлы игры PRM-CARDS_…-Front_RU_….pdf: оружие, шлемы,
доспехи, предметы, зелья; папка — все PDF в ней и во вложенных папках). Название и уровень берутся из текста
страницы (название бывает разбито на 2–3 строки) и сопоставляются:
- с каталогом кузницы (web/backend/src/main/resources/catalog/forge.yaml) — код предмета `FIRE_05`;
- с каталогом лаборатории (lab.yaml) — код зелья `LAB_01`;
- с базовым снаряжением стартового набора — код вида (ForgeSlot): оружие класса `DUAL_BLADES`, `HELMET`
  («Основной шлем»), `ARMOR` («Основной доспех»).
Карт одного предмета и уровня в печатных файлах несколько (по числу игроков) — берётся первая, остальные
должны совпадать с ней по тексту. Легендарное оружие (в кузнице не создаётся) пропускается. Страница, которую не
удалось сопоставить, — ошибка, ничего не записывается.

Карты сохраняются WebP-файлами `<код>-<уровень>.webp` сразу для сайта и для приложения, список карт —
`cards.json` (новые карты добавляются к уже нарезанным). Исходные PDF в репозитории не хранятся.
"""
import argparse
import io
import json
import re
import sys
from pathlib import Path

import pymupdf
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'web/backend/src/main/resources/catalog'
FORGE_SLOT = ROOT / 'web/backend/src/main/java/com/primal/rules/model/ForgeSlot.java'
OUTPUTS = [
    # (папка карт, файл списка карт)
    (ROOT / 'web/frontend/public/cards', ROOT / 'web/frontend/src/shared/cards/cards.json'),
    (ROOT / 'app/androidApp/src/main/assets/cards', ROOT / 'app/androidApp/src/main/assets/cards/cards.json'),
]
LEVEL = re.compile(r'([123])-Й УРОВЕНЬ')
# Базовое снаряжение: название в инвентаре (InventoryService.startingKit) → вид; на карте название бывает другим
BASIC = {'Основной шлем': 'HELMET', 'Основной доспех': 'ARMOR'}
CARD_NAMES = {'Острое копьё': 'Копьё', 'Боевой барабан': 'Барабан', 'Арбалет-пушка': 'Пушка'}
LEGENDARY = 'легендарное.'


def normalize(text: str) -> str:
    text = text.replace('­', '').replace('ё', 'е').replace('Ё', 'Е')
    return re.sub(r'\s+', ' ', text).strip().casefold()


def known_names() -> tuple[dict[str, tuple[str, str]], dict[str, tuple[str, str]]]:
    """Предметы каталогов и базовое снаряжение по нормализованному названию: (код, название как в приложении)."""
    catalog: dict[str, tuple[str, str]] = {}
    element, index = None, 0
    for line in (CATALOG / 'forge.yaml').read_text(encoding='utf-8').splitlines():
        if match := re.match(r'- element: (\w+)', line):
            element, index = match.group(1), 0
        elif match := re.search(r'\{ name: ([^,]+), slot: \w+', line):
            index += 1
            catalog[normalize(match.group(1))] = (f'{element}_{index:02d}', match.group(1).strip())
    potions = re.findall(r'^- \{ name: ([^,]+),', (CATALOG / 'lab.yaml').read_text(encoding='utf-8'), re.M)
    for index, name in enumerate(potions, start=1):
        catalog[normalize(name)] = (f'LAB_{index:02d}', name.strip())

    basic: dict[str, tuple[str, str]] = {}
    for slot, name in re.findall(r'(\w+)\("([^"]+)", HunterClass\.\w+\)', FORGE_SLOT.read_text(encoding='utf-8')):
        basic[normalize(name)] = (slot, name)
    for name, slot in BASIC.items():
        basic[normalize(name)] = (slot, name)
    for card_name, name in CARD_NAMES.items():
        basic[normalize(card_name)] = basic[normalize(name)]
    return catalog, basic


def candidates(lines: list[str]) -> set[str]:
    """Строки страницы и их склейки по 2–3 подряд: название карты бывает разбито («ТЕМНОКАМЕННЫЙ | ЩИТ»)."""
    found = set()
    for start in range(len(lines)):
        for size in (1, 2, 3):
            part = lines[start:start + size]
            if len(part) == size:
                found.add(normalize(' '.join(part)))
                found.add(normalize(''.join(part)))  # перенос по дефису: «МИНИ-» + «АРБАЛЕТ»
    return found


def identify(page: pymupdf.Page, catalog: dict, basic: dict) -> tuple[str, str, int] | None:
    """(код, название, уровень) карты; легендарное оружие — None."""
    lines = [line.strip() for line in page.get_text().splitlines() if line.strip()]
    if any(normalize(line) == LEGENDARY for line in lines):
        return None
    levels = {int(m.group(1)) for line in lines if (m := LEVEL.search(line))}
    texts = candidates(lines)
    names = {catalog[text] for text in texts if text in catalog}
    if not names:  # «Молот» — и базовое оружие, и часть названия «Костяной молот»: каталог важнее
        names = {basic[text] for text in texts if text in basic}
    if len(levels) != 1 or len(names) != 1:
        raise ValueError(f'страница {page.number + 1}: не удалось определить карту (названия {names}, уровни {levels})')
    (code, name), level = names.pop(), levels.pop()
    return code, name, level


def render(page: pymupdf.Page, dpi: int, quality: int) -> bytes:
    pixmap = page.get_pixmap(dpi=dpi)
    image = Image.open(io.BytesIO(pixmap.tobytes('png'))).convert('RGB')
    buffer = io.BytesIO()
    image.save(buffer, 'WEBP', quality=quality, method=6)
    return buffer.getvalue()


def pdfs(paths: list[Path]) -> list[Path]:
    found = []
    for path in paths:
        found += sorted(path.rglob('*.pdf')) if path.is_dir() else [path]
    return found


def main() -> None:
    parser = argparse.ArgumentParser(description='Нарезка PDF с картами снаряжения на отдельные карты.')
    parser.add_argument('pdf', nargs='+', type=Path, help='PDF или папка с PDF')
    parser.add_argument('--dpi', type=int, default=250, help='разрешение карты (250 dpi — оружие 689×1182 px)')
    parser.add_argument('--quality', type=int, default=85, help='качество WebP')
    args = parser.parse_args()

    catalog, basic = known_names()
    cards: dict[tuple[str, int], tuple[str, bytes]] = {}
    texts: dict[tuple[str, int], str] = {}
    for path in pdfs(args.pdf):
        try:
            for page in pymupdf.open(path):
                where = f'{path.name} стр. {page.number + 1}'
                card = identify(page, catalog, basic)
                if card is None:
                    print(f'{where}: легендарное оружие — пропущено')
                    continue
                code, name, level = card
                text = normalize(page.get_text())
                if (code, level) in cards:
                    if texts[(code, level)] != text:
                        sys.exit(f'{where}: карта {code} {level}-го уровня уже есть, но текст другой')
                    continue
                cards[(code, level)] = (name, render(page, args.dpi, args.quality))
                texts[(code, level)] = text
                print(f'{where}: {name}, {level}-й уровень → {code}-{level}.webp')
        except ValueError as error:
            sys.exit(f'{path.name}, {error}')

    for folder, manifest_path in OUTPUTS:
        folder.mkdir(parents=True, exist_ok=True)
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {'cards': []}
        entries = {(card['code'], card['level']): card for card in manifest['cards']}
        for (code, level), (name, data) in cards.items():
            file = f'{code}-{level}.webp'
            (folder / file).write_bytes(data)
            entries[(code, level)] = {'code': code, 'level': level, 'name': name, 'file': file}
        ordered = sorted(entries.values(), key=lambda card: (card['code'], card['level']))
        manifest_path.write_text(json.dumps({'cards': ordered}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Карт: {len(cards)} → ' + ', '.join(str(folder.relative_to(ROOT)) for folder, _ in OUTPUTS))


if __name__ == '__main__':
    main()
