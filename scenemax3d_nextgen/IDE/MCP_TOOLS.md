# Bevy IDE MCP tool report

The server advertises **69 tools**. All accept optional `project_root` to guard the active project. Document operations also expose the path/base and revision/save controls listed below. Read the live `tools/list` schemas before calling a tool.

Connection, examples, concurrency guarantees, native differences and limitations: [MCP guide](MCP.md).

Common parameters: `path` selects a project file; `base` is workspace/project/scripts/resources; `expected_revision` protects buffer edits; `save` controls persistence after a document mutation (default true). These controls affect the relevant document operations, not read-only queries or explicit Save commands.

| Tool | Purpose | Specific parameters (`*` required) |
| --- | --- | --- |
| `app.restart` | Restart IDE using normal unsaved-document guards. | — |
| `asset.list_models` | List current project resource metadata. | — |
| `designer.add_entities_batch` | Atomically add up to 100 native entity objects. | `entities`* |
| `designer.add_primitive` | Add a scene entity using native templates. Rotation is quaternion XYZW. | `primitive`*, `name`, `properties` |
| `designer.add_primitive_advanced` | Add a scene entity using native templates. Rotation is quaternion XYZW. | `primitive`*, `name`, `properties` |
| `designer.adjust_view` | Set live orbit camera target, distance and angles. | `cameraTarget`, `cameraDistance`, `cameraYawDegrees`, `cameraPitchDegrees` |
| `designer.apply_material` | Assign a native material to an entity. | `entity_id`, `entity_name`, `material`* |
| `designer.array_entities` | Create translated copies with fresh identities. | `entity_id`, `entity_name`, `count`, `step` |
| `designer.capture_canvas` | Capture the live scene render target as an MCP PNG image. Includes editor grid/gizmos. | — |
| `designer.capture_canvas_reliable` | Capture the live scene render target as an MCP PNG image. Includes editor grid/gizmos. | — |
| `designer.capture_thumbnail` | Capture the live scene render target as an MCP PNG image. Includes editor grid/gizmos. | — |
| `designer.compare_to_reference` | Compare two local project images with normalized grayscale MSE; returns dimensions and similarity. Does not generate or download files. | `reference_image_path`*, `snapshot_path`* |
| `designer.create_cinematic_rig` | Add a native cinematic rig and track. | `name`, `targetEntityId`, `targetEntityName` |
| `designer.create_document` | Create a native scene (.smdesign) or UI (.smui) document. | `directoryPath`*, `fileName`*, `kind`* |
| `designer.delete_entity` | Remove a scene entity subtree. | `entity_id`, `entity_name` |
| `designer.duplicate_entity` | Create translated copies with fresh identities. | `entity_id`, `entity_name`, `count`, `step` |
| `designer.generate_code` | Save via native IDE pipeline, including generated companions/export when applicable. | — |
| `designer.get_document_json` | Inspect or validate the current JSON document, including unsaved edits. | — |
| `designer.get_entity` | Read one scene entity. | `entity_id`, `entity_name` |
| `designer.get_view_state` | Read live scene camera and selection. | — |
| `designer.group_entities` | Group top-level entities or ungroup a section. | `entity_id`, `entity_name`, `entity_ids`, `action`*, `group_name` |
| `designer.list_available_materials` | List current project resource metadata. | — |
| `designer.list_entities` | Inspect or validate the current JSON document, including unsaved edits. | — |
| `designer.mirror_entities` | Mirror one entity position/scale about a world axis (copy or in place). | `entity_id`, `entity_name`, `axis`*, `pivot`, `mode` |
| `designer.overlay_reference_image` | Overlay a local project image over the scene viewport. Preview-only; not saved or exported. | `image_path`, `visible`, `opacity` |
| `designer.refresh_project_tree` | Refresh project inventory through the IDE worker. | — |
| `designer.select_entity` | Select an entity in the live designer. | `entity_id`, `entity_name` |
| `designer.set_document_json` | Replace a scene/UI JSON document while preserving undo history. | `document`* |
| `designer.update_entity` | Patch scene entity properties; identity and hierarchy are preserved. | `entity_id`, `entity_name`, `updates`* |
| `designer.use_view_gizmo` | Select a standard live camera orientation. | `preset`* |
| `designer.validate_document` | Inspect or validate the current JSON document, including unsaved edits. | — |
| `designer.validate_resources` | Resolve scene resources and return per-entity diagnostics. | — |
| `editor.check` | Validate the active script or native scene/UI/material document without running it. Includes the checked snapshot revision. | — |
| `editor.get_active_document` | Read the active buffer. | — |
| `editor.list_documents` | List open documents and revisions. | — |
| `editor.open_file` | Open a project file in its native editor. | `path`* |
| `editor.redo` | Redo one active-document edit. | — |
| `editor.reload_from_disk` | Reload a clean file. Dirty buffers are refused. | — |
| `editor.save_active` | Save active document and generated companions. | — |
| `editor.save_all` | Save all open documents. | — |
| `editor.undo` | Undo one active-document edit. | — |
| `file.create` | Create a new UTF-8 file without overwriting; optionally create missing parent directories. | `path`*, `content`*, `createParents` |
| `file.modify` | Replace all text or an exact expected text match; edits are undoable. | `newContent`, `expectedText`, `replacementText`, `replaceAll` |
| `file.read` | Read current editor text or load a project file. Returns revision and dirty state. | `path`* |
| `ide.get_recent_errors` | Read bounded IDE status, runtime output and MCP request diagnostics. | `limit` |
| `ide.get_status` | Read bounded IDE status, runtime output and MCP request diagnostics. | `limit` |
| `material.save_export` | Save via native IDE pipeline, including generated companions/export when applicable. | — |
| `project.get_context` | Active project, open documents, revisions, running state and MCP connection. | — |
| `project.list_tree` | List project inventory with bounded results and offset pagination. | `query`, `root`, `maxResults`, `maxEntries`, `offset` |
| `project.refresh_tree` | Refresh project inventory through the IDE worker. | — |
| `project.search_files` | List project inventory with bounded results and offset pagination. | `query`, `root`, `maxResults`, `maxEntries`, `offset` |
| `project.search_text` | Search project text, preferring unsaved editor buffers. | `query`*, `maxResults` |
| `run.preview_scene` | Save and run the active script or designer generated code. | — |
| `run.stop` | Stop the owned projector. | — |
| `runtime.get_issue_log` | Read bounded IDE status, runtime output and MCP request diagnostics. | `limit` |
| `scene.create` | Create a scene directory, designer and companion scripts. | `parentPath`*, `sceneName`* |
| `ui.add_layer` | Add a UI layer. | `name`*, `visible`, `zOrder` |
| `ui.add_widget` | Add a native widget to a layer or PANEL selected by JSON pointer. | `parent`, `type`*, `name`, `properties` |
| `ui.delete_layer` | Delete a layer and its widgets. | `layerName`, `layerId` |
| `ui.delete_widget` | Find a widget by JSON pointer, name or ID. | `widgetPath`, `widgetId` |
| `ui.get_schema` | Get native UI authoring schema and templates. | — |
| `ui.get_widget` | Find a widget by JSON pointer, name or ID. | `widgetPath`, `widgetId` |
| `ui.list_fonts` | List current project resource metadata. | — |
| `ui.list_layers` | Inspect or validate the current JSON document, including unsaved edits. | — |
| `ui.list_sprites` | List current project resource metadata. | — |
| `ui.list_widgets` | Inspect or validate the current JSON document, including unsaved edits. | — |
| `ui.update_layer` | Patch a layer by name, ID or JSON pointer. | `layerName`, `layerId`, `updates`* |
| `ui.update_widget` | Patch a widget, including constraints and appearance. | `widgetPath`, `widgetId`, `updates`* |
| `ui.validate_document` | Inspect or validate the current JSON document, including unsaved edits. | — |

Java-name coverage: 61 of 63 names, adapted to native formats and documented schemas. Sketchfab search/import remains outside the native migration scope. Native additions: `asset.list_models`, `editor.check`, `editor.list_documents`, `editor.redo`, `editor.save_all`, `editor.undo`, `ide.get_status`, `run.stop`.
