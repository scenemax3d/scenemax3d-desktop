"""Remove duplicate float32 key times and their paired samples from a glTF asset.

Keeps the last sample at each timestamp. Existing buffer allocations are retained,
so mesh, skin and texture bytes and all unrelated offsets remain unchanged.
"""
import argparse
import json
import struct
from pathlib import Path


def repair(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    buffers = [bytearray((path.parent / b["uri"]).read_bytes()) for b in data["buffers"]]
    edits = {}
    removed = 0
    for animation in data.get("animations", []):
        for sampler in animation["samplers"]:
            assert sampler.get("interpolation", "LINEAR") in ("LINEAR", "STEP")
            accessor = data["accessors"][sampler["input"]]
            view = data["bufferViews"][accessor["bufferView"]]
            assert accessor["type"] == "SCALAR" and accessor["componentType"] == 5126
            offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
            times = struct.unpack_from("<" + "f" * accessor["count"], buffers[view["buffer"]], offset)
            assert all(a <= b for a, b in zip(times, times[1:])), "Decreasing key times"
            keep = [i for i, t in enumerate(times) if i == len(times) - 1 or t < times[i + 1]]
            if len(keep) == len(times):
                continue
            removed += len(times) - len(keep)
            for index in (sampler["input"], sampler["output"]):
                if index in edits:
                    assert edits[index] == keep
                edits[index] = keep
    for index, keep in edits.items():
        accessor = data["accessors"][index]
        assert accessor["componentType"] == 5126 and "sparse" not in accessor
        width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[accessor["type"]]
        view = data["bufferViews"][accessor["bufferView"]]
        assert view.get("byteStride", width * 4) == width * 4
        offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        buffer = buffers[view["buffer"]]
        samples = [bytes(buffer[offset + i * width * 4:offset + (i + 1) * width * 4]) for i in keep]
        packed = b"".join(samples)
        buffer[offset:offset + len(packed)] = packed
        accessor["count"] = len(keep)
        values = [struct.unpack("<" + "f" * width, sample) for sample in samples]
        for key, fn in (("min", min), ("max", max)):
            if key in accessor:
                accessor[key] = [fn(v[i] for v in values) for i in range(width)]
    if edits:
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        for meta, buffer in zip(data["buffers"], buffers):
            (path.parent / meta["uri"]).write_bytes(buffer)
    print(f"Removed {removed} duplicate sampler timestamps from {path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    repair(parser.parse_args().path)
