# Effekseer browser integration gate

This fixture isolates rendering interoperability from production playback/export
tests. Its initial result established the architecture used by the browser backend.

## Proven rendering architecture (2026-10-22)

The compiled Bevy example ran in a WebGPU browser on localhost. At a fixed
simulation frame, the foreground blue Bevy cuboid occluded the left half of the
particle field. Hiding the cuboid revealed those same particles. The successful
run continued for over 3,000 shared-device frames without new console errors.
Screenshots: `target/effekseer-web-proof/occluded.png` and `unoccluded.png`.
This establishes shared-device, shared-target drawing and reversed-depth
occlusion for this fixture, not production playback/export support.

The release requires a canvas context even for external-only initialization.
An unused 1x1 OffscreenCanvas context avoids its hardcoded HTML `#canvas` path.
It must be configured with a canvas-supported non-sRGB format; the external
draw call separately receives Bevy's actual `rgba8unorm-srgb` attachment format.
No visible drawing, presentation or compositing uses the offscreen surface.

Bevy 0.20.0 / wgpu 30.0.0 owns the GPUDevice, command submission, color/depth
attachments and presentation. A dedicated Core3d pass after transparent geometry
and before tone mapping lends its GPURenderPassEncoder synchronously to
Effekseer. No additional canvas or separate-device texture sharing is involved.
The fixture disables MSAA; production effect-bearing Web scenes likewise use
single-sampled color and depth attachments.

EffekseerForWeb 0.1.0, commit `da76b5023bd7aa10fe66ee05f6fecd4b84debce3`,
CI run `32250773782`, artifact `9364608544` is the selected binary.
The repository had no GitHub Releases when checked on 2026-10-22.
The downloaded archive was verified against GitHub's SHA-256 digest before use.
`prepare.ps1` now uses the checked-in runtime and fixture assets, verifying the
per-file hashes; it needs no network or Emscripten installation. The CI artifact
expires on 2026-11-17, but the vendored runtime does not depend on that URL.

Bevy uses reversed depth. The fixture passes the actual Bevy camera/projection
matrices and adapts only Effekseer's pipeline depth comparisons through a bound
device proxy. The fixed-frame occluded/unoccluded images validate this adaptation.
The external pass is ended and submitted only by wgpu. No tracked Bevy drawing
occurs after the external drawing inside that pass.

## Existing script contract (audited)

- `actions.rs` and `startup.rs` create the shared `Effect` component and resolve
  project resource paths. `effect_commands.rs` evaluates position, loop, speed
  aliases and four dynamic inputs before playback.
- Each play installs `Playback` with a fresh u64 generation; transforms follow
  the entity's GlobalTransform, including attachments and ordinary motion.
- Stop, resource replacement, pool reset and destruction remove playback or the
  entity. The browser must reconcile the complete extracted set every frame,
  including frames with zero instances or no active camera.
- Completion is `(instance_id, play_generation)`. Late loads must only populate
  an asset cache, never create a handle; current extracted state creates handles.
  Retired generations cannot complete or restart current playback. Serialize IDs
  as strings across JavaScript to preserve all u64 values.
- One-shots currently use metadata/native duration as well as completion feedback.
  Without native duration the fallback is two seconds: browser integration must
  explicitly address this rather than truncate longer effects while loading.
- Looping restarts when the renderer reports no live particles. Speed and dynamic
  inputs apply before seeking/updating a new handle. Network resume supplies
  elapsed time and must not be discarded.
- Editor-only PreviewOptions additionally carry seek, seed, target, color and
  triggers; these are distinct from the exported game playback contract.

## Acceptance checklist

Visible particles and geometry with depth occlusion; correct camera movement,
resize and alpha blending; clean WebGPU error scopes/console; MSAA policy;
scene color/depth access for distortion/soft particles or explicit rejection;
loop/one-shot/stop/replay/deletion and stale async loads; dependency failures and
subdirectory URLs; user-gesture audio startup; native regression checks; an
exported ZIP served through the IDE preview implementation.
Production fixture instructions and the recorded validation results are in
`../web_effects/README.md`; supported limits are in
`../../../Engine/effects/web/MAINTENANCE.md`.

Upstream references:
- https://github.com/effekseer/EffekseerForWeb/blob/da76b5023bd7aa10fe66ee05f6fecd4b84debce3/docs/Design_en.md
- https://github.com/effekseer/EffekseerForWeb/blob/da76b5023bd7aa10fe66ee05f6fecd4b84debce3/docs/Distribution.md
- https://github.com/effekseer/EffekseerForWeb/blob/da76b5023bd7aa10fe66ee05f6fecd4b84debce3/PackageData/README_en.md
