#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
js = (root / 'src/main/resources/static/js/app.js').read_text(encoding='utf-8')
index = (root / 'src/main/resources/static/index.html').read_text(encoding='utf-8')
mockup = (root / 'src/main/resources/static/mockup.html').read_text(encoding='utf-8')

required = [
    '/api/session/me', '/api/system/health', '/api/dashboard', '/api/items',
    '/api/suppliers', '/api/receiving', '/api/approvals', '/api/issuances',
    '/api/batches', '/api/equipment', '/api/disposals', '/api/users',
    '/api/roles', '/api/settings', '/api/transaction-logs', '/api/reports'
]

problems = []
for name, source in [('app.js', js), ('index.html', index), ('mockup.html', mockup)]:
    if 'localStorage' in source or 'cimsMockupState' in source:
        problems.append(f'{name} still contains browser-local inventory persistence')

for endpoint in required:
    if endpoint not in js:
        problems.append(f'app.js does not reference {endpoint}')

if '/js/app.js' not in index or '/js/app.js' not in mockup:
    problems.append('Both served HTML entry points must load /js/app.js')

page_defs = re.findall(r"\['([^']+)',\s*(?:null|'[^']+')", js.split('const PAGE_DEFS',1)[1].split('];',1)[0])
for page in page_defs:
    if page not in js.split('const PAGES=',1)[1].split('};',1)[0]:
        problems.append(f'Navigation page {page!r} has no PAGES renderer')

if problems:
    print('REST wiring verification FAILED:')
    for problem in problems:
        print(' -', problem)
    sys.exit(1)

print('REST wiring verification PASSED')
print(f' - {len(required)} API areas referenced')
print(f' - {len(page_defs)} navigation screens mapped')
print(' - no localStorage inventory fallback in served files')
