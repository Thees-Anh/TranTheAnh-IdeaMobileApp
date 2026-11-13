"""Check bilingual UI literals and the supplied catalog without network access."""
import json
import re
from pathlib import Path

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
source = ROOT / 'shared/src/commonMain/kotlin/com/pawquest/localization/Translations.kt'
rows = source.read_text(encoding='utf-8').split('"""')[1].strip().splitlines()
pairs = [row.split('|', 1) for row in rows]
assert all(len(pair) == 2 and all(pair) for pair in pairs)
known = {value for pair in pairs for value in pair}
placeholder = re.compile(r'\{\d+\}')
for en, vi in pairs:
    assert set(placeholder.findall(en)) == set(placeholder.findall(vi)), en

checks = 0
for root in ['shared/src/commonMain/kotlin/com/pawquest/presentation',
             'shared/src/androidMain/kotlin/com/pawquest/presentation']:
    for path in (ROOT / root).rglob('*.kt'):
        for match in re.finditer(r'\btr\("((?:\\.|[^"\\$])*)"\)', path.read_text(encoding='utf-8')):
            text = match.group(1)
            if not re.search(r'[A-Za-zÀ-ỹ]', text) or text in {'PawQuest'}:
                continue
            assert text in known, f'{path.name}: untranslated system copy: {text}'
            checks += 1
catalog = json.loads((ROOT / 'design/assets/decor/catalog.json').read_text(encoding='utf-8'))
for item in catalog:
    for key in ['name', 'desc']:
        assert item[key] in known, f'{item["id"]}: missing {key}'
print(f'PASS {len(pairs)} bilingual pairs, {checks} static UI references, all 20 decor names/descriptions')
