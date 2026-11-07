//! Shared inventory discovery for every designer.
use std::path::{Path, PathBuf};

pub(crate) fn shared(project: &Path) -> Option<PathBuf> {
    scenemax_assets::resource_roots::shared(Some(project), None, Some(&project.join("resources")))
}
