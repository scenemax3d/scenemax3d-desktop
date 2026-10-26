# IK Asset Designer

Assets > Create New IK Asset opens a name/solver dialog and creates `resources/ik/ik_<name>.smik`. The same action is available from project folders. Existing `.smik` and legacy `.ik.json` documents open in the native designer.

Choose a registered skinned glTF/GLB model. Use IK ID to identify the asset and Layer ID to identify each contact layer; the layer selector displays these IDs. Add independent layers for each contact, select actual skin bones, and set a target object or `object."joint name"`. TwoBoneIK, ThreeBoneIK, FABRIK (arbitrary ancestor chain), FootIK, LookAtIK and AimIK retain the original asset names. Positional layers use the pinned Bevy community solver; LookAt/Aim use the SceneMax directional adapter. FootIK is a targeted limb contact, as in the Java runtime, not automatic terrain raycasting.

The inspector provides add/duplicate/delete, humanoid/use-case presets, enabled/weight/priority/blend, bone dropdowns, affected joints, target/pole, stretch limits, solver iterations, contact offsets and optional target orientation matching. Presets resolve names against the selected model's actual skin joints; unmatched fields remain unbound. Unknown legacy metadata is preserved.

Preview: Run once, Play layer, Play all, Stop IK, Reset pose/view, animation selection/play/stop, model or target move/rotate/scale handles and numeric transforms. Right-drag orbits; the wheel zooms. A target can be a sphere, box, or another registered model; selecting its bone writes the selected layer's runtime target reference. Preview object names are represented by this chosen target model, with each layer retaining its own bone reference. The purple pole marker supplies any configured pole target. Runtime object names must exist in the actual scene.

Scene designer: `.smdesign` model IK assets and enabled layer playback settings are evaluated in the viewport using the shared Bevy IK solver. Attachment references follow the selected object's actual skin joint, preserve the authored local offsets and cancel inherited scale. Position/rotation/scale controls and gizmos edit those local offsets. The viewport reports active IK layers and missing assets, joints or targets. Disabled layers release the underlying pose. Embedded code nodes are not executed by this declarative preview.

Save/Ctrl+S, Undo/Redo, Save All, recovery and restored tabs use the standard document workflow. Legacy source documents outside resources/ik also publish an ID-named runtime copy. Conflicting IDs, externally changed files and unsafe output paths are rejected; source files are never silently deleted.

## Scripts

```scenemax
rider.ik = "ik_riding"
rider.ik.hand_contact.play : target vehicle."grip", blend 0.2, weight 1
rider.ik.foot_contact.play : target vehicle."pedal", blend 0.2
rider.ik.hand_contact.target = other_object
rider.ik.hand_contact.weight = 0.75
rider.ik.hand_contact.stop : blend 0.2
rider.ik = empty
```

Applying an asset prepares its layers; `.play` activates them. The script weight multiplies the authoring weight. Bone targets follow current animated transforms. Separate hands/feet use separate layer IDs. IK modifies bone rotations after animation and before world transform propagation. Removing/stopping restores the underlying animation or original pose. It does not parent the character to the vehicle: author character placement/movement separately.

Contact position offsets are in the target's local space. Match target orientation aligns the endpoint to the target's rotation plus the authored degree offsets. Model root scaling is supported; bone-local scale must be unit scale. Invalid/ambiguous joints, missing targets, invalid chains and degenerate solves produce diagnostics and preserve finite poses. Shared-chain layers are evaluated in ascending priority. Cyclic constraints across separate characters are not a supported constraint graph.

The legacy smoothing/rotationSpeed/ground/pelvis fields are retained for compatibility; this port does not claim terrain planting or pelvis compensation. The original Java runtime did not implement those behaviors either.

## Solver source

SceneMax uses a dedicated IK solver with Bevy 0.20. SceneMax vendors [bevy_mod_inverse_kinematics](https://github.com/Kurble/bevy_mod_inverse_kinematics) 0.12.0 at revision `5c037a33b55e8056ec24222d2968972f59e009d5`, with its local dependency updated to Bevy 0.20. The shared `Engine/ik` adapter runs the solver in the correct animation schedule and handles targets, layering, weighting, restoration and scaled model roots. It deliberately does not install upstream's Update-scheduled plugin. No Java/JME IK implementation is linked into the Bevy product.


## Tune IK directly in the scene designer

Select a model that has an IK asset and enabled scene layers. The viewport draws only those layers' chains: cyan end joints are contact controls, amber middle joints are knee/elbow direction controls, and gray joints show the fixed chain structure. Click a colored joint, or use **Contact handle** / **Bend handle** in that layer's inspector.

- **Contact handle → Move** adjusts the contact position relative to its existing target (including a target bone). **Rotate** adjusts the endpoint orientation and enables orientation matching for this scene instance. The target collider and shared IK asset remain unchanged.
- **Bend handle → Move** sets the pole in model coordinates. It follows the model's placement/attachment. This guides the knee/elbow direction; the root-to-contact distance and bone lengths still determine bend amount. The chain root is not freely translated or rotated by the solver controls. Scale is not an IK pose operation.
- The amber pole marker and its connecting line remain visible when moved away from the joint. An unreached contact is highlighted and reported in the viewport hint; moving the contact closer or adjusting the model placement may be necessary.
- **Model transform** returns the gizmo to the model. **Reset pose** removes only that layer's scene overrides and restores the shared asset settings. Dragging is grouped for Undo; ordinary Ctrl+S, recovery, and designer code regeneration apply.

Scene overrides are stored in each `ikLayers` row as `positionOffset` (target-local), `rotationOffset` (XYZ degrees relative to target rotation), and `polePosition` (model-local). Generated code applies these before `.play`, using the same shared IK adapter as the preview. They affect only that model instance. Existing layers without these fields retain their previous behavior. Directional LookAt/Aim layers do not have a positional knee/end chain; tune those in the asset tool.
