//! Durable navigation state, independent of unsaved-buffer recovery.
use crate::{Filesystem, ServiceError, io_result};
use scenemax_ide_core::{Document, EditorWorkspace, Project, Selection};
use serde::{Deserialize, Serialize};
use std::{
    fs,
    io::{Read, Write},
    path::{Component, Path, PathBuf},
};

/// One previously open document, relative to its project root.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TabState {
    /// Project-relative document path.
    pub path: PathBuf,
    /// UTF-8 byte offset of the selection anchor.
    pub anchor: usize,
    /// UTF-8 byte offset of the caret.
    pub focus: usize,
}
/// Versioned project-local workspace navigation state.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct WorkspaceState {
    /// Format version.
    pub version: u32,
    /// Open tabs in display order, including designer documents.
    pub tabs: Vec<TabState>,
    /// Project-relative selected tab, if any.
    pub active: Option<PathBuf>,
}
impl WorkspaceState {
    /// Capture navigation without copying any unsaved text.
    pub fn capture(workspace: &EditorWorkspace) -> Self {
        let root = workspace.project().root();
        Self {
            version: 1,
            tabs: workspace
                .documents()
                .filter_map(|(_, doc)| {
                    Some(TabState {
                        path: doc.path().strip_prefix(root).ok()?.into(),
                        anchor: doc.selection().anchor,
                        focus: doc.selection().focus,
                    })
                })
                .collect(),
            active: workspace
                .active_id()
                .and_then(|id| workspace.document(id).ok())
                .and_then(|doc| doc.path().strip_prefix(root).ok())
                .map(Path::to_owned),
        }
    }
}
fn relative(path: &Path) -> bool {
    !path.as_os_str().is_empty() && path.components().all(|c| matches!(c, Component::Normal(_)))
}
fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T, ServiceError> {
    let mut bytes = Vec::new();
    io_result(path, "Read workspace state", fs::File::open(path))?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|source| ServiceError::Io {
            operation: "Read workspace state",
            path: path.into(),
            source,
        })?;
    if bytes.len() > 1024 * 1024 {
        return Err(ServiceError::Limit("Workspace state exceeds 1 MiB"));
    }
    serde_json::from_slice(&bytes).map_err(|e| ServiceError::Recovery(e.to_string()))
}
fn atomic(path: &Path, bytes: &[u8]) -> Result<(), ServiceError> {
    let parent = path
        .parent()
        .ok_or(ServiceError::Limit("Missing workspace state directory"))?;
    io_result(
        parent,
        "Create workspace state directory",
        fs::create_dir_all(parent),
    )?;
    let mut file = io_result(
        path,
        "Stage workspace state",
        tempfile::NamedTempFile::new_in(parent),
    )?;
    io_result(path, "Write workspace state", file.write_all(bytes))?;
    io_result(path, "Flush workspace state", file.as_file().sync_all())?;
    file.persist(path).map_err(|e| ServiceError::Io {
        operation: "Replace workspace state",
        path: path.into(),
        source: e.error,
    })?;
    Ok(())
}
/// Persist project navigation atomically, optionally updating the application's last project.
pub fn save(
    root: &Path,
    state: &WorkspaceState,
    last_project: Option<&Path>,
) -> Result<(), ServiceError> {
    if is_installation_root(root) {
        return Ok(());
    }
    let project = Project::new(root.to_owned(), vec![]);
    let folder = root.join(".scenemax-studio");
    if folder.exists() {
        Filesystem::resolve(&project, &folder)?;
    }
    let bytes =
        serde_json::to_vec_pretty(state).map_err(|e| ServiceError::Recovery(e.to_string()))?;
    atomic(&folder.join("workspace.json"), &bytes)?;
    if let Some(path) = last_project {
        atomic(
            path,
            &serde_json::to_vec(root).map_err(|e| ServiceError::Recovery(e.to_string()))?,
        )?;
    }
    Ok(())
}
/// Load existing tabs from disk, skipping removed, inaccessible or invalid files.
/// Invalid state falls back to normal project opening; an intentionally empty tab set is retained.
pub fn restore(project: &Project) -> Option<(Vec<Document>, Option<PathBuf>)> {
    let path = Filesystem::resolve(
        project,
        &project.root().join(".scenemax-studio/workspace.json"),
    )
    .ok()?;
    let state: WorkspaceState = read_json(&path).ok()?;
    if state.version != 1 || state.tabs.len() > 512 {
        return None;
    }
    let mut docs = Vec::new();
    for tab in state.tabs {
        if !relative(&tab.path) {
            continue;
        }
        if let Ok(mut doc) = Filesystem::open_document(project, &tab.path) {
            if docs.iter().any(|d: &Document| d.path() == doc.path()) {
                continue;
            }
            doc.select(Selection {
                anchor: tab.anchor,
                focus: tab.focus,
            });
            docs.push(doc);
        }
    }
    let active = state
        .active
        .filter(|p| relative(p))
        .and_then(|p| Filesystem::resolve(project, &p).ok())
        .filter(|p| docs.iter().any(|d| d.path() == p));
    Some((docs, active))
}
/// Identify the program directory, which must not become an implicit project.
pub fn is_installation_root(root: &Path) -> bool {
    root.join(".scenemax-studio-installation").is_file()
}
/// Locate the installed distribution independently of the working directory.
pub fn installation_root() -> Option<PathBuf> {
    let executable = std::env::current_exe().ok()?;
    installed_history_root(&executable)
}
fn installed_history_root(executable: &Path) -> Option<PathBuf> {
    executable
        .parent()
        .filter(|p| is_installation_root(p))
        .map(Path::to_owned)
}
/// Installed copies keep navigation history separate from development launches.
pub fn last_project_file() -> Option<PathBuf> {
    if let Some(root) = installation_root() {
        return Some(root.join("user-state/last-project.json"));
    }
    #[cfg(windows)]
    let root = std::env::var_os("LOCALAPPDATA").map(PathBuf::from);
    #[cfg(not(windows))]
    let root = std::env::var_os("XDG_STATE_HOME")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|p| PathBuf::from(p).join(".local/state")));
    root.map(|p| p.join("SceneMax/Studio/last-project.json"))
}
/// Read the last project only if its directory still exists.
pub fn last_project(path: &Path) -> Option<PathBuf> {
    read_json::<PathBuf>(path)
        .ok()
        .filter(|p| p.is_absolute() && p.is_dir() && !is_installation_root(p))
}

/// Relaunch the IDE after the previous UI has shut down. Arguments are never shell-interpreted.
pub fn restart(
    executable: &Path,
    root: &Path,
    catalog: Option<&Path>,
    projector: Option<&Path>,
) -> Result<(), ServiceError> {
    let mut command = std::process::Command::new(executable);
    if !is_installation_root(root) {
        command.arg("--project-root").arg(root);
    }
    if let Some(path) = catalog {
        command.arg("--project-catalog").arg(path);
    }
    if let Some(path) = projector {
        command.arg("--projector").arg(path);
    }
    command.spawn()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn installed_history_is_separate_and_empty_root_is_not_saved() {
        let dir = tempfile::tempdir().unwrap();
        let installed = dir.path().join("installed");
        fs::create_dir(&installed).unwrap();
        let exe = installed.join("scenemax_ide.exe");
        assert_eq!(installed_history_root(&exe), None);
        fs::write(installed.join(".scenemax-studio-installation"), "1").unwrap();
        assert_eq!(installed_history_root(&exe), Some(installed.clone()));
        let history = installed.join("user-state/last-project.json");
        let state = WorkspaceState {
            version: 1,
            tabs: vec![],
            active: None,
        };
        save(&installed, &state, Some(&history)).unwrap();
        assert!(!history.exists());
        let project = dir.path().join("project");
        fs::create_dir(&project).unwrap();
        save(&project, &state, Some(&history)).unwrap();
        assert_eq!(last_project(&history), Some(project));
        assert!(!installed.join(".scenemax-studio").exists());
    }

    #[test]
    fn restores_tab_order_active_file_and_unicode_selection_without_saving_edits() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::create_dir(root.join("scripts")).unwrap();
        fs::write(root.join("scripts/first.code"), "αβ saved").unwrap();
        fs::write(root.join("scripts/scene.smdesign"), r#"{"entities":[]}"#).unwrap();
        let project = Filesystem::open_project(&root).unwrap();
        let mut workspace = EditorWorkspace::new(project);
        let first = workspace
            .open_document(
                Filesystem::open_document(workspace.project(), Path::new("scripts/first.code"))
                    .unwrap(),
            )
            .unwrap();
        workspace
            .open_document(
                Filesystem::open_document(workspace.project(), Path::new("scripts/scene.smdesign"))
                    .unwrap(),
            )
            .unwrap();
        workspace.select(first).unwrap();
        workspace.document_mut(first).unwrap().select(Selection {
            anchor: 2,
            focus: 4,
        });
        let state = WorkspaceState::capture(&workspace);
        let pointer = root.join("settings/last-project.json");
        save(&root, &state, Some(&pointer)).unwrap();
        assert_eq!(last_project(&pointer), Some(root.clone()));
        let (docs, active) = restore(workspace.project()).unwrap();
        assert_eq!(
            docs.iter()
                .map(|d| d.path().file_name().unwrap().to_str().unwrap())
                .collect::<Vec<_>>(),
            ["first.code", "scene.smdesign"]
        );
        assert_eq!(active, Some(root.join("scripts/first.code")));
        assert_eq!(
            docs[0].selection(),
            Selection {
                anchor: 2,
                focus: 4
            }
        );
        fs::remove_file(root.join("scripts/scene.smdesign")).unwrap();
        assert_eq!(restore(workspace.project()).unwrap().0.len(), 1);
        let mut invalid = state;
        invalid.tabs.push(TabState {
            path: PathBuf::from("../outside.code"),
            anchor: 0,
            focus: 0,
        });
        save(&root, &invalid, None).unwrap();
        assert_eq!(restore(workspace.project()).unwrap().0.len(), 1);
    }
    #[test]
    fn empty_workspace_stays_empty_and_corrupt_state_is_ignored() {
        let dir = tempfile::tempdir().unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let workspace = EditorWorkspace::new(project);
        save(
            workspace.project().root(),
            &WorkspaceState::capture(&workspace),
            None,
        )
        .unwrap();
        assert!(restore(workspace.project()).unwrap().0.is_empty());
        fs::write(dir.path().join(".scenemax-studio/workspace.json"), "{bad").unwrap();
        assert!(restore(workspace.project()).is_none());
    }
}
