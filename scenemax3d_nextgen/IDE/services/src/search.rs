use crate::{Filesystem, ServiceError};
use scenemax_ide_core::{Document, Project, SearchHit};
use std::path::PathBuf;
/// Bounded project search results from one disk/buffer snapshot.
pub struct SearchReport {
    /// At most 100 matching source lines.
    pub hits: Vec<SearchHit>,
    /// Number of files successfully searched.
    pub scanned: usize,
    /// Number of unreadable or oversized files skipped.
    pub skipped: usize,
    /// A result or byte limit stopped the scan early.
    pub truncated: bool,
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
    let mut bytes = 0;
    for path in paths {
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
        bytes += document.text().len();
        if bytes > 64 * 1024 * 1024 {
            report.truncated = true;
            break;
        }
        report.scanned += 1;
        for (index, line) in document.text().lines().enumerate() {
            if line.contains(needle) {
                if report.hits.len() == 100 {
                    report.truncated = true;
                    return Ok(report);
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
