//! Runtime bundles produced with the IDE release. Never invokes a compiler.
use super::{Request, files};
use scenemax_ide_core::deployment::Platform;
use std::{
    io,
    path::{Path, PathBuf},
};

fn required(path: PathBuf) -> io::Result<PathBuf> {
    if path.is_file() {
        return Ok(path);
    }
    Err(io::Error::other(format!(
        "Missing bundled export runtime: {}. Repair the matching SceneMax installation or select a supplied runtime.",
        path.display()
    )))
}
pub(super) fn desktop(workspace: &Path, platform: &Platform, package: &str) -> io::Result<PathBuf> {
    // Target triples must not escape the runtime bundle directory.
    if platform.triple.is_empty()
        || !platform
            .triple
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'-' || c == b'_')
    {
        return Err(io::Error::other("Invalid runtime target triple."));
    }
    let suffix = if platform.target == scenemax_ide_core::deployment::Target::Windows {
        ".exe"
    } else {
        ""
    };
    required(
        workspace
            .join("prebuilt")
            .join(&platform.triple)
            .join(format!("{package}{suffix}")),
    )
}
pub(super) fn check_desktop(request: &Request, platform: &Platform) -> io::Result<()> {
    let projector = if platform.runtime.is_empty() {
        desktop(&request.workspace, platform, "scenemax_projector_nextgen")?
    } else {
        PathBuf::from(&platform.runtime)
    };
    files::native_binary(&projector, platform.target)?;
    let launcher = desktop(&request.workspace, platform, "scenemax_game_launcher")?;
    files::native_binary(&launcher, platform.target)?;
    if request.settings.native_effects
        && platform.target == scenemax_ide_core::deployment::Target::Windows
    {
        required(
            projector
                .parent()
                .unwrap()
                .join("scenemax_effekseer_bevy.dll"),
        )?;
    }
    Ok(())
}
pub(super) fn web(workspace: &Path) -> io::Result<PathBuf> {
    let directory = workspace.join("prebuilt/web");
    for name in ["runtime.js", "runtime_bg.wasm"] {
        required(directory.join(name))?;
    }
    Ok(directory)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn missing_web_bundle_reports_repair_without_building() {
        let dir = tempfile::tempdir().unwrap();
        assert!(
            web(dir.path())
                .unwrap_err()
                .to_string()
                .contains("explicitly enable")
        );
        std::fs::create_dir_all(dir.path().join("prebuilt/web")).unwrap();
        std::fs::write(dir.path().join("prebuilt/web/runtime.js"), "bindings").unwrap();
        assert!(web(dir.path()).is_err());
        std::fs::write(dir.path().join("prebuilt/web/runtime_bg.wasm"), b"\0asm").unwrap();
        assert!(web(dir.path()).is_ok());
    }
}
