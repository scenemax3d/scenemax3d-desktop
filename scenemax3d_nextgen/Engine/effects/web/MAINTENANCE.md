# Bundled Effekseer browser runtime

Users consume these files from their exported ZIP. No CDN, Emscripten, CMake,
Node.js or additional effect SDK is used by exported games.

Effect files and dependencies are read from the same mounted startup downloads
as Bevy through `read_file`. The upstream buffer/resource-loader APIs avoid
fetching them again during gameplay, including after destruction and replay.
Relative dependency paths remain anchored to each effect's original directory.
Effect decoding progress uses a small nonblocking status, not the startup panel.
Parsed built-in effect resource tables select the runtime dependencies without
copying entire sample folders. Web manifests retain all logical paths but map
byte-identical files to a single download. Aliases share mounted immutable bytes;
relative effect dependencies resolve against their original paths as before.

The unmodified runtime files are EffekseerForWeb package version **0.1.0**, commit
`da76b5023bd7aa10fe66ee05f6fecd4b84debce3`, with Effekseer submodule
`216c307192ff9bc7917472b05731bbc4fd46fa04`. There were no tagged GitHub releases
when checked on 2026-10-22. The chosen distribution is from successful upstream
CI run `32250773782`, artifact `9364608544`, `EffekseerForWeb0.1.0`.

Archive SHA-256:
`c08428bffb5153317835995be2f83ee276f2d6003c7343675ca3b3967a82ee77`.
Individual byte-level hashes are in `vendor/SHA256SUMS`. `.gitattributes` disables
line-ending conversion for this directory. The exporter checks those hashes
before writing the runtime into an export. Runtime and dependency license notices
are bundled alongside the binaries. The WebGL backend is not shipped.

## Restore and update procedure

From the workspace, `python Engine/effects/web/verify_bundle.py` verifies all
bundled files. Add `--restore-archive <pinned.zip>` to restore the four runtime
files from the exact distribution after verifying both archive and file hashes.
The script never downloads files or changes the recorded checksums.

1. Obtain the distribution for the exact CI run above:
   https://nightly.link/effekseer/EffekseerForWeb/actions/runs/32250773782/EffekseerForWeb0.1.0.zip
   Verify its archive hash before extracting. This upstream CI artifact expires
   2026-11-17; the checked-in files are the durable distribution, not that URL.
2. Copy `effekseer.js`, `effekseer-webgpu.js`, `effekseer-webgpu.wasm` and
   `effekseer.d.ts` unchanged. Fetch the MIT notice from the wrapper commit and
   the core `LICENSE` / `LICENSE_RUNTIME_VULKAN` dependency notices from the
   pinned Effekseer commit, plus LLGI's notice at
   `8c476bdea911d99dc307655be3cad1cc2b8d5a1e`. The included Emscripten license text
   is referenced from its `4.0.11/LICENSE`; that is an attribution source, not a
   claim about the historic CI compiler version. Verify every file against `SHA256SUMS`.
3. For an update, choose a fixed upstream commit and successful distribution run,
   record its artifact ID/hash and Effekseer submodule commit, stage the same
   files in a separate directory, and regenerate the per-file hashes only after
   validating the archive. Never use a moving `main`, `latest` or CDN URL in an
   exported game.
4. For a maintainer source build, clone that commit with recursive submodules,
   install dependencies with `npm ci`, activate a fixed Emscripten SDK version,
   then run `npm run package:distribution:build`. Record the SDK version and
   archive digest with the new pin. Upstream's selected CI used `emsdk latest`;
   a byte-identical source rebuild of that historic artifact is **not claimed**.
   Restoring the checked-in, checksum-pinned bytes is reproducible without a SDK.
5. Run `node --test Engine/effects/web/*.test.mjs`, the services Web-effects
   and preview tests, the shared-pass fixture, and the actual exported fixture:
   `cargo run --locked -p scenemax_ide_services --example web_effects`.
   Test the ZIP over the actual preview server and under a URL subdirectory.
   Check console/WebGPU errors, depth, camera movement, resize, lifecycle and
   dependency failure behavior. Check the native-effects feature as well.

The wgpu accessors are maintained separately in `third_party/wgpu/SCENEMAX.md`.
The rendering proof and its limitations are in
`Tests/fixtures/effekseer_web_interop/CONTRACT.md`.

## Current limits

- Single-sample color/depth rendering; effect-bearing Web scenes disable MSAA.
- SDR targets and `rg11b10ufloat` are accepted by the upstream external-pass API.
  Unsupported target formats cause a visible error, rather than drawing nothing.
- Distortion samples a GPU copy of the current camera's scene color, captured
  after Bevy transparent geometry and before Effekseer. The viewport is cropped
  and targets are resized as needed. Other Effekseer particles in the same pass
  are not included in this snapshot. Distortion requires an SDR camera target.
- Soft particles, custom materials and GPU-simulated particles remain rejected.
  Sampling Bevy's depth texture for soft particles is not implemented.
- Compiled effects must contain their editor metadata so feature preflight can
  conservatively reject unsupported settings. Raw `.efk` files are not accepted.
- Effects draw after Bevy transparent geometry and before tone mapping; there is
  no joint depth sorting between individual Bevy and Effekseer transparent items.
- No device-loss restoration: the error instructs the player to reload.
- Browser one-shots use live renderer completion, including dynamically evaluated
  lifetimes. They do not use the desktop metadata timeout. The existing network
  protocol cannot reconstruct a finite late-join phase without a known duration;
  browser effects use its unbounded-lifetime path and late joins start afresh.
  Live play/replacement/stop ownership still uses the existing generation protocol.

Upstream API/design/distribution sources:
https://github.com/effekseer/EffekseerForWeb/tree/da76b5023bd7aa10fe66ee05f6fecd4b84debce3/docs

## Distortion adapter contract

The pinned native `Context::SetWebGPUBackgroundImage` creates a single-sample
RGBA8 texture, installs it with `SetBackgroundInternal`, and selects normal UVs.
`background.mjs` scopes interception of the supplied device's `createTexture`
to that synchronous public API call, adds render-attachment usage, validates the
allocation, and borrows the resulting texture. It does not inspect Wasm memory or
private Emscripten handle tables. Only allocation/resizing uploads zeroed pixels;
each frame uses a GPU-only render pass to copy scene color (including sRGB
decoding). No CPU frame readback, separate canvas overlay, or binary patch is used.

The native context retains ownership. Destruction after a resize is deferred
until the previous frame has been submitted and completed, including multiple
camera views in one frame. Immutable per-view crop uniforms avoid queue-write
races. Revalidate this allocation contract when changing the runtime pin.
