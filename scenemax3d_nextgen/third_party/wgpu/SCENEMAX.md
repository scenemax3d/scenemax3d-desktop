# Browser handle access

Source: crates.io `wgpu` 29.0.4, upstream commit
`e99f5305ded96ff7006f0714d043a7f735bd45c2` (the published crate's VCS metadata).
MIT / Apache-2.0 licenses are included unchanged.
Published `.crate` SHA-256:
`76e8840e1ba2881d4cbb18d2147627a56af426ff064c0401eb0c8410c6325d07`.

SceneMax adds four methods, compiled only for the browser WebGPU backend:
`Device::browser_device`, `RenderPass::browser_render_pass`,
`TextureView::browser_texture_view` and `CommandEncoder::browser_command_encoder`.
They borrow the existing JavaScript object through the backend's checked
dispatch accessor. There are no pointer casts or changes to native backends.

An external renderer must use the supplied device, draw synchronously, never
retain/end the borrowed pass, and never destroy the device. Use a dedicated
pass so external state changes cannot invalidate Bevy's tracked state. The encoder
accessor is used only between passes to copy scene color into Effekseer's distortion
background. External code must never finish or submit the borrowed encoder.

On update, replace the published crate, reapply only these four accessors, and
rerun the browser interoperability fixture plus native checks. Do not patch
the global Cargo registry cache.
