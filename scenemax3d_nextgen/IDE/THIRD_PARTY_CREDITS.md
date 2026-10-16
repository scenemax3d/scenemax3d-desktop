# IDE third-party credits

Help > About displays the SceneMax Studio version, official website and repository, followed by searchable third-party credits. Select a component name to visit its upstream project; select its license to expand locally embedded notices. Previous/Next page through all components. Escape or Close dismisses the dialog.

The credits are embedded in the executable and work offline. Web links use the default browser through the IDE storage worker. No project files are modified.

Regenerate after dependency changes:

```powershell
python Tools/generate_ide_credits.py
```

The generator traverses the locked Windows dependency graph starting at scenemax_ide, including normal and build dependencies and excluding dev-only edges and SceneMax-owned crates. Package versions, contributors, licenses and links come from Cargo metadata; available license/notice files come from their source packages. Additional entries acknowledge the native integrations, adapted gizmo code and embedded default font. Native notice inputs are listed explicitly in the generator. This inventory covers the Rust IDE, not legacy IDE components. The reported application version comes from Cargo.toml.

The checked-in IDE/app/assets/credits.json is consumed directly by the retained About view; regenerating does not require network access when Cargo sources are cached. Keep the original native vendor license files when packaging their tools. This credits inventory supplements those vendor notices.

Packages from the same upstream repository are presented as one project credit (including one Bevy entry). Each grouped notice retains its component versions, contributors and license details. Independent Bevy extensions retain their own credits.
