# SceneMax MCP Tool Reference

This document describes the built-in SceneMax MCP tools, what they are for, how to use them, and what they return.

Use it together with [Built-in MCP Server and AI Assistants](built-in-mcp-server.md), which explains how to connect Claude Code, Codex, Claude Desktop, and Local Gemma to the running SceneMax IDE.

## How To Read This Reference

There are two sources of truth for SceneMax MCP tools:

- This document, which explains the tools in human terms and shows common usage patterns.
- The live MCP server, which exposes each tool's current `name`, `description`, `inputSchema`, and `outputSchema` through `tools/list`.

If you are writing an agent or debugging a tool call, prefer the live `tools/list` response for the exact schema currently served by the IDE.

## Tool Discovery

The SceneMax MCP server supports the standard MCP `tools/list` method. Each listed tool includes:

- `name`
- `description`
- `inputSchema`
- `outputSchema`

Typical JSON-RPC request:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

Typical response shape:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "designer.capture_canvas_reliable",
        "description": "Captures the active scene/UI designer canvas using the renderer-backed snapshot path for reliable visual verification, defaulting to a clean scene snapshot.",
        "inputSchema": {
          "type": "object"
        },
        "outputSchema": {
          "type": "object"
        }
      }
    ]
  }
}
```

## Shared Conventions

Many SceneMax MCP tools follow the same conventions.

### Path bases

Several tools accept a `base` argument to resolve relative paths. Supported values are:

- `workspace`
- `project`
- `scripts`
- `resources`

If a path is already absolute, it is used as-is.

### Designer tools operate on the active tab

Most `designer.*` tools act on the currently active designer tab in the IDE. For example:

- `designer.list_entities` reads the active scene designer tab
- `designer.capture_canvas_reliable` captures the active scene or UI designer tab
- `designer.adjust_view` mutates the current designer viewport

If no matching tab is open, the tool usually fails with an error such as `Open a scene designer tab first.`

### Structured results

SceneMax MCP tool calls return human-readable content plus structured JSON content. Agents should prefer the structured content when they need to chain operations.

### Attached-image inputs

Some visual tools accept either:

- a normal string path
- or a file-like object from the client layer

The following object fields are recognized when present:

- `path`
- `local_path`
- `download_url`
- `url`
- `name`
- `filename`
- `content_type`
- `mime_type`

If the image is outside the workspace, SceneMax stages it into `workspace/tmp/mcp-inputs`.

### Sequential UI operations are safer

Designer camera, selection, overlay, and capture operations should be treated as sequential UI steps. In practice this means agents should avoid issuing parallel calls like:

- `designer.adjust_view`
- `designer.select_entity`
- `designer.capture_canvas`
- `designer.capture_canvas_reliable`

against the same active designer tab at the same time.

## Tool Categories

The current default SceneMax MCP registry includes the following tools.

### Project and workspace tools

| Tool | Purpose |
| --- | --- |
| `project.get_context` | Return workspace, active project, and active editor context |
| `project.list_tree` | List files and folders under workspace, project, scripts, or resources |
| `project.refresh_tree` | Refresh the IDE project tree after filesystem changes |
| `project.search_files` | Search for files by name |
| `project.search_text` | Search text across files |

### File and editor tools

| Tool | Purpose |
| --- | --- |
| `file.read` | Read a text file |
| `file.create` | Create a new file |
| `file.modify` | Replace matching text or overwrite a file |
| `editor.get_active_document` | Return information about the active editor tab |
| `editor.open_file` | Open a file in the editor or matching designer |
| `editor.reload_from_disk` | Reload an open file from disk |
| `editor.save_active` | Save the active editor/designer tab |

### Scene designer document tools

| Tool | Purpose |
| --- | --- |
| `designer.create_document` | Create a new scene, UI, shader, environment shader, or material document |
| `designer.get_document_json` | Read the JSON stored in a designer document |
| `designer.set_document_json` | Replace a designer JSON document on disk |
| `designer.generate_code` | Generate the companion `.code` file for a designer document |
| `designer.validate_document` | Validate a designer document |
| `designer.validate_resources` | Report missing materials, shaders, models, and other references |
| `designer.refresh_project_tree` | Refresh the project tree from the designer side |

### Scene designer entity tools

| Tool | Purpose |
| --- | --- |
| `designer.list_entities` | List entities in the active scene designer tab |
| `designer.get_entity` | Return one entity by id or name |
| `designer.update_entity` | Update an entity by id or name |
| `designer.delete_entity` | Delete one or more entities |
| `designer.duplicate_entity` | Duplicate one or more entities |
| `designer.mirror_entities` | Mirror one or more entities across an axis |
| `designer.array_entities` | Create repeated copies with a fixed step |
| `designer.group_entities` | Group or ungroup entities into a section |
| `designer.select_entity` | Select an entity in the active scene designer |

### Primitive and material authoring tools

| Tool | Purpose |
| --- | --- |
| `designer.add_primitive` | Add a basic primitive to the active scene designer |
| `designer.add_primitive_advanced` | Create a primitive with full initial properties in a document |
| `designer.add_entities_batch` | Append multiple entities into a document |
| `designer.apply_material` | Apply a material to an entity |
| `designer.list_available_materials` | List materials available to scene designer documents |
| `material.save_export` | Export a `.mat` document into runtime-ready resources |

### Viewport, capture, and comparison tools

| Tool | Purpose |
| --- | --- |
| `designer.get_view_state` | Inspect the current scene or UI designer viewport |
| `designer.adjust_view` | Orbit, pan, zoom, fit, frame selection, or frame all |
| `designer.use_view_gizmo` | Apply a view-gizmo preset such as front/top/default |
| `designer.capture_thumbnail` | Capture the current designer thumbnail |
| `designer.capture_canvas` | Capture the visible designer canvas to PNG |
| `designer.capture_canvas_reliable` | Capture the current designer view through the reliable renderer-backed path |
| `designer.overlay_reference_image` | Show or hide a reference image overlay in the active scene designer |
| `designer.compare_to_reference` | Compare a reference image to a designer snapshot and return metrics plus hints |

### Scene authoring helpers

| Tool | Purpose |
| --- | --- |
| `designer.create_cinematic_rig` | Create a cinematic rig in the active scene designer |

### Runtime, app, and diagnostics tools

| Tool | Purpose |
| --- | --- |
| `run_preview_scene` | Generate code if needed, validate, and launch a SceneMax preview |
| `runtime.get_issue_log` | Read runtime syntax/runtime issues from the active project |
| `ide.get_recent_errors` | Return recent IDE/MCP/server errors |
| `app.restart` | Restart the SceneMax IDE |

### Asset tools

| Tool | Purpose |
| --- | --- |
| `asset.search_sketchfab` | Search Sketchfab for downloadable models using SceneMax filters |
| `asset.import_sketchfab` | Download a Sketchfab model into project resources and register it as an asset |

## Core Usage Patterns

The sections below describe the most important tools in more detail.

## Project And File Workflow

### `project.get_context`

Purpose:

- Discover the active workspace and project roots
- Discover the active editor/designer file
- Build path decisions before editing or generating files

Common use:

```json
{
  "name": "project.get_context",
  "arguments": {}
}
```

Typical output includes:

- workspace path
- active project path
- scripts/resources roots
- current editor file and tab information

### `project.search_files` and `project.search_text`

Purpose:

- Find candidate files before opening them
- Search game logic or designer docs by name or text

Common inputs:

- `base`
- `root`
- `query`
- `maxResults`

Typical outputs:

- matching file paths
- matching text snippets with file context

### `file.read`, `file.create`, and `file.modify`

Purpose:

- Read or edit project files without leaving the IDE flow

Typical input patterns:

- `file.read`: `base`, `path`
- `file.create`: `base`, `path`, `content`, `overwrite`, `openInEditor`
- `file.modify`: `base`, `path`, `expectedText`, `replacementText`, `replaceAll`, or full `newContent`

Typical outputs:

- resolved absolute path
- created/modified status
- sometimes a short preview or metadata block

## Scene Designer Workflow

### `designer.create_document`

Purpose:

- Create a new scene, UI, shader, environment-shader, or material document

Important inputs:

- `directoryPath`
- `fileName`
- `kind`
- `preset`
- `openInEditor`

Typical output:

- created file path
- kind
- whether it was opened in the editor

### `designer.list_entities`

Purpose:

- Inspect the current scene structure before editing it

Input:

- no arguments

Output:

- `entities`: array of entity summaries

Each entity summary includes:

- `id`
- `name`
- `type`
- `material`
- `shader`
- `resourcePath`
- `position`
- `rotation`
- `scale`
- optional cinematic metadata
- optional `children`

### `designer.get_entity`

Purpose:

- Fetch one entity before making a targeted update

Typical inputs:

- `entity_id`
- or `entity_name`

Typical output:

- the same summary shape used by `designer.list_entities`

### `designer.update_entity`

Purpose:

- Change an entity's transform, material, visibility, primitive-specific properties, or other serialized fields

Typical inputs:

- `path` when targeting a document on disk
- `entity_id` or `entity_name`
- `updates` object
- optional `reload`

Typical output:

- updated entity summary

### `designer.add_primitive`

Purpose:

- Add a new primitive into the active scene designer tab

Supported primitive values:

- `sphere`
- `box`
- `wedge`
- `cylinder`
- `cone`
- `hollow_cylinder`
- `quad`
- `stairs`
- `arch`

Typical inputs:

- `primitive`
- `saveDocument`

Typical output:

- summary of the newly created entity

### `designer.add_primitive_advanced`

Purpose:

- Build a primitive directly in a `.smdesign` document with position, rotation, scale, material, and primitive-specific dimensions in one call

Use this when:

- you want deterministic scene generation
- you already know the initial values
- you do not want to rely on the active selection or manual follow-up edits

Typical outputs:

- created entity summary
- resolved target document information

### `designer.apply_material`

Purpose:

- Apply a named material to an entity by id or name

Typical inputs:

- `material`
- `entityId` or `entityName`
- `path`
- `reload`

Typical output:

- updated entity or operation status

### `designer.validate_document` and `designer.validate_resources`

Purpose:

- Check whether a generated scene is structurally valid
- Catch missing materials, shaders, environment shaders, and model references before preview

Typical outputs:

- validation status
- error or warning details

### `designer.generate_code`

Purpose:

- Generate the companion `.code` file for `.smdesign` or `.smui` documents

Typical output:

- generated code path
- syntax validation result

## View, Capture, Overlay, And Comparison

These are the most important tools for design-compare-improve loops.

### `designer.get_view_state`

Purpose:

- Inspect the current viewport before or after a camera operation

Input:

- no arguments

Scene designer output includes:

- `path`
- `kind`
- `cameraMode`
- `cameraDistance`
- `cameraYawDegrees`
- `cameraPitchDegrees`
- `cameraTarget`
- `viewportWidth`
- `viewportHeight`
- selected entity metadata when available

UI designer output includes:

- `path`
- `kind`
- `zoom`
- `panX`
- `panY`
- `activeLayer`
- document canvas size
- selected widget metadata when available

### `designer.adjust_view`

Purpose:

- Move the current designer viewport without touching scene data

Scene designer actions:

- `orbit`
- `pan`
- `zoom`
- `camera_mode`
- `frame_selection`
- `frame_all`

UI designer actions:

- `pan`
- `zoom`
- `fit`

Important input fields:

- `action`
- `yaw_degrees`
- `pitch_degrees`
- `right`
- `up`
- `forward`
- `distance_delta`
- `camera_mode`
- `delta_x`
- `delta_y`
- `zoom_factor`
- `anchor_x`
- `anchor_y`
- `padding`
- `padding_scale`

Important output:

- the updated view state, using the same structure as `designer.get_view_state`

Example:

```json
{
  "name": "designer.adjust_view",
  "arguments": {
    "action": "frame_all",
    "padding_scale": 2.5
  }
}
```

### `designer.use_view_gizmo`

Purpose:

- Snap the scene designer to common viewpoints such as front, back, left, right, top, bottom, or default

Useful when:

- you want a stable orthographic-like inspection angle
- you want a repeatable framing before capture

### `designer.capture_canvas`

Purpose:

- Save the current active designer canvas to PNG

Important inputs:

- `path`
- `base`
- `width`
- `height`
- `clean`

Important output:

- `path`
- `width`
- `height`
- `clean`

Use `clean: true` in scene designers when you want to hide editor-only aids such as:

- grid
- gizmos
- selection outlines
- scene camera markers

### `designer.capture_canvas_reliable`

Purpose:

- Capture the current active designer view through the renderer-backed path rather than screen scraping

This is the preferred capture tool for:

- automated comparison loops
- deterministic design verification
- agentic visual iteration

Important inputs:

- `path`
- `base`
- `width`
- `height`
- `clean`

Important output:

- `path`
- `kind`
- `width`
- `height`
- `captureMode`
- `clean`

Example:

```json
{
  "name": "designer.capture_canvas_reliable",
  "arguments": {
    "path": "projects/AI_tests/captures/current.png",
    "base": "workspace",
    "width": 1280,
    "height": 720,
    "clean": true
  }
}
```

### `designer.overlay_reference_image`

Purpose:

- Show or hide a reference image over the active scene designer canvas

Accepted image inputs:

- `image_path`: string path
- `image`: string path or attached-file object

Important additional inputs:

- `visible`
- `opacity`
- `scale`
- `fit_mode`
- `offset_x`
- `offset_y`

Important output:

- `visible`
- `imagePath`
- `resolvedImagePath`
- `opacity`
- `scale`
- `fitMode`
- `offsetX`
- `offsetY`

Example with a local path:

```json
{
  "name": "designer.overlay_reference_image",
  "arguments": {
    "image_path": "C:/Users/adikt/Downloads/temple_test1.png",
    "visible": true,
    "opacity": 0.45,
    "fit_mode": "contain"
  }
}
```

### `designer.compare_to_reference`

Purpose:

- Compare a reference image to a designer snapshot
- auto-capture a snapshot when needed
- return metrics plus human-readable improvement suggestions

Accepted reference inputs:

- `reference_image_path`: string path
- `reference_image`: string path or attached-file object

Important additional inputs:

- `snapshot_path`
- `output_snapshot_path`
- `capture_if_missing`
- `capture_clean`
- `capture_width`
- `capture_height`

Important output:

- `referenceImagePath`
- `snapshotPath`
- `metrics`
- `referenceBox`
- `snapshotBox`
- `suggestions`

Current metrics include:

- `grayscaleMse`
- `edgeMse`
- `widthRatio`
- `heightRatio`
- `centerDeltaX`
- `centerDeltaY`
- `referenceSymmetry`
- `snapshotSymmetry`
- `overallScore`

Example with auto-capture:

```json
{
  "name": "designer.compare_to_reference",
  "arguments": {
    "reference_image_path": "C:/Users/adikt/Downloads/temple_test1.png",
    "capture_if_missing": true,
    "capture_clean": true,
    "capture_width": 1280,
    "capture_height": 720
  }
}
```

## Runtime, Preview, And Diagnostics

### `run_preview_scene`

Purpose:

- Generate companion code when needed
- validate syntax
- launch the SceneMax preview runtime

Use this after:

- generating or editing a scene
- updating code or materials
- validating that a scene can actually run

### `runtime.get_issue_log`

Purpose:

- Read runtime syntax or runtime failures collected by the active project

Useful inputs:

- `issue_type`: `all`, `syntax`, or `runtime`
- `limit`

Typical output:

- recent issue entries with messages and context

### `ide.get_recent_errors`

Purpose:

- Inspect IDE-side or MCP-side failures captured by the host

Use this when:

- a tool appears to fail without a clear reason
- the app looks frozen
- the local MCP server stopped responding

### `app.restart`

Purpose:

- Restart the SceneMax IDE

Use this when:

- a new build has been installed
- the app is stuck
- the local MCP server needs to come back cleanly

## Asset Workflow

### `asset.search_sketchfab`

Purpose:

- Search Sketchfab for downloadable models using the same filter model the SceneMax importer expects

Typical inputs:

- `query`
- `animatedOnly`
- `category`
- `license`
- `maxFaceCount`
- `sortBy`
- `staffPicked`

Typical output:

- result items with enough metadata to choose a downloadable model

### `asset.import_sketchfab`

Purpose:

- Download a Sketchfab model into the active project's `resources/Models` folder
- register it as a SceneMax model asset

Typical inputs:

- `uid`
- `apiToken`
- `modelName`
- `replaceExisting`

Typical output:

- imported resource path
- registered model name
- asset/import status

## Recommended Agent Flows

### Explore a project

1. `project.get_context`
2. `project.search_files` or `project.search_text`
3. `file.read` or `editor.open_file`
4. `editor.get_active_document`

### Build or update a scene

1. `designer.create_document`
2. `designer.add_primitive_advanced` or `designer.add_primitive`
3. `designer.list_entities`
4. `designer.update_entity`
5. `designer.apply_material`
6. `designer.validate_document`
7. `designer.generate_code`
8. `run_preview_scene`

### Design-compare-improve loop

1. `designer.overlay_reference_image`
2. `designer.adjust_view`
3. `designer.capture_canvas_reliable`
4. `designer.compare_to_reference`
5. `designer.update_entity` or other authoring tools
6. repeat

## Common Pitfalls

- Many designer tools require the right tab to be open first.
- Scene designer and UI designer tools share some names, but not every action is valid in both.
- `designer.capture_canvas_reliable` is the preferred capture tool for agentic image comparison.
- `designer.compare_to_reference` can auto-capture, so agents do not always need to call capture separately.
- Relative paths without the correct `base` often resolve to the wrong location.
- Designer view mutation and capture calls should be made sequentially rather than in parallel.

## Related Docs

- [Built-in MCP Server and AI Assistants](built-in-mcp-server.md)
- [Documentation Index](README.md)
