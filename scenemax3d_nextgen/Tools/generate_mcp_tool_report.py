"""Generate the native MCP tool inventory from the actual discovery schemas."""
import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
tools = json.loads((root / "IDE/core/src/automation/tools.json").read_text(encoding="utf-8"))
lines = ["# Bevy IDE MCP tool report", "", f"The server advertises **{len(tools)} tools**. All accept optional `project_root` to guard the active project. Document operations also expose the path/base and revision/save controls listed below. Read the live `tools/list` schemas before calling a tool.", "", "Connection, examples, concurrency guarantees, native differences and limitations: [MCP guide](MCP.md).", "", "Common parameters: `path` selects a project file; `base` is workspace/project/scripts/resources; `expected_revision` protects buffer edits; `save` controls persistence after a document mutation (default true). These controls affect the relevant document operations, not read-only queries or explicit Save commands.", "", "| Tool | Purpose | Specific parameters (`*` required) |", "| --- | --- | --- |"]
common = {"path", "base", "expected_revision", "save", "project_root"}
for tool in sorted(tools, key=lambda tool: tool["name"]):
    schema = tool["inputSchema"]
    required = schema.get("required", [])
    params = [f"`{name}`{'*' if name in required else ''}" for name in schema["properties"] if name not in common or name in required]
    lines.append(f"| `{tool['name']}` | {tool['description']} | {', '.join(params) or '—'} |")
lines += ["", "Java-name coverage: 61 of 63 names, adapted to native formats and documented schemas. Sketchfab search/import remains outside the native migration scope. Native additions: `asset.list_models`, `editor.check`, `editor.list_documents`, `editor.redo`, `editor.save_all`, `editor.undo`, `ide.get_status`, `run.stop`.", ""]
content = "\n".join(lines)
path = root / "IDE/MCP_TOOLS.md"
if "--check" in sys.argv:
    if not path.exists() or path.read_text(encoding="utf-8") != content:
        raise SystemExit("MCP tool report is stale; run Tools/generate_mcp_tool_report.py")
else:
    path.write_text(content, encoding="utf-8")
print(f"MCP tool report verified: {len(tools)} tools.")
