# Java IDE artwork

These bundled PNG assets reuse the existing SceneMax Java IDE artwork:

- Folder, script, main and C# images: `assets/images`.
- File-type drawings: `src/com/scenemax/desktop/ScriptsTreeCellRenderer.java`, its `create*Icon` methods.
- Toolbar drawings: `scenemax_designer/src/com/scenemax/designer/DesignerPanel.java`, `createDesignerToolbarIcon` and `drawToolbar*` methods.

Procedural drawings were exported through the original Java2D drawing methods with a 4x graphics transform and canvas, preserving paths, colors, stroke weights and alpha. Existing PNG files were copied unchanged. Rust embeds the PNGs directly; Java is not involved in building or running the IDE. Scene toolbar artwork is displayed at 16 logical pixels within 24-pixel buttons; file icons at 18 logical pixels.
