use crate::{ServiceError, io_result};
use scenemax_ide_core::{Document, Project, ProjectEntry};
use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
};

/// Initial maximum loaded source size; large-document virtualization is separate work.
pub const MAX_DOCUMENT_BYTES: u64 = 8 * 1024 * 1024;
/// Maximum inventory size in the synchronous initial adapter.
pub const MAX_SCRIPTS: usize = 2000;

/// Local disk adapter. All filesystem access is centralized here.
pub struct Filesystem;
impl Filesystem {
    /// Load source inventory without traversing symlinks or Windows junctions.
    pub fn open_project(root: &Path) -> Result<Project, ServiceError> {
        let root = io_result(root, "Open project", root.canonicalize())?;
        if !root.is_dir() {
            return Err(ServiceError::NotDirectory(root));
        }
        let mut scripts = Vec::new();
        let script_root = root.join("scripts");
        if script_root.is_dir() {
            let script_root =
                io_result(&script_root, "Resolve scripts", script_root.canonicalize())?;
            if !script_root.starts_with(&root) {
                return Err(ServiceError::OutsideProject(script_root));
            }
            scan(&script_root, &mut scripts, 0)?;
        }
        let mut entries = Vec::new();
        let mut truncated = false;
        scan_tree(&root, &mut entries, 0, &mut truncated)?;
        Ok(Project::new(root, scripts).with_entries(entries, truncated))
    }
    /// Create a new project directory and a minimal script entry point. Never overwrite a project.
    pub fn create_project(root: &Path) -> Result<(Project, Document), ServiceError> {
        io_result(root, "Create project", fs::create_dir(root))?;
        io_result(root, "Create scripts", fs::create_dir(root.join("scripts")))?;
        let resources = root.join("resources");
        io_result(&resources, "Create resources", fs::create_dir(&resources))?;
        io_result(
            &resources,
            "Keep resources directory in version control",
            fs::write(resources.join(".gitkeep"), b""),
        )?;
        io_result(
            root,
            "Create project ignore rules",
            fs::write(
                root.join(".gitignore"),
                b".scenemax-studio/\nscenemax-nextgen-runtime.log\n",
            ),
        )?;
        let project = Self::open_project(root)?;
        let document = Self::create_script(
            &project,
            Path::new("main"),
            b"// SceneMax project entry point\nsys.print \"Project ready\"\n",
        )?;
        Ok((Self::open_project(root)?, document))
    }
    /// Create a new script under scripts/, refusing traversal and existing files.
    pub fn create_document(project: &Project, relative: &Path) -> Result<Document, ServiceError> {
        Self::create_script(project, relative, &[])
    }
    /// Save a buffer as a new script, without overwriting an existing path or changing its source buffer.
    pub fn save_copy(
        project: &Project,
        relative: &Path,
        document: &Document,
    ) -> Result<Document, ServiceError> {
        Self::create_script(project, relative, &document.encoded_bytes())
    }
    fn create_script(
        project: &Project,
        relative: &Path,
        bytes: &[u8],
    ) -> Result<Document, ServiceError> {
        use std::path::Component;
        if relative.as_os_str().is_empty()
            || relative
                .components()
                .any(|c| !matches!(c, Component::Normal(_)))
        {
            return Err(ServiceError::Limit(
                "Enter a relative script name without '..' or absolute paths",
            ));
        }
        if relative
            .extension()
            .is_some_and(|e| !e.eq_ignore_ascii_case("code"))
        {
            return Err(ServiceError::Limit(
                "Scripts must use .code or have no extension",
            ));
        }
        if relative
            .file_name()
            .is_some_and(|n| n.to_string_lossy().starts_with('.'))
        {
            return Err(ServiceError::Limit("Script names cannot begin with a dot"));
        }
        let scripts = project.root().join("scripts");
        if !scripts.exists() {
            io_result(&scripts, "Create scripts", fs::create_dir(&scripts))?;
        }
        let scripts = Self::resolve(project, &scripts)?;
        let path = scripts.join(relative);
        let parent = path
            .parent()
            .ok_or_else(|| ServiceError::OutsideProject(path.clone()))?;
        let parent = Self::resolve(project, parent)?;
        if !parent.starts_with(&scripts) {
            return Err(ServiceError::OutsideProject(parent));
        }
        let path = parent.join(
            path.file_name()
                .ok_or(ServiceError::Limit("Enter a file name"))?,
        );
        let mut pending = io_result(
            &path,
            "Create script",
            tempfile::NamedTempFile::new_in(&parent),
        )?;
        io_result(&path, "Write new script", pending.write_all(bytes))?;
        io_result(&path, "Flush new script", pending.as_file().sync_all())?;
        pending
            .persist_noclobber(&path)
            .map_err(|error| ServiceError::Io {
                operation: "Create script (existing files are preserved)",
                path: path.clone(),
                source: error.error,
            })?;
        Self::open_document(project, &path)
    }
    /// Canonicalize and validate an existing path inside the selected project.
    pub fn resolve(project: &Project, path: &Path) -> Result<PathBuf, ServiceError> {
        let path = if path.is_absolute() {
            path.to_owned()
        } else {
            project.root().join(path)
        };
        let path = io_result(&path, "Resolve file", path.canonicalize())?;
        if !path.starts_with(project.root()) {
            return Err(ServiceError::OutsideProject(path));
        }
        Ok(path)
    }
    /// Load a UTF-8 document with a bounded read, including concurrent file growth.
    pub fn open_document(project: &Project, path: &Path) -> Result<Document, ServiceError> {
        let path = Self::resolve(project, path)?;
        let file = io_result(&path, "Open file", fs::File::open(&path))?;
        let mut bytes = Vec::new();
        io_result(
            &path,
            "Read file",
            file.take(MAX_DOCUMENT_BYTES + 1).read_to_end(&mut bytes),
        )?;
        if bytes.len() as u64 > MAX_DOCUMENT_BYTES {
            return Err(ServiceError::Limit(
                "Document exceeds the 8 MiB editor limit",
            ));
        }
        Ok(Document::from_bytes(path, bytes)?)
    }
    /// Save with conflict detection and same-directory atomic file replacement.
    /// The buffer becomes clean only after successful replacement.
    pub fn save_document(doc: &mut Document) -> Result<(), ServiceError> {
        if !doc.is_dirty() {
            return Ok(());
        }
        let path = doc.path();
        let original = io_result(path, "Open original before save", fs::File::open(path))?;
        let mut current = Vec::new();
        io_result(
            path,
            "Read original before save",
            original
                .take(doc.saved_bytes().len() as u64 + 1)
                .read_to_end(&mut current),
        )?;
        if current != doc.saved_bytes() {
            return Err(ServiceError::ExternalChange(path.to_owned()));
        }
        let permissions =
            io_result(path, "Read file permissions", fs::metadata(path))?.permissions();
        if permissions.readonly() {
            return Err(ServiceError::ReadOnly(path.to_owned()));
        }
        let parent = path
            .parent()
            .ok_or_else(|| ServiceError::OutsideProject(path.to_owned()))?;
        let mut pending = io_result(
            path,
            "Create save file",
            tempfile::NamedTempFile::new_in(parent),
        )?;
        io_result(
            path,
            "Write save file",
            pending.write_all(&doc.encoded_bytes()),
        )?;
        io_result(
            path,
            "Preserve permissions",
            pending.as_file().set_permissions(permissions),
        )?;
        io_result(path, "Flush save file", pending.as_file().sync_all())?;
        pending.persist(path).map_err(|error| ServiceError::Io {
            operation: "Replace file",
            path: path.to_owned(),
            source: error.error,
        })?;
        doc.mark_saved();
        Ok(())
    }
}

fn scan(dir: &Path, files: &mut Vec<PathBuf>, depth: usize) -> Result<(), ServiceError> {
    if depth > 32 {
        return Err(ServiceError::Limit(
            "Script folder nesting exceeds 32 levels",
        ));
    }
    for entry in io_result(dir, "List scripts", fs::read_dir(dir))? {
        let entry = io_result(dir, "Read directory entry", entry)?;
        let path = entry.path();
        let kind = io_result(&path, "Read file type", entry.file_type())?;
        if kind.is_symlink() {
            continue;
        }
        #[cfg(windows)]
        {
            use std::os::windows::fs::MetadataExt;
            if io_result(&path, "Read file attributes", entry.metadata())?.file_attributes() & 0x400
                != 0
            {
                continue;
            }
        }
        if kind.is_dir() {
            scan(&path, files, depth + 1)?;
        } else if kind.is_file()
            && (path
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("code"))
                || (path.extension().is_none()
                    && !entry.file_name().to_string_lossy().starts_with('.')))
        {
            if files.len() >= MAX_SCRIPTS {
                return Err(ServiceError::Limit(
                    "Project exceeds the initial limit of 2000 scripts",
                ));
            }
            files.push(path);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture(bytes: &[u8]) -> (tempfile::TempDir, Project, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/test.code");
        fs::write(&path, bytes).unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        (dir, project, path)
    }
    #[test]
    fn saves_unicode_preserving_bom_and_crlf() {
        let (_dir, project, path) = fixture("\u{feff}// hello\r\n".as_bytes());
        let mut doc = Filesystem::open_document(&project, &path).unwrap();
        doc.replace_text(format!("{}// שלום 🦀\n", doc.text()));
        Filesystem::save_document(&mut doc).unwrap();
        assert!(!doc.is_dirty());
        assert_eq!(
            fs::read_to_string(path).unwrap(),
            "\u{feff}// hello\r\n// שלום 🦀\r\n"
        );
    }
    #[test]
    fn refuses_external_changes_and_preserves_dirty_buffer() {
        let (_dir, project, path) = fixture(b"original");
        let mut doc = Filesystem::open_document(&project, &path).unwrap();
        doc.replace_text("mine".into());
        fs::write(&path, "external").unwrap();
        assert!(matches!(
            Filesystem::save_document(&mut doc),
            Err(ServiceError::ExternalChange(_))
        ));
        assert!(doc.is_dirty());
        assert_eq!(fs::read_to_string(path).unwrap(), "external");
    }
    #[test]
    fn clean_save_preserves_mixed_line_endings() {
        let (_dir, project, path) = fixture(b"a\r\nb\n");
        Filesystem::save_document(&mut Filesystem::open_document(&project, &path).unwrap())
            .unwrap();
        assert_eq!(fs::read(path).unwrap(), b"a\r\nb\n");
    }
    #[test]
    fn refuses_outside_files() {
        let (_dir, project, _) = fixture(b"test");
        let outside = tempfile::NamedTempFile::new().unwrap();
        assert!(matches!(
            Filesystem::open_document(&project, outside.path()),
            Err(ServiceError::OutsideProject(_))
        ));
    }
    #[test]
    fn refuses_invalid_utf8() {
        let (_dir, project, path) = fixture(&[0xff]);
        assert!(Filesystem::open_document(&project, &path).is_err());
    }
    #[test]
    fn discovers_legacy_scripts_but_not_designer_documents() {
        let (dir, _, _) = fixture(b"// code");
        fs::write(dir.path().join("scripts/main"), "// entry").unwrap();
        fs::write(dir.path().join("scripts/scene.smdesign"), "{}").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        assert_eq!(project.scripts().len(), 2);
    }
    #[test]
    fn refuses_large_source_without_loading_it_all() {
        let (_dir, project, path) = fixture(b"");
        fs::File::options()
            .write(true)
            .open(&path)
            .unwrap()
            .set_len(MAX_DOCUMENT_BYTES + 1)
            .unwrap();
        assert!(matches!(
            Filesystem::open_document(&project, &path),
            Err(ServiceError::Limit(_))
        ));
    }
}

#[cfg(test)]
mod creation_tests {
    use super::*;
    #[test]
    fn creating_scripts_never_overwrites_and_rejects_traversal() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().join("new project");
        let (project, doc) = Filesystem::create_project(&root).unwrap();
        assert_eq!(doc.path().file_name().unwrap(), "main");
        assert!(project.root().join("resources/.gitkeep").is_file());
        assert!(doc.text().contains("sys.print"));
        assert!(Filesystem::create_document(&project, Path::new("main")).is_err());
        assert!(Filesystem::create_document(&project, Path::new("../outside.code")).is_err());
        assert!(Filesystem::create_document(&project, Path::new("scene.smdesign")).is_err());
        let new = Filesystem::create_document(&project, Path::new("שלום.code")).unwrap();
        assert_eq!(new.text(), "");
        assert_eq!(Filesystem::open_project(&root).unwrap().scripts().len(), 2);
    }
}

// Project navigation is bounded independently of the source inventory. Build output,
// VCS internals and recovery journals never belong in the editable project tree.
fn scan_tree(
    dir: &Path,
    entries: &mut Vec<ProjectEntry>,
    depth: usize,
    truncated: &mut bool,
) -> Result<(), ServiceError> {
    if depth >= 32 || entries.len() >= 10_000 {
        *truncated = true;
        return Ok(());
    }
    let mut children = Vec::new();
    for item in io_result(dir, "Read project tree", fs::read_dir(dir))? {
        let item = io_result(dir, "Read project entry", item)?;
        let kind = io_result(&item.path(), "Read entry type", item.file_type())?;
        if kind.is_symlink() {
            continue;
        }
        #[cfg(windows)]
        {
            use std::os::windows::fs::MetadataExt;
            if io_result(&item.path(), "Read entry metadata", item.metadata())?.file_attributes()
                & 0x400
                != 0
            {
                continue;
            }
        }
        if kind.is_dir()
            && matches!(
                item.file_name().to_str(),
                Some(".git" | "target" | "node_modules" | ".scenemax-studio")
            )
        {
            continue;
        }
        if !kind.is_dir() && !kind.is_file() {
            continue;
        }
        children.push(ProjectEntry {
            path: item.path(),
            is_directory: kind.is_dir(),
        });
        if children.len() >= 10_000 {
            *truncated = true;
            break;
        }
    }
    children.sort_by_key(|entry| {
        (
            !entry.is_directory,
            entry
                .path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .to_lowercase(),
        )
    });
    for entry in children {
        if entries.len() >= 10_000 {
            *truncated = true;
            break;
        }
        let directory = entry.is_directory.then(|| entry.path.clone());
        entries.push(entry);
        if let Some(path) = directory {
            scan_tree(&path, entries, depth + 1, truncated)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tree_tests {
    use super::*;
    #[test]
    fn tree_includes_empty_folders_and_non_script_files_but_skips_build_state() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir_all(dir.path().join("resources/models")).unwrap();
        fs::create_dir_all(dir.path().join("target/ignored")).unwrap();
        fs::create_dir_all(dir.path().join(".scenemax-studio/recovery")).unwrap();
        fs::write(dir.path().join("README.md"), "project").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let entries = project.entries();
        assert_eq!(entries.len(), 3);
        assert!(entries[0].is_directory);
        assert!(
            entries
                .iter()
                .any(|e| e.path.ends_with("resources/models") && e.is_directory)
        );
        assert!(
            entries
                .iter()
                .any(|e| e.path.ends_with("README.md") && !e.is_directory)
        );
        assert!(!project.tree_truncated());
    }
}
