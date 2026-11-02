"""Check repository Markdown links and keep docs navigation complete after moves."""
import pathlib
import re
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]


def check(root=ROOT):
    errors = []
    docs = root / 'docs'
    for entry in docs.iterdir():
        if entry.is_file() and entry.name != 'README.md':
            errors.append('Unclassified docs root file: ' + entry.name)
    documents = [root / 'README.md', root / 'AGENTS.md', *docs.rglob('*.md')]
    targets = set()
    for document in documents:
        for raw in re.findall(r'\]\(([^)]+)\)', document.read_text(encoding='utf-8')):
            parsed = urllib.parse.urlsplit(raw)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            target = (document.parent / urllib.parse.unquote(parsed.path)).resolve()
            if not target.is_relative_to(root.resolve()) or not target.exists():
                errors.append(str(document.relative_to(root)) + ': broken link ' + raw)
            if document.name == 'README.md':
                targets.add(target)
    for entry in docs.rglob('*'):
        if entry.is_file() and entry.suffix in ('.md', '.json') and entry != docs / 'README.md':
            if entry.resolve() not in targets:
                errors.append('Missing navigation: ' + str(entry.relative_to(root)))
    return errors


if __name__ == '__main__':
    failures = check()
    if failures:
        raise SystemExit('\n'.join(failures))
    print('PASS: Markdown links and classified docs navigation')
