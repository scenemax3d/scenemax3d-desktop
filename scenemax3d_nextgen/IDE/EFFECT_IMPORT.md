# Effekseer import designer

Assets → Import Effekseer Effect opens a retained Bevy document. Browse a compiled `.efkefc` / `.efk`, inspect its dependency report, test it in the native viewport, choose an asset name, and Import effect. Legacy `.efkproj` files are automatically compiled in the background using the official Effekseer command-line exporter. Conversion writes to a private temporary folder, resolves resources beside the original project, and never changes source files. The IDE discovers Effekseer under the repository `tools` folder or on PATH; set `SCENEMAX_EFFEKSEER_TOOL` to override its executable. If no tool is installed, a same-named runtime export at least as recent as the project can be used. Preview remains embedded in Bevy; no Java is required.

Controls: play, pause, restart, single-frame step (60 Hz), scrub, loop duration and playback speed; replay seed; live emitter position/rotation/scale; four dynamic inputs and four triggers; attraction target; RGBA multiplier; ground grid and four backdrops. Right drag orbits, middle drag pans, wheel zooms. Reset view frames the emitter at its scale. Snapshot saves a PNG of the viewport in the selected project. Inspector and individual sections collapse and scroll. Save/Undo/Redo use normal document commands.

Preparation runs off-thread and holds an immutable private package snapshot. The inspector lists direct textures, models, sounds, materials and curves, with missing files marked explicitly. Missing dependencies and paths outside the selected effect folder block import. Resource packages are bounded to 512 MiB and binary input to 32 MiB. Registration is staged and atomic, rejects duplicate names, preserves the original source and persists preview settings to `.smeffectdesign` and importer draft settings to `.smeffectimport`.

The native renderer has the same limits as the projector (see Engine/effects/README.md). Preview GPU resources are released on reload and when its document closes. This is an import/preview tool, not an Effekseer node editor. Sound files are copied but not auditioned; complex materials with indirect resources may need a self-contained export. No Sketchfab/Java conversion dependency.

Design references:
- [Official viewer tutorial](https://effekseer.github.io/QuickTutorial_Tool/QuickTutorial_en.html): 60 Hz timing and orbit/pan/zoom conventions.
- [Recorder](https://effekseer.github.io/Help_Tool/en/ToolReference/record.html): viewport captures and blending caveats.
- [Native Manager API](https://github.com/effekseer/Effekseer/blob/master/Dev/Cpp/Effekseer/Effekseer/Effekseer.Manager.h): seed, seek, target, dynamic inputs and triggers.
- [Official EFKE resource table reader](https://github.com/effekseer/Effekseer/blob/master/Dev/Cpp/Effekseer/Effekseer/IO/Effekseer.EfkEfcFactory.cpp): versioned dependency parsing. No effect authoring code was copied.
