# Web distortion validation

The effect and its four textures are the unmodified upstream
`Resources/00_Basic/Simple_Distortion.efkefc` sample from ResourceData commit
`54e00177497a148647373099e996d0392f28aec2`, distributed with the pinned
EffekseerForWeb archive. Effect SHA-256:
`6e9e379348653dd65da220eb393f531d037b1691ec9cbf344340cf2a931fa321`.
Upstream effect/texture data licensing:
https://effekseer.github.io/Help_Tool/en/overview.html#license

Export using the real deployment pipeline and ZIP preview server:

```powershell
cargo run --locked -p scenemax_ide_services --example web_effects -- "$PWD/Tests/fixtures/web_distortion/project"
```

After Start game, stripes behind the effect should bend and move. The foreground
occluder should keep its straight edges. D deletes the effect (straight stripes),
R recreates it, C moves the camera. Resize the browser while the effect plays and
check for black regions, flipped sampling or WebGPU validation errors.

The runtime and engine contain no references to this fixture or any game assets.

Validated on 2026-10-23 in a WebGPU browser through the deployment ZIP preview:
the scene stripes visibly refract, the foreground box occludes the effect,
deleting restores straight stripes, and replay, camera movement and resizing
continue rendering without console warnings or WebGPU validation errors.
The JavaScript tests also check viewport crop uniforms and deferred target
destruction when multiple views are encoded before submission.

Mounted-resource regression: after the effect has rendered, stop the preview
server, press D and confirm straight stripes, then press R. The effect must load
again from the mounted startup files and refract the scene without any HTTP
server or full-screen loading panel. This check passed on 2026-10-23.
