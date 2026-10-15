# Weapon Designer

Open **Assets > Create New Weapon…**, enter a name, choose Sword, Pistol, Bow or Magic Staff, and confirm. The designer opens as a normal document tab. The project-folder context menu offers the same command. As in the original designer, all four templates start with an unbound model and one default posture.

New assets live in `resources/weapons/weapon_<name>.smweapon`. No separate registration is needed: the existing runtime resolves weapon definitions by ID. Use Save / Ctrl+S, Undo / Ctrl+Z, Redo, Save All and normal tab closing. Open weapon tabs participate in workspace restoration and unsaved-buffer recovery.

## Overview and postures

- Set the weapon ID and select a registered project or shared model. Changing the weapon model resets the posture transforms, matching the original tool; Undo restores them.
- Choose a player model for the preview. This choice is stored in `designerMetadata.previewPlayerModelAssetId`.
- Add, duplicate, delete and select named postures, and choose the default posture. At least one posture must remain. New postures clone the default; duplicates copy the selected posture.
- Type an attachment name or choose a bone used by the loaded player mesh. Clear Attachment attaches at the origin. The resolver prefers joints used by the preview mesh over same-named nodes in unused imported rigs. An unresolved or ambiguous socket produces an explicit diagnostic and hides the weapon instead of placing it at the model origin.
- Edit position, rotation in degrees and scale with precise numeric inputs or sliders. Red, green and blue viewport handles edit X, Y and Z; Move and Rotate affect the selected posture.

## Colliders and preview

The Colliders tab supports box and sphere colliders, duplicate/delete, names, shape changes and all nine transform fields. Collider outlines follow the weapon posture. The selected collider is highlighted; viewport handles edit its local transform. Boxes have unit base dimensions; spheres have base radius 0.5, matching the runtime.

Right-drag orbits the preview, the wheel zooms, and Reset View frames the models. Test Animation lists clips from the player model. Bundled clips targeting duplicate imported rigs are adapted in memory to uniquely named bones in the visible skeleton; original assets are unchanged. Animation Speed supports 1–100%; Stop / Resume pauses playback, with progress shown in the status line. Dragging a transform handle pauses playback for precise placement. Rig attachment follows the current animation frame and cancels inherited player scale, while the weapon's own resource and posture transforms remain effective.

Preview loads native glTF/GLB, including `bevyPath` registrations. Missing models, failed loads and legacy-only formats show diagnostics. Refresh Models rescans the active project and shared registrations. This tool does not convert legacy model formats or change the runtime.

## Files and safeguards

The output uses `SceneMaxWeaponDefinition` schema 1.0, with `modelAssetId`, `defaultPostureId`, `postures`, `colliders`, and `designerMetadata`. Authoring preserves unknown JSON metadata; legacy attachment fields normalize when edited.

Weapons already directly under `resources/weapons` or `resources/Weapons` save in place. Changing an ID preserves the source filename; runtime ID lookup supports this. A legacy source elsewhere in a project also publishes an ID-named runtime copy under `resources/weapons` on save. Existing conflicting definitions, external source changes, read-only files and linked output paths block publication. Failed source saves roll back their runtime copy. The editor does not silently delete other weapon documents.

The pure schema/edit operations live in `IDE/core/weapon.rs`; creation and publication live in `IDE/services/weapon.rs`; the retained workbench and isolated render layer live in `IDE/app/presentation/weapon`. The Java implementation and the game runtime are unchanged.
