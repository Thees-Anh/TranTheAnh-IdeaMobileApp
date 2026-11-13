"""Validate first-party layout, documentation links and literal tool/design paths."""
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())


def main():
    errors = []
    for directory in [
        'androidApp/src/main', 'shared/src/commonMain', 'shared/src/androidMain',
        'shared/src/commonTest', 'shared/src/androidDeviceTest', 'iosApp',
        'supabase/migrations', 'supabase/tests', 'supabase/templates', 'config/examples',
        'design/prototypes/pets', 'design/prototypes/focus', 'design/assets/decor',
        'design/assets/pets/puppy', 'tools/assets', 'tools/balance', 'tools/validation',
        'tools/testing/fixtures', 'docs/features', 'docs/backend', 'docs/rendering',
        'docs/design', 'docs/product', 'docs/archive', 'docs/media/previews',
    ]:
        if not (ROOT / directory).is_dir(): errors.append(f'Missing directory: {directory}')
    for path in ROOT.iterdir():
        if path.is_file() and path.suffix.lower() in {'.html', '.svg', '.py', '.mjs'}:
            errors.append(f'Loose design/tool file at root: {path.name}')

    documents = [ROOT / 'README.md']
    for folder in ['docs', 'design', 'tools']:
        documents.extend((ROOT / folder).rglob('*.md'))
    links = 0
    for path in documents:
        text = path.read_text(encoding='utf-8')
        for match in re.finditer(r'\]\(([^\n)]*)\)', text):
            target = match.group(1).strip().strip('<>')
            if not target or target.startswith('#'): continue
            parsed = urlsplit(target)
            if parsed.scheme in {'http', 'https', 'mailto'}: continue
            if parsed.scheme:
                errors.append(f'{path.relative_to(ROOT)}: nonportable link: {target}')
                continue
            filename = unquote(parsed.path)
            if not filename: continue
            candidate = (path.parent / filename).resolve()
            try: relative = candidate.relative_to(ROOT)
            except ValueError:
                errors.append(f'{path.relative_to(ROOT)}: link outside repository: {target}')
                continue
            if 'build' in relative.parts: continue  # Optional local execution evidence.
            if not candidate.exists(): errors.append(f'{path.relative_to(ROOT)}: broken link: {target}')
            links += 1

    sources = documents + list((ROOT / 'tools').rglob('*.py')) + list((ROOT / 'tools').rglob('*.mjs'))
    # Match concrete first-party paths, not f-strings, globs, fixture URLs or illustrative directories.
    pattern = re.compile(r'(?<![\w/])(?:tools|design|config|supabase)/[\w./-]+\.(?:py|mjs|html|svg|json|css|example)\b')
    references = 0
    for path in sources:
        for match in pattern.finditer(path.read_text(encoding='utf-8')):
            target = match[0]
            if not (ROOT / target).is_file() and not (path.parent / target).is_file():
                errors.append(f'{path.relative_to(ROOT)}: missing referenced file: {target}')
            references += 1
    if errors:
        raise SystemExit('\n'.join(sorted(set(errors))))
    print(f'PASS project layout, {links} documentation links and {references} concrete source references')


if __name__ == '__main__': main()
