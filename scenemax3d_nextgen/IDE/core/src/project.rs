use std::path::{Path, PathBuf};
/// Project inventory loaded and validated by a storage service.
#[derive(Debug)]
pub struct Project {
    root: PathBuf,
    scripts: Vec<PathBuf>,
    entries: Vec<ProjectEntry>,
    tree_truncated: bool,
}
impl Project {
    /// Construct from canonical paths supplied by storage.
    pub fn new(root: PathBuf, mut scripts: Vec<PathBuf>) -> Self {
        scripts.sort();
        Self {
            root,
            scripts,
            entries: Vec::new(),
            tree_truncated: false,
        }
    }
    /// Attach the bounded, directory-first tree inventory supplied by storage.
    pub fn with_entries(mut self, entries: Vec<ProjectEntry>, truncated: bool) -> Self {
        self.entries = entries;
        self.tree_truncated = truncated;
        self
    }
    /// Files and directories in depth-first display order.
    pub fn entries(&self) -> &[ProjectEntry] {
        &self.entries
    }
    /// Whether the storage traversal reached its entry or depth bound.
    pub fn tree_truncated(&self) -> bool {
        self.tree_truncated
    }
    /// Java IDE project entry convention: the shallowest file named `main` under scripts/.
    /// Equal-depth candidates use a stable path order rather than filesystem enumeration order.
    pub fn entry_point(&self) -> Option<&Path> {
        self.scripts
            .iter()
            .filter(|p| p.file_name().is_some_and(|n| n == "main"))
            .min_by_key(|p| (p.components().count(), *p))
            .map(PathBuf::as_path)
    }
    /// Canonical project directory.
    pub fn root(&self) -> &Path {
        &self.root
    }
    /// Deterministically sorted source paths.
    pub fn scripts(&self) -> &[PathBuf] {
        &self.scripts
    }
}

/// A source location produced by a bounded project search.
#[derive(Debug, Clone)]
pub struct SearchHit {
    /// Canonical script path.
    pub path: PathBuf,
    /// One-based source line.
    pub line: usize,
    /// Short source preview, without modifying the document.
    pub preview: String,
}

/// A filesystem entry for the project navigator; contains no rendering state.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProjectEntry {
    /// Absolute path inside the project.
    pub path: PathBuf,
    /// Directories can be expanded; files can be opened.
    pub is_directory: bool,
}

/// A selectable project imported from the existing project catalog.
#[derive(Debug, Clone)]
pub struct ProjectSummary {
    /// User-facing project name.
    pub name: String,
    /// Absolute project directory, resolved relative to the catalog's installation root.
    pub root: PathBuf,
    /// Last activation time recorded by the catalog.
    pub last_active: u64,
}
/// Read-only view of the Java-compatible project catalog.
#[derive(Debug, Default)]
pub struct ProjectCatalog {
    /// Project choices in recent-first order.
    pub projects: Vec<ProjectSummary>,
    /// Previously selected project directory, if present in the catalog.
    pub selected: Option<PathBuf>,
}
#[cfg(test)]
mod project_tests {
    use super::*;
    #[test]
    fn project_entry_is_shallowest_main_not_the_active_script() {
        let p = Project::new(
            "project".into(),
            vec![
                "project/scripts/a/deep/main".into(),
                "project/scripts/z/main".into(),
                "project/scripts/a/file.code".into(),
            ],
        );
        assert_eq!(p.entry_point(), Some(Path::new("project/scripts/z/main")));
        assert!(
            Project::new("project".into(), vec![])
                .entry_point()
                .is_none()
        );
    }
}
