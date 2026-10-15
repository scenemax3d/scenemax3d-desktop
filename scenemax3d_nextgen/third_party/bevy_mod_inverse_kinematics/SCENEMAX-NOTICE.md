# Pinned upstream IK solver

Source: https://github.com/Kurble/bevy_mod_inverse_kinematics
Revision: 5c037a33b55e8056ec24222d2968972f59e009d5
Upstream package: 0.12.0, Bevy 0.19. License: MIT OR Apache-2.0 (see upstream README).

SceneMax calls IkConstraint::solve from its own post-animation scheduling adapter rather than installing the upstream Update plugin. This preserves correct animation order and allows layer weighting, target resolution, validation and restoration. Upstream solver source is retained; local robustness changes, if any, are documented below.

Redistributed under the Apache-2.0 option; license text is included in LICENSE-APACHE. Upstream author: Bram Buurlage (as listed in Cargo.toml). No changes to upstream Rust source.
