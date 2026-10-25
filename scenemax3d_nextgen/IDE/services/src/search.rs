use crate::{Filesystem, ServiceError};
use scenemax_ide_core::{Document, Project, SearchHit};
use std::path::PathBuf;
/// Bounded project search results from one disk/buffer snapshot.
pub struct SearchReport {
    /// At most 5000 matching source lines.
    pub hits: Vec<SearchHit>,
    /// Number of files successfully searched.
    pub scanned: usize,
    /// Number of unreadable or oversized files skipped.
    pub skipped: usize,
    /// Some matches or inventory entries could not be included.
    pub truncated: bool,
}
/// Refresh the inventory so new files and text outside scripts/ participate.
pub(crate) fn project_search(
    root: PathBuf,
    needle: &str,
    buffers: Vec<Document>,
) -> Result<SearchReport, ServiceError> {
    let project = Filesystem::open_project(&root)?;
    let mut paths: Vec<_> = project
        .entries()
        .iter()
        .filter(|entry| !entry.is_directory)
        .map(|entry| entry.path.clone())
        .collect();
    paths.extend(buffers.iter().map(|doc| doc.path().to_owned()));
    paths.sort();
    paths.dedup();
    let mut report = search(project.root().to_owned(), paths, needle, buffers)?;
    report.truncated |= project.tree_truncated();
    Ok(report)
}
pub(crate) fn search(
    root: PathBuf,
    paths: Vec<PathBuf>,
    needle: &str,
    buffers: Vec<Document>,
) -> Result<SearchReport, ServiceError> {
    if needle.is_empty() {
        return Err(ServiceError::Limit("Enter text to find in the project"));
    }
    let project = Project::new(root, vec![]);
    let mut report = SearchReport {
        hits: vec![],
        scanned: 0,
        skipped: 0,
        truncated: false,
    };
    for path in paths {
        if !path.starts_with(project.root()) {
            report.skipped += 1;
            continue;
        }
        let loaded;
        let document = if let Some(doc) = buffers.iter().find(|doc| doc.path() == path) {
            doc
        } else {
            match Filesystem::open_document(&project, &path) {
                Ok(doc) => {
                    loaded = doc;
                    &loaded
                }
                Err(_) => {
                    report.skipped += 1;
                    continue;
                }
            }
        };
        if document.text().contains('\0') {
            report.skipped += 1;
            continue;
        }
        report.scanned += 1;
        for (index, line) in document.text().lines().enumerate() {
            if line.contains(needle) {
                if report.hits.len() == 5000 {
                    report.truncated = true;
                    continue;
                }
                report.hits.push(SearchHit {
                    path: path.clone(),
                    line: index + 1,
                    preview: line.trim().chars().take(100).collect(),
                });
            }
        }
    }
    Ok(report)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn project_search_refreshes_all_text_files_and_does_not_stop_at_100_lines() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir_all(dir.path().join("scripts/nested")).unwrap();
        std::fs::write(dir.path().join("scripts/main"), "needle\n".repeat(150)).unwrap();
        let old_inventory = Filesystem::open_project(dir.path()).unwrap();
        std::fs::write(dir.path().join("scripts/nested/new.code"), "needle").unwrap();
        std::fs::create_dir(dir.path().join("resources")).unwrap();
        std::fs::write(
            dir.path().join("resources/config.json"),
            "{\"value\":\"needle\"}",
        )
        .unwrap();
        std::fs::write(dir.path().join("binary.bin"), b"needle\0").unwrap();
        let report = project_search(old_inventory.root().to_owned(), "needle", vec![]).unwrap();
        assert_eq!(report.hits.len(), 152);
        assert_eq!(report.scanned, 3);
        assert_eq!(report.skipped, 1);
        assert!(!report.truncated);
        assert!(report.hits.iter().any(|hit| hit.path.ends_with("new.code")));
        assert!(
            report
                .hits
                .iter()
                .any(|hit| hit.path.ends_with("config.json"))
        );
    }

    #[test]
    fn result_limit_does_not_prevent_scanning_later_files() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join("a.txt"), "needle\n".repeat(5001)).unwrap();
        std::fs::write(dir.path().join("z.txt"), "needle").unwrap();
        let report = project_search(dir.path().to_owned(), "needle", vec![]).unwrap();
        assert_eq!(report.hits.len(), 5000);
        assert_eq!(report.scanned, 2);
        assert!(report.truncated);
    }
    #[test]
    fn project_search_uses_unsaved_buffers_and_reports_source_lines() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        std::fs::write(&path, "disk").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let mut doc = Filesystem::open_document(&project, &path).unwrap();
        doc.replace_text("first\nfind שלום".into());
        let report = search(
            project.root().to_owned(),
            project.scripts().to_vec(),
            "שלום",
            vec![doc],
        )
        .unwrap();
        assert_eq!(report.hits.len(), 1);
        assert_eq!(report.hits[0].line, 2);
        assert_eq!(report.scanned, 1);
    }
}
