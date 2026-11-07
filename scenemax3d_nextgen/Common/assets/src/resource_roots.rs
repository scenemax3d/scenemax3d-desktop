//! Shared-resource discovery used by the IDE and projector.
use std::path::{Path, PathBuf};

pub fn shared(
    project: Option<&Path>,
    script: Option<&Path>,
    local: Option<&Path>,
) -> Option<PathBuf> {
    let executable = std::env::current_exe().ok();
    let cwd = std::env::current_dir().ok();
    discover(
        project,
        script,
        local,
        std::env::var_os("SCENEMAX_BUILTIN_RESOURCES")
            .map(PathBuf::from)
            .as_deref(),
        executable.as_deref(),
        cwd.as_deref(),
    )
}

fn discover(
    project: Option<&Path>,
    script: Option<&Path>,
    local: Option<&Path>,
    explicit: Option<&Path>,
    executable: Option<&Path>,
    cwd: Option<&Path>,
) -> Option<PathBuf> {
    let mut candidates = Vec::new();
    candidates.extend(explicit.map(Path::to_owned));
    // The installed product owns the shared library, independent of launch cwd.
    for base in [executable.and_then(Path::parent), project]
        .into_iter()
        .flatten()
    {
        candidates.extend(
            base.ancestors()
                .filter(|p| p.join(".scenemax-studio-installation").is_file())
                .map(|p| p.join("resources")),
        );
    }
    for base in [project, script, executable.and_then(Path::parent), cwd]
        .into_iter()
        .flatten()
    {
        candidates.extend(base.ancestors().map(|p| p.join("resources")));
    }
    let local = local.and_then(|p| crate::filesystem::canonicalize(p).ok());
    candidates
        .into_iter()
        .filter_map(|p| crate::filesystem::canonicalize(&p).ok())
        .find(|p| {
            Some(p) != local.as_ref()
                && [
                    "Models", "models", "sprites", "fonts", "audio", "sounds", "Textures",
                    "material", "shaders", "effects",
                ]
                .iter()
                .any(|folder| crate::filesystem::is_dir(&p.join(folder)))
        })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn installed_shared_library_beats_unrelated_cwd_for_external_and_empty_projects() {
        let dir = tempfile::tempdir().unwrap();
        let installed = dir.path().join("installed");
        let shared = installed.join("resources");
        let project = dir.path().join("external/project");
        let cwd = dir.path().join("unrelated");
        std::fs::create_dir_all(shared.join("Models")).unwrap();
        std::fs::create_dir_all(cwd.join("resources/Models")).unwrap();
        std::fs::write(installed.join(".scenemax-studio-installation"), "1").unwrap();
        for local_folder in [false, true] {
            if local_folder {
                std::fs::create_dir_all(project.join("resources/Models")).unwrap();
            }
            assert_eq!(
                discover(
                    Some(&project),
                    None,
                    Some(&project.join("resources")),
                    None,
                    Some(&installed.join("projector.exe")),
                    Some(&cwd)
                ),
                Some(shared.canonicalize().unwrap())
            );
        }
    }
    #[test]
    fn shared_sprite_only_library_does_not_require_models() {
        let dir = tempfile::tempdir().unwrap();
        let shared = dir.path().join("resources");
        std::fs::create_dir_all(shared.join("sprites")).unwrap();
        assert_eq!(
            discover(
                Some(&dir.path().join("projects/game")),
                None,
                None,
                None,
                None,
                None
            ),
            Some(shared.canonicalize().unwrap())
        );
    }
}
