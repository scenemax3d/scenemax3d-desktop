"""Audit the migration menu snapshot against the existing Swing menu definition."""
import json
import re
from pathlib import Path

workspace = Path(__file__).resolve().parents[1]
reference = json.loads((workspace.parent / "assets/menu/main_menu").read_text(encoding="utf-8"))
source = (workspace / "IDE/app/src/presentation/menu_catalog.rs").read_text(encoding="utf-8")

# User-approved omissions from the Bevy IDE (2026-09-23).
excluded_commands = {"load_from_cloud", "macro_builder"}

def flatten(items):
    result = []
    for item in items:
        if item.get("command") in excluded_commands:
            continue
        result.append((item["name"], item.get("command", "")))
        result.extend(flatten(item.get("items", [])))
    return result

names = [json.loads(value) for value in re.findall(r'name:\s*("(?:[^"\\]|\\.)*")', source)]
commands = [json.loads(value) for value in re.findall(r'command:\s*("(?:[^"\\]|\\.)*")', source)]
expected = flatten(reference["items"])
actual = list(zip(names, commands))
if actual != expected:
    raise SystemExit("Menu parity failed: names, order or command IDs differ from the Java IDE")
print(f"Java menu parity passed: {len(expected)} menus/items, same retained names, order and command IDs (cloud/snippets intentionally omitted).")
