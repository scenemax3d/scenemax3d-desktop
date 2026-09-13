"""Audit the migrated SceneMax highlighting vocabulary; no Java runtime needed."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
java = (root.parent / "src/com/scenemax/desktop/SceneMaxTokenManager.java").read_text(encoding="utf-8")
rust = (root / "IDE/core/src/syntax.rs").read_text(encoding="utf-8")
count = 0
for name in ("SCOPE_WORDS", "KEYWORDS", "DATA_TYPES", "FUNCTIONS"):
    old = re.search(r"String\[\] " + name + r" = \{(.*?)\};", java, re.S)
    new = re.search(r"const " + name + r": &\[&str\] = &\[(.*?)\];", rust, re.S)
    assert old and new, f"Missing vocabulary: {name}"
    expected = set(re.findall(r'"([^"\n]+)"', old.group(1)))
    actual = re.findall(r'"([^"\n]+)"', new.group(1))
    assert set(actual) == expected, f"Vocabulary differs: {name}: {set(actual) ^ expected}"
    assert actual == sorted(set(actual)), f"Binary search requires sorted unique words: {name}"
    count += len(actual)
print(f"SceneMax syntax parity passed: {count} Java editor vocabulary entries")
