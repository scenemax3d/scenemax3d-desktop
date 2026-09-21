//! Project navigator operations, executed only by the disk worker.
use crate::{Filesystem, ServiceError, io_result};
use scenemax_ide_core::{Document, Project};
use std::{
    fs,
    io::Write,
    path::{Component, Path, PathBuf},
};

/// Local operations available from the project tree.
#[derive(Clone, Debug)]
pub enum TreeOperation {
    /// Create a runtime-ready IK asset in the active project.
    CreateIk {
        /// Display name for the new IK asset.
        name: String,
        /// Initial solver type.
        kind: String,
    },
    /// Create a runtime-ready throw motion.
    CreateMotion {
        /// Display name used to derive the motion ID.
        name: String,
        /// Starter motion type.
        kind: String,
    },
    /// Create a runtime-ready weapon in the active project.
    CreateWeapon {
        /// Display name used for the initial resource ID.
        name: String,
    },
    /// Create a scene directory, designer document and main script as one operation.
    AddScene {
        /// Existing parent directory.
        parent: PathBuf,
        /// New scene name.
        name: String,
    },
    /// Create a file with an immutable template, or a directory when absent.
    Create {
        /// Parent directory.
        parent: PathBuf,
        /// Single child name.
        name: String,
        /// Initial file contents, or a directory.
        source: Option<String>,
    },
    /// Rename or move an existing item without replacing its destination.
    Move {
        /// Existing path.
        path: PathBuf,
        /// Existing destination directory.
        parent: PathBuf,
        /// Single destination name.
        name: String,
    },
    /// Remove from the project tree, retaining a recoverable copy in studio trash.
    Delete(PathBuf),
}

/// Successful disk mutation and the buffer path changes it requires.
pub struct TreeOutcome {
    /// Original paths mapped to their new location, or removed paths.
    pub paths: Vec<(PathBuf, Option<PathBuf>)>,
    /// Newly created document, if any.
    pub document: Option<Document>,
}

fn child_name(name: &str) -> Result<&str, ServiceError> {
    let name = name.trim();
    if name.is_empty()
        || name.starts_with('.')
        || name.ends_with(['.', ' '])
        || name.contains(['/', '\\', ':', '*', '?', '"', '<', '>', '|'])
        || name.chars().any(char::is_control)
        || Path::new(name).components().count() != 1
        || !matches!(
            Path::new(name).components().next(),
            Some(Component::Normal(_))
        )
    {
        return Err(ServiceError::Limit(
            "Enter a single file or folder name without path separators",
        ));
    }
    Ok(name)
}

fn destination(project: &Project, parent: &Path, name: &str) -> Result<PathBuf, ServiceError> {
    let parent = Filesystem::resolve(project, parent)?;
    if !parent.is_dir() {
        return Err(ServiceError::NotDirectory(parent));
    }
    let path = parent.join(child_name(name)?);
    if path.exists() {
        return Err(ServiceError::Limit("The destination already exists"));
    }
    Ok(path)
}

fn companions(path: &Path) -> Vec<PathBuf> {
    let stem = path.file_stem().unwrap_or_default().to_string_lossy();
    match path.extension().and_then(|s| s.to_str()) {
        Some("smdesign") => [
            format!("{stem}.code"),
            format!("{stem}_init.code"),
            format!("{stem}_end.code"),
        ]
        .into_iter()
        .map(|name| path.with_file_name(name))
        .filter(|p| p.is_file())
        .collect(),
        Some("smui") => vec![path.with_file_name(format!("{stem}_ui.code"))]
            .into_iter()
            .filter(|p| p.is_file())
            .collect(),
        _ => vec![],
    }
}

pub(crate) fn perform(root: &Path, operation: TreeOperation) -> Result<TreeOutcome, ServiceError> {
    let project = Project::new(root.to_owned(), vec![]);
    match operation {
        TreeOperation::CreateIk { name, kind } => crate::ik::create(root, &name, &kind),
        TreeOperation::CreateMotion { name, kind } => crate::motion::create(root, &name, &kind),
        TreeOperation::CreateWeapon { name } => crate::weapon::create(root, &name),
        TreeOperation::AddScene { parent, name } => {
            let path = destination(&project, &parent, &name)?;
            let name = child_name(&name)?;
            let pending = io_result(
                &path,
                "Prepare scene",
                tempfile::Builder::new().prefix(".new-scene-").tempdir_in(
                    path.parent()
                        .ok_or(ServiceError::Limit("No parent directory"))?,
                ),
            )?;
            for (file, source) in [
                (
                    format!("{name}.smdesign"),
                    "{\"version\":1,\"entities\":[]}\n".to_owned(),
                ),
                (format!("{name}.code"), String::new()),
                (format!("{name}_init.code"), String::new()),
                (format!("{name}_end.code"), String::new()),
                ("main".into(), format!("add \"{name}.code\" code\n")),
            ] {
                io_result(
                    &path,
                    "Write scene files",
                    fs::write(pending.path().join(file), source),
                )?;
            }
            io_result(&path, "Create scene", fs::rename(pending.path(), &path))?;
            Ok(TreeOutcome {
                paths: vec![],
                document: Some(Filesystem::open_document(
                    &project,
                    &path.join(format!("{name}.smdesign")),
                )?),
            })
        }
        TreeOperation::Create {
            parent,
            name,
            source,
        } => {
            let path = destination(&project, &parent, &name)?;
            let document = if let Some(source) = source {
                let mut pending = io_result(
                    &path,
                    "Create document",
                    tempfile::NamedTempFile::new_in(&parent),
                )?;
                io_result(
                    &path,
                    "Write document",
                    pending.write_all(source.as_bytes()),
                )?;
                io_result(&path, "Flush document", pending.as_file().sync_all())?;
                pending
                    .persist_noclobber(&path)
                    .map_err(|e| ServiceError::Io {
                        operation: "Create document",
                        path: path.clone(),
                        source: e.error,
                    })?;
                Some(Filesystem::open_document(&project, &path)?)
            } else {
                io_result(&path, "Create folder", fs::create_dir(&path))?;
                None
            };
            Ok(TreeOutcome {
                paths: vec![],
                document,
            })
        }
        TreeOperation::Move { path, parent, name } => {
            let path = Filesystem::resolve(&project, &path)?;
            if path == root {
                return Err(ServiceError::Limit(
                    "The project root cannot be renamed here",
                ));
            }
            let target = destination(&project, &parent, &name)?;
            if target.starts_with(&path) {
                return Err(ServiceError::Limit("A folder cannot be moved into itself"));
            }
            io_result(&path, "Move item", fs::rename(&path, &target))?;
            Ok(TreeOutcome {
                paths: vec![(path, Some(target))],
                document: None,
            })
        }
        TreeOperation::Delete(path) => {
            let path = Filesystem::resolve(&project, &path)?;
            if path == root || path.file_name().is_some_and(|n| n == "main") {
                return Err(ServiceError::Limit(
                    "The project root and main entry point cannot be deleted",
                ));
            }
            let studio = root.join(".scenemax-studio");
            if !studio.exists() {
                io_result(&studio, "Create studio data", fs::create_dir(&studio))?;
            }
            let studio = Filesystem::resolve(&project, &studio)?;
            let trash = io_result(
                &studio,
                "Create trash",
                tempfile::Builder::new()
                    .prefix("deleted-")
                    .tempdir_in(&studio),
            )?
            .keep();
            let mut sources = vec![path.clone()];
            if path.is_file() {
                sources.extend(companions(&path));
            }
            let mut moved: Vec<(PathBuf, PathBuf)> = vec![];
            for source in sources {
                let target = trash.join(source.file_name().unwrap_or_default());
                if let Err(error) = io_result(
                    &source,
                    "Move to studio trash",
                    fs::rename(&source, &target),
                ) {
                    for (original, backup) in moved.iter().rev() {
                        let _ = fs::rename(backup, original);
                    }
                    return Err(error);
                }
                moved.push((source, target));
            }
            Ok(TreeOutcome {
                paths: moved.into_iter().map(|(p, _)| (p, None)).collect(),
                document: None,
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn add_scene_creates_a_complete_starter_without_overwriting() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let operation = TreeOperation::AddScene {
            parent: root.clone(),
            name: "intro".into(),
        };
        let result = perform(&root, operation.clone()).unwrap();
        assert!(
            result
                .document
                .unwrap()
                .path()
                .ends_with("intro/intro.smdesign")
        );
        assert_eq!(
            fs::read_to_string(root.join("intro/main")).unwrap(),
            "add \"intro.code\" code\n"
        );
        assert!(root.join("intro/intro_init.code").exists());
        assert!(perform(&root, operation).is_err());
    }
    #[test]
    fn names_cannot_escape_or_replace_existing_items() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        for name in ["../escape", "a/b", "a\\b", "C:escape", "", ".", "a:stream"] {
            assert!(
                perform(
                    &root,
                    TreeOperation::Create {
                        parent: root.clone(),
                        name: name.into(),
                        source: None
                    }
                )
                .is_err()
            );
        }
        fs::write(root.join("existing"), "keep").unwrap();
        assert!(
            perform(
                &root,
                TreeOperation::Create {
                    parent: root.clone(),
                    name: "existing".into(),
                    source: Some("replace".into())
                }
            )
            .is_err()
        );
        assert_eq!(fs::read_to_string(root.join("existing")).unwrap(), "keep");
    }
    #[test]
    fn delete_preserves_scene_companions_in_trash_and_protects_main() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        for name in ["scene.smdesign", "scene.code", "scene_init.code", "main"] {
            fs::write(root.join(name), name).unwrap();
        }
        let result = perform(&root, TreeOperation::Delete(root.join("scene.smdesign"))).unwrap();
        assert_eq!(result.paths.len(), 3);
        let trash = fs::read_dir(root.join(".scenemax-studio"))
            .unwrap()
            .next()
            .unwrap()
            .unwrap()
            .path();
        assert_eq!(
            fs::read_to_string(trash.join("scene_init.code")).unwrap(),
            "scene_init.code"
        );
        assert!(perform(&root, TreeOperation::Delete(root.join("main"))).is_err());
        assert!(perform(&root, TreeOperation::Delete(root.clone())).is_err());
    }
    #[test]
    fn move_is_confined_to_project_and_never_overwrites() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::write(root.join("source"), "source").unwrap();
        fs::write(root.join("target"), "target").unwrap();
        let operation = |parent, name: &str| TreeOperation::Move {
            path: root.join("source"),
            parent,
            name: name.into(),
        };
        assert!(perform(&root, operation(root.clone(), "target")).is_err());
        assert!(
            perform(
                &root,
                operation(root.parent().unwrap().to_owned(), "escaped")
            )
            .is_err()
        );
        perform(&root, operation(root.clone(), "renamed")).unwrap();
        assert_eq!(fs::read_to_string(root.join("renamed")).unwrap(), "source");
    }
}
