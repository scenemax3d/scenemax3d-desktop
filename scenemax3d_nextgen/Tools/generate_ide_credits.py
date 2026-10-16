"""Regenerate embedded Rust IDE credits from the locked Windows dependency graph."""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
metadata = json.loads(subprocess.check_output([
    "cargo", "metadata", "--locked", "--format-version", "1",
    "--filter-platform", "x86_64-pc-windows-msvc"], cwd=ROOT))
packages = {p["id"]: p for p in metadata["packages"]}
nodes = {n["id"]: n for n in metadata["resolve"]["nodes"]}
queue = [p["id"] for p in packages.values() if p["name"] == "scenemax_ide"]
seen = set()
while queue:
    pid = queue.pop()
    if pid in seen:
        continue
    seen.add(pid)
    queue.extend(d["pkg"] for d in nodes[pid]["deps"]
                 if any(k["kind"] != "dev" for k in d["dep_kinds"]))
entries = []
for pid in sorted(seen):
    p = packages[pid]
    if p["name"].startswith("scenemax_"):
        continue
    folder = Path(p["manifest_path"]).parent
    files = [f for f in folder.iterdir() if f.is_file() and
             f.name.lower().startswith(("license", "licence", "copying", "notice"))]
    if p.get("license_file"):
        files.append(folder / p["license_file"])
    texts = [f.name + "\n" + f.read_text(encoding="utf-8", errors="replace")
             for f in sorted(set(files)) if f.is_file()]
    entries.append(dict(name=p["name"], version=p["version"],
        authors=", ".join(p["authors"]) or p["name"] + " contributors",
        license=p.get("license") or "See upstream license",
        url=p.get("repository") or p.get("homepage") or "https://crates.io/crates/" + p["name"],
        group="Rust libraries", notices="\n\n".join(texts)))

def native(name, authors, license, url, files, version=""):
    texts = [Path(f).name + "\n" + (ROOT / f).read_text(encoding="utf-8", errors="replace") for f in files]
    entries.append(dict(name=name, version=version, authors=authors, license=license,
                        url=url, group="Native tools & adapted code", notices="\n\n".join(texts)))
native("Renzora gizmo", "Renzora contributors", "MIT", "https://github.com/renzora/engine",
       ["IDE/third_party/renzora_gizmo/NOTICE.md", "IDE/third_party/renzora_gizmo/LICENSE-MIT"], "60c0879")
native("Effekseer / Vulkan runtime", "Effekseer Project and native dependency contributors", "MIT and bundled dependency licenses",
       "https://github.com/effekseer/Effekseer", ["../third_party/Effekseer/LICENSE_RUNTIME_VULKAN"])
native("gltfpack / meshoptimizer", "Arseny Kapoulkine and contributors", "MIT", "https://github.com/zeux/meshoptimizer", ["Tools/vendor/gltfpack/LICENSE.md"])
native("FBX2glTF", "Facebook / Meta and V-Sekai contributors", "BSD-3-Clause", "https://github.com/godotengine/FBX2glTF", ["Tools/vendor/fbx2gltf/FBX2glTF-License.txt"], "0.13.1")
native("Autodesk FBX SDK", "Autodesk, Inc.", "Autodesk FBX SDK license", "https://www.autodesk.com/developer-network/platform-technologies/fbx-sdk-2020-0", ["Tools/vendor/fbx2gltf/FBX-SDK-License.rtf"])
native("LLGI", "Altseed contributors", "Zlib", "https://github.com/altseed/LLGI", ["../third_party/Effekseer/Dev/Cpp/3rdParty/LLGI/LICENSE"])
native("glslang", "Khronos Group and contributors", "BSD and bundled licenses", "https://github.com/KhronosGroup/glslang", ["../third_party/Effekseer/Dev/Cpp/3rdParty/glslang/LICENSE.txt"])
native("Vulkan Headers", "The Khronos Group Inc.", "Apache-2.0 OR MIT", "https://github.com/KhronosGroup/Vulkan-Headers", ["../third_party/Vulkan-Headers/LICENSE.md", "../third_party/Vulkan-Headers/LICENSES/Apache-2.0.txt", "../third_party/Vulkan-Headers/LICENSES/MIT.txt"])
# Bevy's embedded default font is a non-Cargo asset. Read its own SFNT attribution.
import struct
text_package = next(p for p in packages.values() if p["name"] == "bevy_text" and p["id"] in seen)
font = (Path(text_package["manifest_path"]).parent / "src/FiraMono-subset.ttf").read_bytes()
for i in range(struct.unpack_from(">H", font, 4)[0]):
    tag, _, offset, _ = struct.unpack_from(">4sIII", font, 12 + 16*i)
    if tag != b"name": continue
    _, count, strings = struct.unpack_from(">HHH", font, offset)
    names = {}
    for j in range(count):
        platform, _, _, nid, size, start = struct.unpack_from(">HHHHHH", font, offset+6+12*j)
        value = font[offset+strings+start:offset+strings+start+size].decode("utf-16-be" if platform in [0,3] else "latin1", errors="replace")
        names[nid] = value
    entries.append(dict(name="Fira Mono", version="", authors=names.get(0,"Mozilla and contributors"), license="SIL Open Font License 1.1", url="https://github.com/mozilla/Fira", group="Native tools & adapted code", notices=names.get(13,"") + "\n" + names.get(14,"")))
# Credit library projects once, retaining every package's attribution in the notices.
projects = {}
standalone = []
for entry in entries:
    if entry["group"] != "Rust libraries":
        standalone.append(entry)
        continue
    key = entry["url"].rstrip("/").removesuffix(".git").lower()
    projects.setdefault(key, []).append(entry)
entries = standalone
for url, members in projects.items():
    if len(members) == 1:
        entries.extend(members)
        continue
    base = url.rsplit("/", 1)[-1]
    main = next((m for m in members if m["name"].replace("_", "-") == base),
                min(members, key=lambda m: (len(m["name"]), m["name"])))
    combined = dict(main)
    if url == "https://github.com/bevyengine/bevy":
        combined["name"] = "Bevy"
    combined["version"] = main["version"]
    combined["authors"] = "Bevy contributors" if combined["name"] == "Bevy" else main["authors"]
    licenses = sorted({m["license"] for m in members})
    combined["license"] = licenses[0] if len(licenses) == 1 else "Multiple licenses — see notices"
    details = []
    texts = set()
    for m in members:
        details.append(f'{m["name"]} {m["version"]} — {m["authors"]} — {m["license"]}')
    for m in members:
        if m["notices"] and m["notices"] not in texts:
            details.append(m["notices"])
            texts.add(m["notices"])
    combined["notices"] = "\n\n".join(details)
    entries.append(combined)
entries.sort(key=lambda e: (e["group"] == "Rust libraries", e["name"].lower(), e["version"]))
out = ROOT / "IDE/app/assets/credits.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {len(entries)} third-party credits")
