#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:?usage: apply-cyhai-branding.sh <pocketpal-root>}"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])

ui = root / "src/store/UIStore.ts"
s = ui.read_text()
old = "_language: AvailableLanguage = 'en';"
new = "_language: AvailableLanguage = 'zh';"
if old not in s:
    raise SystemExit("Could not find UIStore default language; refusing an unsafe patch")
ui.write_text(s.replace(old, new, 1))

app = root / "app.json"
s = app.read_text()
s = s.replace('"name": "PocketPal"', '"name": "Cyhai"', 1)
s = s.replace('"displayName": "PocketPal"', '"displayName": "Cyhai"', 1)
app.write_text(s)

strings = root / "android/app/src/main/res/values/strings.xml"
s = strings.read_text()
s = s.replace("<string name=\"app_name\">PocketPal</string>",
              "<string name=\"app_name\">Cyhai</string>", 1)
strings.write_text(s)
PY
