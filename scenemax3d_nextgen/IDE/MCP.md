# SceneMax Bevy IDE MCP automation

The native IDE contains a lightweight local Model Context Protocol server. It exposes live project state, text editing, scene and UI authoring, previews and diagnostics to AI agents. No Java, Node.js, Python, cloud account, separate web server or MCP SDK is needed by the IDE.

## Connect

Keep the IDE running. The default endpoint is **http://127.0.0.1:8765/mcp**. Click **MCP** in the bottom-right corner for the actual endpoint and recent request metadata. Health: `GET /mcp/status`.

A client with Streamable HTTP support can use:

```json
{
  "mcpServers": {
    "scenemax": {
      "type": "http",
      "url": "http://127.0.0.1:8765/mcp"
    }
  }
}
```

Configuration key names vary by agent. The endpoint and `tools/list` schema are authoritative. Supported protocol versions are `2025-03-26`, `2025-06-18`, and `2025-11-25`. Initialization negotiates one of these versions. POST responses use JSON, and notifications receive HTTP 202. Requests use HTTP/1.1 with `Content-Length`; chunked request bodies are not accepted. This is stateless Streamable HTTP: no session IDs, standalone SSE stream, server-initiated requests, subscriptions, or asynchronous MCP tasks. GET `/mcp` returns 405, as permitted when the server does not provide SSE. The protocol reference is the [official MCP transport specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports).

For agents that support only stdio, use the native **scenemax_mcp_proxy.exe** built alongside the IDE:

```json
{
  "mcpServers": {
    "scenemax": {
      "command": "C:\\dev\\scenemax_desktop\\scenemax3d_nextgen\\target\\debug\\scenemax_mcp_proxy.exe",
      "args": ["http://127.0.0.1:8765/mcp"]
    }
  }
}
```

The proxy does not launch the IDE. It forwards newline-delimited JSON-RPC, writes diagnostics only to stderr, and never retries mutations automatically. Build it with `cargo build --locked -p scenemax_ide_services --bin scenemax_mcp_proxy` from `scenemax3d_nextgen`.

## Configuration and access

Set environment variables **before starting the IDE**:

| Variable | Behavior |
| --- | --- |
| `SCENEMAX_MCP_PORT` | Fixed port, default `8765`; `0` requests an available port reported in the monitor. |
| `SCENEMAX_MCP_DISABLED=1` | Disable the listener. |
| `SCENEMAX_MCP_TOKEN` | Require `Authorization: Bearer <token>` on all HTTP endpoints. Give the same variable to the stdio proxy. The monitor and logs never display the token. |
| `SCENEMAX_MCP_URL` | Optional stdio-proxy endpoint when no positional endpoint was supplied. |

The listener binds only to `127.0.0.1`. Host headers must identify its actual local port; unrelated browser Origins are rejected. It does not grant wildcard CORS access. Without a token, local processes can automate the IDE; use a token where local applications are not equally trusted. It is not a remote multi-user service.

A port collision leaves the IDE operational and displays an unavailable status in its MCP monitor. It does not silently select a different port. When running multiple IDE instances, configure distinct ports. Restart releases the old listener before starting the replacement IDE. Bounded GPU smoke runs disable MCP unless a port is explicitly supplied.

Requests are limited to 8 MiB, headers to 16 KiB, connections to four, the pending tool queue to eight, and tools execute serially through the editor bridge. Calls have a 60-second deadline. A queued expired call cannot begin a mutation. A filesystem operation already in flight may complete after a client disconnect/timeout: inspect state before retrying. Cancellation notifications are not an undo or rollback mechanism.

## Agent workflow

1. Call `project.get_context`; confirm the intended root. Use its `project_root` in subsequent calls when working across multiple projects. Every tool accepts this optional guard.
2. Discover tools with `tools/list` and inspect `inputSchema`. Unknown arguments and wrong types are rejected rather than silently ignored.
3. Find assets with `asset.list_models`, `designer.list_available_materials`, `ui.list_sprites`, and `ui.list_fonts`. Read files with `file.read`; open documents and unsaved text are authoritative.
4. Use native authoring tools or `file.modify`. Pass the returned `expected_revision` when updating a buffer; it is mandatory when the buffer is already dirty. Revision tokens include document path, content, and editor revision. Exact replacements also require a matching `expectedText`.
5. Inspect JSON or validate with `editor.check`. Scene/UI edits preserve unknown fields and become one undo transaction. Native saves check the original disk baseline and refuse external changes.
6. `designer.generate_code` saves a `.smdesign` and generates companion SceneMax code through the normal pipeline. `run.preview_scene` uses the active script or designer's generated code; `run.stop` stops the IDE-owned projector.
7. Inspect runtime output with `runtime.get_issue_log`, inspect view state, and capture the scene with `designer.capture_canvas_reliable`. Captures return MCP PNG image content for the agent to inspect.

Paths default to the active project root. `base` can be `workspace`/`project`, `scripts`, or `resources`. Absolute paths must remain in the active project. Traversal, alternate streams and hidden metadata paths are refused; existing targets are canonicalized. File creation never overwrites an existing file. Set `createParents: true` to create missing parent directories within the project. It opens the resulting document and refreshes inventory. The IDE can return a busy error while another file operation, import or text composition is in progress; retry after that operation finishes.

Document tools accept `path` to open/select a specific file, or use the active document when omitted. Edits default to `save: true`; use `save: false` for a sequence of edits, passing each new revision, then save explicitly. Reads may open/select a requested file but do not overwrite a dirty buffer. Reload never discards unsaved edits. Save errors leave the edited buffer available for inspection/undo. Do not treat an HTTP 200 as tool success: check `result.isError`.

Example tool-call parameters:

```json
{"name":"scene.create","arguments":{"parentPath":"scripts","sceneName":"demo"}}
```
```json
{"name":"designer.add_primitive","arguments":{"primitive":"BOX","name":"stage","properties":{"position":[0,0,0],"scale":[4,0.2,4]}}}
```
```json
{"name":"designer.add_primitive_advanced","arguments":{"primitive":"MODEL","name":"performer","properties":{"resourcePath":"a_name_from_asset_list_models","position":[0,1,0],"scale":[1,1,1]}}}
```
```json
{"name":"designer.update_entity","arguments":{"entity_name":"performer","updates":{"position":[2,1,0]}}}
```
```json
{"name":"designer.generate_code","arguments":{}}
```

Scene transforms use local position/scale vectors and **quaternion XYZW rotations**, matching native designer documents. `properties` is the native entity/widget JSON object; query current documents/schema before constructing unfamiliar fields. Batch insertion accepts up to 100 entities and validates the whole resulting document before applying it.

## Supported workflows and limitations

The generated catalog describes the supported tools and their exact parameters. Use it together with live discovery instead of examples from earlier releases.

- The two Sketchfab search/import tools are not advertised. Import supported local model files through the IDE instead. Registered local glTF/GLB resources are available through `asset.list_models` and native scene entities.
- `material.save_export` saves the native `.smmat` runtime material; no extra export is necessary.
- Use `properties` for advanced primitive/widget values, `updates` for property changes, `count`/`step` for translated copies, and `expected_revision` for concurrency. `force`, `overwrite`, `discard_editor_state`, download-URL attachments and reload flags are not accepted.
- Duplicate, array, mirror and group/ungroup currently operate on top-level scene entities. Ungroup accepts sections with identity transforms so placement is preserved. Grouping retains existing IDs. Duplicating/inserting generates fresh IDs and remaps internal references.
- `ui.update_layer` uses an `updates` object. Widget selectors accept native JSON pointers or unique names (`widgetPath`) and IDs (`widgetId`). New widgets target a layer or PANEL using a JSON pointer.
- View and capture tools target the **3D scene designer**. UI authoring/validation is supported, but UI-designer screenshot/camera control is not exposed by these view tools. Captures use the current scene render-target size, capped at 1600×1200, and include editor grid/gizmos; thumbnails are capped at 320×240. They do not write a file and fail clearly while scene/model assets are not ready.
- Reference overlays use local project PNG/JPEG images and a viewport-sized preview overlay; they are never saved/exported. A scene rebuild removes the overlay. Image comparison accepts two existing project image paths, normalizes them to 256×256 grayscale and returns MSE/dimensions/similarity. It does not download attachments or automatically save/capture an image. Agents can compare returned MCP captures visually directly.
- `runtime.get_issue_log` exposes bounded captured projector output/status, not structured access to a running game's entity graph. `ide.get_recent_errors` also includes bounded MCP request metadata. No arbitrary shell, Git push, package publication or credential-management tool is exposed.

## Tool report and maintenance

See [the complete tool report](MCP_TOOLS.md) for every available tool and its parameters. `IDE/core/src/automation/tools.json` is the single discovery/schema source. Run `python Tools/generate_mcp_tool_report.py` after updating it; CI-style verification uses `--check`.

Responsibilities: core owns schemas and pure authoring transactions; services owns the bounded HTTP/stdio transport and background disk work; application owns live buffer revisions and existing save/run commands; presentation owns camera, selection, overlay, GPU capture and the retained monitor. Runtime/engine modules have no dependency on MCP.

## Historical verification record

The native server was exercised against a real GPU IDE instance with 68 tools / 79 calls and the stdio proxy. The restart tool was checked with isolated lifecycle and listening-port-reuse tests. Scene capture and the MCP monitor were visually inspected. The regression suite plus added focused checks passed 306 unique tests (three existing optional service tests remain skipped); scoped Clippy, architecture, menu and tool-report checks passed. Local evidence is under `target/mcp-audit`. These tests do not imply remote-network, every-agent-client or Sketchfab integration support.
