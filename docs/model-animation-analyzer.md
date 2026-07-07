# 3D Model Animation Analyzer

The 3D Model Animation Analyzer is an editor tool for inspecting imported 3D models, previewing their bundled animations, and documenting frame ranges inside long animation clips.

Open it from:

```text
Tools > 3D Model Analyzer
```

Use this tool when a model contains several motions inside one long animation, such as a single `Take 001` clip that includes idle, walk, run, attack, and death segments. The analyzer lets you find the exact frame ranges, name them, save them into the model resource JSON, and later use those names in SceneMax animation commands.

## Model Selection

The `Model` combo box lists 3D model resources from the current project's `resources/Models/models-ext.json` index.

The field is editable:

- type part of a model name to filter the dropdown
- select a matching model from the filtered list to load it
- partial text that does not match a known model is treated only as filter text and does not load a model

Use `Refresh` after importing or editing model resources outside the analyzer.

## Preview Controls

The preview panel shows the selected model in a live 3D viewport.

Use the transform controls to inspect the model:

- `Move`
- `Rotate`
- `Scale`
- `Reset Transform`
- `Reset View`

These preview transforms are for inspection. The animation frame range table is the part saved by the analyzer.

## Animation Playback

The `Animation` combo box lists the animation clips available on the selected model.

Playback controls:

- `Play Full` plays the selected animation using the current start/end values as ordinary full-animation preview inputs.
- `Play Range` plays only the current start/end frame range.
- `Pause` pauses the current running preview and keeps the current frame visible.
- `Resume` continues a paused preview.
- `Stop` stops the current preview.

The `Speed` slider controls animation preview speed. The analyzer also shows:

- animation progress percentage
- current frame number
- known frame range for the selected animation

## Range Slider

The frame range slider has separate controls for:

- start frame
- end frame
- current playback cursor

Drag the start and end handles to choose a frame range. After a short debounce, the analyzer automatically plays the new range. Drag the cursor handle to seek within the selected range.

The `Start` and `End` spinners stay synchronized with the slider. Changing either spinner updates the slider, updates the selected table row, and updates the running range when a range preview is already playing.

## Frame Range Table

The table on the left documents named animation frame ranges for the selected model.

Columns:

- `Name`: the range name used later in SceneMax scripts
- `Start`: first frame in the range
- `End`: last frame in the range

Select a row to apply its start/end values to the slider and spinners. Selecting a row also automatically plays that range.

Double-click a cell to edit it. Single-clicking a row selects and previews the row.

Use:

- `Add` to create a row from the current animation name and current start/end values
- `Delete` to remove the selected row from the table draft
- `Save` to write the current table to the selected model JSON

The table is not auto-saved. Edits, Add, Delete, row selection, slider changes, spinner changes, model changes, and closing the analyzer do not write the file. Press `Save` when the table is ready.

## Saved JSON

Saved ranges are written into the selected model entry in:

```text
resources/Models/models-ext.json
```

The analyzer writes an `animationFrameRanges` array:

```json
{
  "name": "horse1",
  "path": "Models/horse/scene.gltf",
  "animationFrameRanges": [
    {
      "name": "Walk",
      "start": 720,
      "end": 751
    },
    {
      "name": "FastWalk",
      "start": 828,
      "end": 858
    }
  ]
}
```

Names are matched case-insensitively by the SceneMax parser.

## Using Named Ranges In Scripts

After saving a range, use it in animation commands with a quoted name inside the frame range brackets:

```scenemax
horse => horse1
horse."Take 001"["Idle1"]
horse."Take 001"["Walk"]
horse."Take 001"["Gallop"]
```

The original animation clip is still `Take 001`. SceneMax just plays the saved frame range inside that clip. This makes one large bundled animation usable as many named runtime clips without modifying or splitting the model file.

You can also play an arbitrary numeric frame range directly:

```scenemax
horse."Take 001"[55 - 85]
```

Or use percentages. This example plays the first half of the animation:

```scenemax
horse."Take 001"[0% - 50%]
```

Named ranges are equivalent to writing their numeric frame ranges:

```scenemax
horse."Take 001"[720-751] loop
horse."Take 001"[828-858] at speed of 1.5 loop
```

Named ranges are resolved at parse time. Runtime animation playback receives only numeric frame ranges, so there is no runtime JSON lookup cost.

If a named range is missing, parsing reports an error similar to:

```text
animation frame range 'Walk' was not found for 'horse' on model 'horse1'
```

## Recommended Workflow

1. Open `Tools > 3D Model Analyzer`.
2. Type in the `Model` box to filter and select the model.
3. Choose the long bundled animation, such as `Take 001`.
4. Use the range slider, cursor, speed slider, pause/resume, and current frame display to identify a motion segment.
5. Press `Add` or edit an existing row.
6. Adjust the slider or spinners until the selected row has the exact `Start` and `End`.
7. Press `Save`.
8. Use the saved name in SceneMax scripts with `model.animation["RangeName"]`.
