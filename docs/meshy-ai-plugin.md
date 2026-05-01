# Meshy AI Plugin

The Meshy AI plugin is an enhanced SceneMax3D IDE plugin for creating, browsing, previewing, and importing 3D models from Meshy AI.

It is also the main reference implementation for the enhanced plugin system. Read this page together with [`plugin-system.md`](plugin-system.md).

Source files:

- Entry point: [`MeshySceneMax3DPlugin.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshySceneMax3DPlugin.java)
- UI and workflow: [`MeshyViewPanel.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshyViewPanel.java)
- Meshy HTTP calls: [`MeshyService.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshyService.java)
- My task adapter: [`MeshyTaskItem.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshyTaskItem.java)
- Community result adapter: [`MeshyCommunityModelItem.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshyCommunityModelItem.java)

## What It Adds

The plugin adds a Meshy AI action to the IDE toolbar and `Assets` menu. Opening it shows a Swing view with:

- Meshy API key storage.
- Text-to-3D model generation.
- Automatic preview-to-refine flow for textured models.
- Existing Meshy task search.
- Meshy community model search.
- Community thumbnails.
- Best-effort rig/animation filters.
- Static environment model option.
- Preview-and-import into the model import designer.
- Direct import into the active project.
- Safe, unique SceneMax model names.

## Plugin Registration

The plugin entry point extends `PluginBase`:

```java
public class MeshySceneMax3DPlugin extends PluginBase {
    static final String VIEW_ID = "meshy.ai.models";

    @Override
    public SceneMaxPluginManifest getManifest() {
        return new SceneMaxPluginManifest(
                "meshy.ai",
                "Meshy AI",
                "1.0.0",
                "Create, browse, refine, download and import Meshy AI 3D models.",
                Arrays.asList("menu", "toolbar", "view", "asset-provider", "settings", "asset-import"));
    }
}
```

In `start()`, it registers:

- A `SceneMaxPluginView` with id `meshy.ai.models`.
- A `SceneMaxAssetProvider`.
- A `SceneMaxPluginAction` that opens the view.
- The action in both the `Assets` menu and toolbar.

The plugin is enabled in [`plugins/index.json`](../plugins/index.json):

```json
{
  "name": "meshy_ai",
  "fileName": "scenemax3d-internal-plugins-ide.jar",
  "className": "com.scenemaxeng.plugins.ide.meshy.MeshySceneMax3DPlugin",
  "active": true,
  "enhanced": true,
  "desc": "Adds Meshy AI model generation, task search, download and SceneMax asset import"
}
```

## User Workflow

### 1. Open The Plugin

Open `Meshy AI` from the toolbar or `Assets` menu. The plugin view opens as an IDE tab.

### 2. Set API Key

Enter a Meshy API key. The plugin stores it with:

```java
context.setSetting("meshy_api_key", apiKey);
```

and reloads it with:

```java
context.getSetting("meshy_api_key", "");
```

### 3. Generate A Model

The text-to-3D form collects:

- Prompt.
- Model type: `standard` or `lowpoly`.
- AI model.
- Remesh flag.
- Target polycount.
- Static environment model flag.

Generation starts by creating a Meshy preview task:

```java
MeshyService.createPreview(...)
```

When the preview succeeds, the plugin automatically creates a refine task:

```java
MeshyService.createRefine(...)
```

The refine task is the textured result intended for import.

### 4. Search My Tasks

The `Search Tasks` button calls:

```java
MeshyService.listTasks(key, 1, 50)
```

The task list shows status, progress, type, prompt, and task id. The plugin polls active tasks and updates progress.

Only succeeded textured GLB outputs are enabled for preview/import. Geometry-only preview tasks prompt the user to refine first.

### 5. Search Community Models

The `Community` tab calls Meshy's public community showcase endpoint through:

```java
MeshyService.searchCommunityModels(query, sortValue, page, pageSize)
```

The UI shows:

- Thumbnail.
- Title.
- Author.
- Views.
- Downloads.
- License.
- Result id.
- `Animated` or `Rig/animation` badge when detected.

Community search supports sort options such as popular, most downloaded, most reacted, newest, and recently updated.

### 6. Filter Rigged Or Animated Models

The plugin exposes two motion filters:

- `Rig or animation`
- `Animation only`

Meshy public community search does not expose a strict documented rigged-only parameter. The plugin therefore does best-effort client-side filtering based on community metadata:

- `animationId`
- `phase`
- `mode`
- `type`
- `args.animate`
- tags and prompt words such as rig, animation, humanoid, character

This logic lives in [`MeshyCommunityModelItem.java`](../scenemax3d_plugins_ide/src/com/scenemaxeng/plugins/ide/meshy/MeshyCommunityModelItem.java).

### 7. Mark Static Environment Models

The `Static environment model` checkbox is intended for buildings, walls, ground, props, and other non-moving geometry.

When checked, the plugin writes:

```json
{
  "isStatic": true
}
```

into import metadata. The IDE passes this into the model import designer, which sets the imported resource's `isStatic` flag. Static models load faster in preview because they do not need the same runtime behavior as animated/dynamic models.

### 8. Preview And Import

`Preview And Import` downloads the selected GLB or ZIP into a temporary folder and calls:

```java
context.previewModelAsset(glbFile, requestedName, metadata);
```

The IDE opens the model import designer. There, users can:

- Inspect the model before accepting.
- Adjust transform values.
- Set static/dynamic import behavior.
- Preview bundled animations from a combo box.
- Accept or cancel the import.

The import designer uses parser-safe unique names. For example:

```text
meshy_horse_girl_t-pose
```

becomes:

```text
meshy_horse_girl_t_pose
```

If the model already exists, the candidate name becomes:

```text
meshy_horse_girl_t_pose_1
meshy_horse_girl_t_pose_2
```

### 9. Import Directly

`Import Directly` downloads the file and calls:

```java
context.importModelAsset(glbFile, requestedName, metadata);
```

The host copies the model into the active project's `resources/Models` folder, normalizes it, writes `models-ext.json`, and refreshes the project asset menus.

## Meshy API Integration

The Meshy plugin currently uses two categories of Meshy endpoints.

### OpenAPI Text-To-3D

The plugin uses Meshy's text-to-3D v2 API for user-owned tasks:

```text
POST https://api.meshy.ai/openapi/v2/text-to-3d
GET  https://api.meshy.ai/openapi/v2/text-to-3d/{taskId}
GET  https://api.meshy.ai/openapi/v2/text-to-3d?page_num=1&page_size=50&sort_by=-created_at
```

Preview request body shape:

```json
{
  "mode": "preview",
  "prompt": "a stylized knight",
  "model_type": "standard",
  "ai_model": "latest",
  "target_formats": ["glb"],
  "should_remesh": true,
  "target_polycount": 30000
}
```

Refine request body shape:

```json
{
  "mode": "refine",
  "preview_task_id": "TASK_ID",
  "target_formats": ["glb"]
}
```

The refine task is used because Meshy preview tasks can be geometry-only, while refine tasks produce textured models suitable for SceneMax import.

### Meshy Community Search And Download

Community search uses the Meshy web community showcase endpoint:

```text
GET https://api.meshy.ai/web/public/v2/showcases
```

with query parameters:

```text
pageNum
pageSize
sortBy
search
```

Community download uses Meshy's web task asset endpoint:

```text
GET https://api.meshy.ai/web/v2/tasks/{resultId}/asset-url?type=Showcase&format=glb
```

For community models with animation metadata, the plugin first tries Meshy's animation packaging parameters:

```text
includeAnimation=true
action=all
includeRiggedCharacter=false
withoutSkin=false
framesPerSecond=30
singleFile=true
```

Some community models expose animation hints but do not accept animation packaging. If Meshy returns `Invalid action`, the plugin falls back to the ordinary community GLB download instead of failing the whole import.

## Import Metadata

Task imports and community imports include metadata for traceability:

```json
{
  "provider": "meshy.ai",
  "source": "community",
  "showcaseId": "SHOWCASE_ID",
  "resultId": "RESULT_ID",
  "animationId": "ANIMATION_ID",
  "downloadTaskId": "RESULT_ID",
  "downloadExtension": ".glb",
  "hasAnimation": true,
  "hasRigHint": true,
  "title": "Model title",
  "author": "Creator",
  "license": "License",
  "pageUrl": "https://www.meshy.ai/3d-models/...",
  "isStatic": false
}
```

Direct imports store this under `pluginMetadata` in `models-ext.json`.

## Bundled Animation Preview

Animated GLB/GLTF models can include bundled animation clips. The model import designer inspects imported models and shows available clips in an animation combo box.

When the user selects a clip, the preview panel runs a SceneMax partial command like:

```scenemax
model_1."walk"
```

This activates the selected animation on the preview model.

The import designer code lives in [`Import3DModelPanel.java`](../scenemax_designer/src/com/scenemax/designer/Import3DModelPanel.java).

## Developer Notes

### Network Work

The plugin runs Meshy API calls and downloads in `SwingWorker` tasks. Keep this pattern for any new long-running action:

- Set the UI busy state.
- Run HTTP/file work off the Event Dispatch Thread.
- Publish progress into Swing components.
- Reset busy/progress state in `done()`.
- Surface useful error messages with root causes.

### Downloaded File Types

Meshy can return direct `.glb` URLs or packaged `.zip` URLs, especially when animation packaging is requested. `MeshyService.CommunityDownloadAsset` carries both the download URL and file extension so the temporary file is named correctly before import.

### Safe Names

Meshy prompts and titles may contain hyphens, spaces, punctuation, or Unicode characters. Suggested asset names are sanitized to lowercase ASCII identifiers:

```java
String sanitized = base.trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9_]+", "_");
```

The host and import designer also sanitize defensively.

### Static Models

Always pass `isStatic` through metadata when importing or previewing:

```java
metadata.put("isStatic", staticModelCheck.isSelected());
```

This makes external providers useful for both environment assets and animated/dynamic character assets.

### Motion Filters

The motion filters are intentionally labeled as hints. Meshy community data can identify many animated models, but there is no guaranteed strict rigged-only filter in the current public community search response. Keep the UI wording conservative unless Meshy adds an explicit supported parameter.

## Troubleshooting

### Model opens with no textures

Use the refine task, not the preview task. The Meshy plugin automatically creates a refine task after preview succeeds, and only enables import for textured outputs.

### `Invalid action` while downloading animated community models

Meshy can mark a showcase as animated while rejecting animation packaging for that asset. The plugin now falls back to normal GLB download in that case.

### SceneMax parser error from model name

Use parser-safe model names. The plugin and host replace unsupported characters with underscores and make names unique. Example:

```text
meshy_horse_girl_t-pose -> meshy_horse_girl_t_pose
```

### Slow preview for large static assets

Enable `Static environment model` before preview/import. This is intended for buildings, terrain pieces, walls, and other non-moving models.

### Community results have no strict rig-only search

Use the `Rig or animation` or `Animation only` filter, and combine it with search terms such as `humanoid`, `character`, `walk`, `fight`, `animated`, or `rigged`.

## Build

Build the plugin jar:

```powershell
.\gradlew.bat :scenemax3d_plugins_ide:build --no-daemon
```

Build the plugin plus desktop compile targets:

```powershell
.\gradlew.bat :scenemax3d_plugins_ide:build compileJava --no-daemon
```

Restart or reload the IDE after rebuilding so it picks up the copied plugin jar.
