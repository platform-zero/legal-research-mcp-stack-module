#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
python3 - "$repo_root/stack.module.json" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1]).parent
for path in json.loads((root / "stack.module.json").read_text())["overlays"]:
    assert (root / path).exists(), path
PY
