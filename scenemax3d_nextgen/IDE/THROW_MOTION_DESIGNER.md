# Throw Motion Designer

Choose **Assets → Create New Throw Motion…** (also available on a project folder). Enter a name and choose Target Arc, Ballistic, Straight Projectile, Homing, Returning, or Physics. The IDE creates and opens `resources/throw_motions/motion_<name>.smmotion` without overwriting an existing asset.

The overview edits the motion ID, name, and type. The parameter form changes with the type and retains all other parameters and unknown metadata. Numeric fields support typing and sliders; boolean and enumerated values use dropdowns. The six templates use the SceneMax motion definition defaults.

The 3D preview shows the ground, blue launch point, red target, orange projectile, cyan trajectory, yellow samples, and green velocity vector. Use Play/Pause, Step (1/30 second), Reset, Reset View, Trajectory, and Samples. Set target distance (2–40) and height (−5–12), drag to orbit, and scroll to zoom. Changes resample the trajectory; playback loops. Target controls affect only the preview. The status shows elapsed/total time, sample count, and validation diagnostics.

The preview follows the original fixed-step kinematic sampler at 60 Hz. Physics uses a ballistic preview; force, mass, drag, collision outcomes, and target-loss behavior are runtime settings rather than a simulation of the project's physics world. Arc/return endpoints are included even for durations that do not divide into 60 Hz. Each simulated phase is capped at 60 seconds to keep large imported settings responsive. This cap does not alter the saved motion. Custom curve authoring remains reserved, as in the original tool.

**Save / Ctrl+S**, Undo/Redo, Save All, dirty-tab closing, recovery, and restored workspace tabs use the normal document workflow. Invalid required values block publishing. Direct runtime files save in place. Older authoring documents in scripts also publish an ID-named copy under `resources/throw_motions`; unrelated files are never deleted. Duplicate IDs, external edits, read-only files, or unsafe paths block destructive replacement. No index registration is needed: the existing runtime resolves `.smmotion` definitions by ID.

Implementation: `IDE/core/motion` owns schema, edits, field specifications and sampling; `IDE/services/motion` owns filesystem creation/publication; `IDE/app/presentation/motion` owns retained controls and isolated Bevy render layer 11.
