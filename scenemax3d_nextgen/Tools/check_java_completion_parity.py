"""Audit the migrated static SceneMax completion catalog."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
java = (root.parent / "src/com/scenemax/desktop/SceneMaxAutoComplete.java").read_text(encoding="utf-8")
rust = (root / "IDE/core/src/completion.rs").read_text(encoding="utf-8")
count = 0
for name in ("KEYWORDS", "BUILTIN_FUNCTIONS", "COLORS", "EFFECTS", "INPUT_KEYS"):
    old = re.search(r"String\[\] " + name + r" = \{(.*?)\};", java, re.S)
    new = re.search(r"const " + name + r": &\[&str\] = &\[(.*?)\];", rust, re.S)
    assert old and new, f"Missing completion catalog: {name}"
    expected = re.findall(r'"([^"\n]+)"', old.group(1))
    actual = re.findall(r'"([^"\n]+)"', new.group(1))
    assert actual == expected, f"Completion catalog differs: {name}"
    count += len(actual)
print(f"Java completion catalog parity passed: {count} entries, same spelling and order")
