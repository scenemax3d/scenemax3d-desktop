//! Debounced project index lifecycle. Results are tied to exact source revisions.
use super::{EditorServices, Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, DocumentRevision, completion::Completion};
use scenemax_ide_services::{IndexReport, IndexRequest};
use std::{
    collections::{BTreeMap, BTreeSet},
    path::PathBuf,
    time::{Duration, Instant},
};
#[derive(Clone, PartialEq, Eq)]
struct Stamp {
    root: PathBuf,
    paths: Vec<PathBuf>,
    versions: Vec<(DocumentId, DocumentRevision, PathBuf)>,
    epoch: u64,
}
#[derive(Resource, Default)]
pub(crate) struct ProjectSymbols {
    desired: Option<Stamp>,
    pending: Option<Stamp>,
    published: Option<Stamp>,
    changed: Option<Instant>,
    report: Option<IndexReport>,
    epoch: u64,
    failed: bool,
    pub(crate) revision: u64,
}
impl ProjectSymbols {
    pub(crate) fn coverage(&self) -> String {
        if self.failed {
            return "Project suggestions unavailable".into();
        }
        self.report.as_ref().map_or_else(
            || "Indexing project…".into(),
            |r| {
                format!(
                    "{} files · {} skipped · {} unresolved includes{}",
                    r.files.len(),
                    r.skipped,
                    r.unresolved,
                    if r.truncated { " · limit reached" } else { "" }
                )
            },
        )
    }
    pub(crate) fn suggestions(&self, active: &std::path::Path) -> Vec<Completion> {
        self.report
            .as_ref()
            .map_or_else(Vec::new, |r| r.suggestions(active))
    }
    fn accept(&mut self, stamp: Stamp, report: IndexReport) -> bool {
        if self.desired.as_ref() != Some(&stamp) {
            return false;
        }
        self.published = Some(stamp);
        self.report = Some(report);
        self.revision += 1;
        true
    }
}
pub(crate) fn update(
    session: Res<Session>,
    mut services: ResMut<EditorServices>,
    mut index: ResMut<ProjectSymbols>,
    mut events: MessageReader<ViewChange>,
) {
    if events.read().any(|e| {
        matches!(
            e,
            ViewChange::ProjectOpened | ViewChange::ProjectIndexInvalidated
        )
    }) {
        index.epoch += 1;
    }
    let project = session.workspace.project();
    let versions: Vec<_> = session
        .workspace
        .documents()
        .map(|(id, d)| (id, d.revision(), d.path().to_owned()))
        .collect();
    let current = index.desired.as_ref().is_some_and(|s| {
        s.root == project.root()
            && s.paths == project.scripts()
            && s.versions == versions
            && s.epoch == index.epoch
    });
    if !current {
        index.desired = Some(Stamp {
            root: project.root().into(),
            paths: project.scripts().to_vec(),
            versions,
            epoch: index.epoch,
        });
        index.report = None;
        index.failed = false;
        index.revision += 1;
        index.changed = Some(Instant::now());
    }
    if services.symbols.is_pending() {
        match services.symbols.poll() {
            Ok(Some(report)) => {
                if let Some(stamp) = index.pending.take() {
                    index.accept(stamp, report);
                }
            }
            Err(_) => {
                index.pending = None;
                index.failed = true;
                index.revision += 1;
                index.published = index.desired.clone();
            }
            Ok(None) => {}
        }
    }
    if services.symbols.is_pending()
        || index.published == index.desired
        || index
            .changed
            .is_none_or(|t| t.elapsed() < Duration::from_millis(350))
    {
        return;
    }
    let mut buffers = BTreeMap::new();
    let mut excluded = BTreeSet::new();
    let mut bytes = 0;
    for (_, doc) in session.workspace.documents() {
        if !project.scripts().contains(&doc.path().to_owned()) {
            continue;
        }
        if doc.text().len() > 1024 * 1024 || bytes + doc.text().len() > 32 * 1024 * 1024 {
            excluded.insert(doc.path().to_owned());
            continue;
        }
        bytes += doc.text().len();
        buffers.insert(doc.path().to_owned(), doc.text().to_owned());
    }
    let request = IndexRequest {
        root: project.root().into(),
        paths: project.scripts().to_vec(),
        buffers,
        excluded,
    };
    if services.symbols.request(request).is_ok() {
        index.pending = index.desired.clone();
    } else {
        index.failed = true;
        index.revision += 1;
        index.published = index.desired.clone();
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn old_project_generation_cannot_publish_after_switch_or_refresh() {
        let mut index = ProjectSymbols::default();
        let old = Stamp {
            root: "project".into(),
            paths: vec![],
            versions: vec![],
            epoch: 1,
        };
        let mut new = old.clone();
        new.epoch = 2;
        index.desired = Some(new.clone());
        assert!(!index.accept(old, IndexReport::default()));
        assert!(index.report.is_none());
        assert!(index.accept(new, IndexReport::default()));
    }
}
