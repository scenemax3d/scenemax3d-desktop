# Model Animation Analyzer

Open **Tools → Model Animation Analyzer**. The project model catalog opens as a retained designer tab without expanding resource folders or changing the project navigator view. The tool edits `resources/Models/models-ext.json` directly through the normal document lifecycle: Save/Ctrl+S, Save All, Undo/Redo, recovery, dirty-close guards and external-change protection.

Choose a model (the filter narrows the model selector), then a source animation. **Play full**, **Play range**, **Pause / Resume**, **Stop**, frame scrubbing, start/end sliders and 1–200% speed operate on the embedded Bevy preview. Right-drag orbits, middle-drag pans, and the wheel zooms. Move/Rotate/Scale select the IDE's axis gizmos; drag a colored axis, ring or scale handle to change the preview transform. The preview gizmo pivots around the framed model bounds so offset source origins remain usable. Reset transform and Reset view affect only the preview.

Set a range and choose **Add range**. Edit its name, source animation, start and end in the table. Click a row or focus any of its fields to automatically loop that record in the preview. Editing its range immediately restarts playback with the new interval. Start/end fields clamp crossed endpoints to keep `start < end`. Its range sliders also update the record; Use full range assigns the full source interval. Delete selected removes one record. Save validates unique names, source availability and frame bounds before publishing anything. Unrelated model properties and unknown record fields survive edits. Refresh reloads a clean catalog and the preview while keeping the selected model and transform mode; unsaved catalog edits are retained.

## Saved artifact and runtime use

The artifact is SceneMax catalog metadata:

```json
{
  "name": "example_model",
  "path": "Models/example.glb",
  "animationFrameRanges": [
    { "name": "opening", "sourceAnimation": "Complete take", "start": 0, "end": 30 },
    { "name": "ending", "sourceAnimation": "Complete take", "start": 30, "end": 60 }
  ],
  "animationFrameRangesSourceAnimation": "Complete take"
}
```

Use the record name with the ordinary SceneMax animation syntax:

```text
actor=>example_model
actor.opening
actor.ending at speed of 0.5 loop
```

The runtime resolves the record on that model, wraps the source curves over the selected time interval, and gives the result its own zero-based duration. Existing speed, looping, transitions and completion/wait behavior consume that duration. Source models, meshes, skins, textures and animation curves are unchanged. Named records take precedence over equally named source clips. Restart the game after saving changes to reload its cached metadata.

Frames are indices in the longest transform track, including nonuniform timestamps. For animations without a transform track the fallback is 24 FPS. The end frame is the interval endpoint; a valid record requires `0 <= start < end <= last frame`. Multiple records can use different source animations and overlapping intervals. Unnamed glTF animations appear as `Animation N`. Legacy shared-source fields and J3O registrations with a supported `sourceModel` alias are understood; standalone J3O files are unsupported.

The analyzer saves frame-range metadata without creating a second model or embedding records into GLTF. Model conversion and optimization remain in Import 3D Model. Selecting a shared model opens its owning shared catalog as an editable tab. Adding, editing, or deleting ranges there uses the same Save and Undo/Redo controls. The shared-catalog banner identifies changes that apply to all projects using those models; project-local registrations continue to take precedence. Save reports read-only files and external changes without overwriting them.

## Ownership and verification

`IDE/core/animation_analyzer` owns lossless edits. `IDE/services/animation_analyzer` resolves contained sources and validates on the disk worker. Application transactions own document mutation. Presentation owns retained controls and an isolated layer-13 preview. `Common/assets/animation_ranges` loads source timing and metadata; `Engine/animation` preserves source interpolation with time-offset curve wrappers shared by preview and projector.

`Tests/fixtures/animation_analyzer` contains an artificial skinned model with seven nonuniform keyframes and two records. Tests cover timing, aliases, unknown fields, invalid records, external changes, native retained editing, same-frame save and undo. GPU smoke can open an isolated copy of its catalog using `--script resources/Models/models-ext.json --smoke-frames 300 --smoke-screenshot <path>`; `SCENEMAX_SMOKE_ANALYZER=1` selects and loops its second record. This hook is installed only for explicit bounded smoke launches.
