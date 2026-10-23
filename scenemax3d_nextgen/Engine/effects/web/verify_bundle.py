"""Verify the vendored distribution, or restore its four runtime files from the pinned ZIP.

python Engine/effects/web/verify_bundle.py [--restore-archive PATH]
License notices remain checked-in and are verified too. No SDK or network needed.
"""
import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile

ARCHIVE_SHA256 = "c08428bffb5153317835995be2f83ee276f2d6003c7343675ca3b3967a82ee77"
RUNTIME = ("effekseer.js", "effekseer-webgpu.js", "effekseer-webgpu.wasm", "effekseer.d.ts")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restore-archive", type=Path)
    args = parser.parse_args()
    vendor = Path(__file__).resolve().parent / "vendor"
    hashes = dict(line.split("  ", 1)[::-1] for line in (vendor / "SHA256SUMS").read_text().splitlines())
    if args.restore_archive:
        if hashlib.sha256(args.restore_archive.read_bytes()).hexdigest() != ARCHIVE_SHA256:
            raise SystemExit("Archive checksum mismatch; no files changed")
        with ZipFile(args.restore_archive) as archive:
            files = {name: archive.read(name) for name in RUNTIME}
        for name, data in files.items():
            if hashlib.sha256(data).hexdigest() != hashes[name]:
                raise SystemExit(f"Runtime checksum mismatch: {name}; no files changed")
        for name, data in files.items():
            (vendor / name).write_bytes(data)
    for name, expected in hashes.items():
        if hashlib.sha256((vendor / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Bundled checksum mismatch: {name}")
    print(f"Verified {len(hashes)} pinned runtime and notice files")


if __name__ == "__main__":
    main()
