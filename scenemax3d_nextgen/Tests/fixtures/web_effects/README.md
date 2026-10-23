# Browser effect fixture

`effect.efkefc` is upstream `00_Basic/Simple_Sprite_BillBoard.efkefc`, with its
`Texture/Particle01.png` dependency, from ResourceData commit
`54e00177497a148647373099e996d0392f28aec2` in the pinned EffekseerForWeb CI archive.
These are test assets only; engine code contains no reference to this effect.
Effekseer's texture/effect data licensing is documented at
https://effekseer.github.io/Help_Tool/en/overview.html#license and
https://effekseer.github.io/en/contribute.html.

Run from the workspace:

```powershell
cargo run --locked -p scenemax_ide_services --example web_effects
```

This calls the same dependency selection, Web packaging and ZIP preview code as
the IDE, prints the artifact and localhost URL, and waits for Enter to shut down.
Use `-- --preview <zip>` to inspect an existing ZIP without rebuilding.
To export another project, pass `-- <project-directory> <entry-script-path>
<shared-resources-directory>` (the latter two arguments are optional). Outputs
remain in the workspace's `target/web-effects-exports` validation directory.

After **Start game**, the canvas accepts these existing SceneMax commands:

| Key | Check |
| --- | --- |
| C / V | Move camera sideways / reset camera; particles and geometry move together |
| O | Move the occluder away |
| P | Replace the loop with a one-shot at x=3, speed 0.5, input0=0.75 |
| R | Replay at the origin, looping at speed 2 |
| D | Delete the effect; no particles should remain |

Resize the browser while playing. The foreground box must continue to occlude
particles behind it; alpha-blended particles in front remain visible.

`python Tests/fixtures/web_effects/validation_archives.py <export.zip>` creates
two test-only archives beside the export. Serve `subdirectory.zip` through the
same preview command and open `/releases/demo/index.html`. Serve
`missing-dependency.zip`, click Start, and expect a visible error naming the
missing `Texture/Particle01.png`. Neither variant changes the source project.

## Verification recorded 2026-10-22

- Release Web export built with the real Builder, including its reachable effect
  and texture, pinned runtime and license files. Ran the resulting ZIP with
  `Preview::serve`, which shares the IDE Run Web server implementation.
- WebGPU browser: visible particles, opaque depth occlusion, translucent blending,
  camera movement, resize, one-shot completion/replay, loop replacement and
  deletion. Successful root and nested-path runs had no reported console/WebGPU
  errors. Screenshots are in `target/effekseer-web-proof/export-*.png`.
- Node tests: 13 passing checks for lifecycle reconciliation, u64 generations,
  stale async loads, cleanup, transforms, inputs, speed/seek, failed loads and URLs.
- Services deployment tests: 26 passed, 4 pre-existing ignored tests, including
  the folder-declaration dependency regression, runtime hashes and feature
  preflight.
- Desktop: native-effects `cargo check` and the native DLL duration inspection
  passed (short 0.050s, long 6.333s and unbounded/dynamic examples).
  The optional full native effects unit-test build was interrupted while
  rebuilding Bevy; it is not counted as a passed test run.

The visual fixture exercises sprites and their texture. Audio gesture wiring,
model and curve resource routing are implemented but are not separately covered
by this visual fixture. Distortion has its own `../web_distortion` fixture.
Soft particles, custom materials and GPU particles are explicitly rejected;
see the runtime maintenance document.
