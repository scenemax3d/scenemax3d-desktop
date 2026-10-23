"""Create subdirectory and missing-dependency variants of a real exported ZIP.

Serve each with `web_effects --preview ZIP`; no browser or runtime is mocked.
"""
import json
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

source = Path(sys.argv[1])
for name in ("subdirectory", "missing-dependency"):
    output = source.with_name(f"{name}.zip")
    with ZipFile(source) as original, ZipFile(output, "w", ZIP_DEFLATED) as result:
        if name == "subdirectory":
            result.writestr("index.html", '<a href="releases/demo/index.html">Open nested game</a>')
        for entry in original.infolist():
            if entry.is_dir():
                continue
            data = original.read(entry)
            if name == "missing-dependency":
                if entry.filename.endswith("/Texture/Particle01.png"):
                    continue
                if entry.filename == "game-manifest.json":
                    manifest = json.loads(data)
                    manifest["files"] = [p for p in manifest["files"] if not p.endswith("/Texture/Particle01.png")]
                    data = json.dumps(manifest).encode()
            path = "releases/demo/" + entry.filename if name == "subdirectory" else entry.filename
            result.writestr(path, data)
    print(output.resolve())
