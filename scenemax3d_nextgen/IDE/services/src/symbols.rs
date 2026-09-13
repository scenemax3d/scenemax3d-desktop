//! Bounded project symbol indexing on a dedicated worker.
use crate::{Filesystem, ServiceError};
use scenemax_ide_core::{Project, completion::Completion};
use scenemax_parser::{AssignmentValue, Statement};
use std::{
    collections::{BTreeMap, BTreeSet},
    path::PathBuf,
    sync::{Mutex, mpsc},
    thread,
};

/// Immutable project inventory and unsaved source overrides.
pub struct IndexRequest {
    /// Canonical project root.
    pub root: PathBuf,
    /// Canonical script paths from the validated project inventory.
    pub paths: Vec<PathBuf>,
    /// Open buffers override disk, including invalid/incomplete source.
    pub buffers: BTreeMap<PathBuf, String>,
    /// Open buffers omitted because they exceed snapshot limits; never read stale disk versions.
    pub excluded: BTreeSet<PathBuf>,
}
/// Symbols belonging to one successfully parsed source file.
#[derive(Clone, Debug)]
pub struct FileSymbols {
    /// Declaration source, retained to exclude the active file from external suggestions.
    pub path: PathBuf,
    /// Top-level declarations only; function locals/parameters are not exported.
    pub items: Vec<Completion>,
    /// Validated direct Add Code targets, in source order.
    pub includes: Vec<PathBuf>,
}
/// A bounded index snapshot and coverage information.
#[derive(Default)]
pub struct IndexReport {
    /// Successfully indexed files.
    pub files: Vec<FileSymbols>,
    /// Unreadable, oversized or syntactically invalid sources.
    pub skipped: usize,
    /// A byte, file or symbol limit was reached.
    pub truncated: bool,
    /// Includes whose target was absent from the indexed inventory.
    pub unresolved: usize,
    /// Project main used for Java-compatible global visibility.
    pub entry: Option<PathBuf>,
}
impl IndexReport {
    /// Main/include declarations plus the active file's include closure.
    /// Iterative traversal is cycle safe and never performs filesystem access.
    pub fn suggestions(&self, active: &std::path::Path) -> Vec<Completion> {
        let files: BTreeMap<_, _> = self.files.iter().map(|f| (f.path.as_path(), f)).collect();
        let mut visited = BTreeSet::new();
        let mut pending = vec![active];
        if let Some(entry) = &self.entry {
            pending.push(entry.as_path());
        }
        let mut items = Vec::new();
        while let Some(path) = pending.pop() {
            if !visited.insert(path) {
                continue;
            }
            if let Some(file) = files.get(path) {
                if path != active {
                    items.extend(file.items.iter().cloned());
                }
                pending.extend(file.includes.iter().rev().map(PathBuf::as_path));
            }
        }
        items
    }
}
#[derive(Clone)]
struct ParsedSymbols {
    items: Vec<Completion>,
    includes: Vec<String>,
}
#[derive(Default)]
struct Cache(BTreeMap<PathBuf, (String, Option<ParsedSymbols>)>);
impl Cache {
    fn index(&mut self, request: IndexRequest) -> IndexReport {
        let project = Project::new(request.root, request.paths.clone());
        let mut report = IndexReport {
            entry: project.entry_point().map(std::path::Path::to_path_buf),
            ..Default::default()
        };
        let mut bytes = 0;
        let mut symbols = 0;
        let paths: BTreeSet<_> = request.paths.iter().take(2000).cloned().collect();
        self.0.retain(|path, _| paths.contains(path));
        report.truncated = request.paths.len() > 2000;
        let mut visited = BTreeSet::new();
        for path in paths.iter().cloned() {
            if request.excluded.contains(&path) {
                report.skipped += 1;
                continue;
            }
            // Buffers come from the validated inventory. Do not require a disk copy:
            // an open document can remain editable after an external deletion.
            if !path.starts_with(project.root())
                || path
                    .components()
                    .any(|c| matches!(c, std::path::Component::ParentDir))
            {
                report.skipped += 1;
                continue;
            }
            let disk;
            let source = if let Some(buffer) = request.buffers.get(&path) {
                buffer.as_str()
            } else {
                disk = match Filesystem::open_document(&project, &path) {
                    Ok(doc) => doc,
                    Err(_) => {
                        report.skipped += 1;
                        continue;
                    }
                };
                disk.text()
            };
            if source.len() > 1024 * 1024 {
                self.0.remove(&path);
                report.skipped += 1;
                continue;
            }
            bytes += source.len();
            if bytes > 32 * 1024 * 1024 {
                report.truncated = true;
                break;
            }
            visited.insert(path.clone());
            if self.0.get(&path).is_none_or(|(old, _)| old != source) {
                let items =
                    scenemax_parser::parse_program(source)
                        .ok()
                        .map(|program| ParsedSymbols {
                            items: program.statements.iter().filter_map(symbol).collect(),
                            includes: program
                                .statements
                                .iter()
                                .filter_map(|s| match s {
                                    Statement::AddCode { path } => Some(path.clone()),
                                    _ => None,
                                })
                                .collect(),
                        });
                self.0.insert(path.clone(), (source.into(), items));
            }
            if let Some((_, Some(parsed))) = self.0.get(&path) {
                if symbols + parsed.items.len() > 20_000 {
                    report.truncated = true;
                    break;
                }
                symbols += parsed.items.len();
                let includes = parsed
                    .includes
                    .iter()
                    .filter_map(|include| {
                        let target = resolve_include(&path, include, &paths);
                        if target.is_none() {
                            report.unresolved += 1;
                        }
                        target
                    })
                    .collect();
                report.files.push(FileSymbols {
                    path,
                    items: parsed.items.clone(),
                    includes,
                });
            } else {
                report.skipped += 1;
            }
        }
        // Retain only this bounded scan's cache, never historical project roots.
        self.0.retain(|path, _| visited.contains(path));
        report
    }
}
// Match runtime relative paths and .code fallback against the validated inventory.
// Normalize lexically: indexing must not follow new symlinks or scan outside it.
fn resolve_include(
    source: &std::path::Path,
    include: &str,
    paths: &BTreeSet<PathBuf>,
) -> Option<PathBuf> {
    use std::path::Component;
    let relative = include
        .trim_start_matches('/')
        .replace('/', std::path::MAIN_SEPARATOR_STR);
    let mut normalized = PathBuf::new();
    for component in source.parent()?.join(relative).components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                normalized.pop();
            }
            other => normalized.push(other.as_os_str()),
        }
    }
    paths
        .get(&normalized)
        .or_else(|| paths.get(&normalized.with_extension("code")))
        .cloned()
}
fn symbol(statement: &Statement) -> Option<Completion> {
    let (name, category) = match statement {
        Statement::FunctionDef(function) => (&function.name, "Project function"),
        Statement::ModelDecl { name, .. } => (name, "Project 3D object"),
        Statement::LightDecl(light) => (&light.name, "Project light"),
        Statement::Assignment(a)
        | Statement::SharedAssignment(a)
        | Statement::LocalAssignment(a) => (
            &a.name,
            if matches!(a.value, AssignmentValue::Number(_)) {
                "Project number"
            } else {
                "Project variable"
            },
        ),
        _ => return None,
    };
    if name.len() > 256 {
        return None;
    }
    Some(Completion {
        label: name.clone(),
        insert: name.clone(),
        category,
    })
}
/// One outstanding index request; expensive reads/parsing never run on the UI thread.
pub struct SymbolIndexer {
    requests: mpsc::SyncSender<IndexRequest>,
    results: Mutex<mpsc::Receiver<IndexReport>>,
    pending: bool,
}
impl SymbolIndexer {
    /// Start the owned indexing worker.
    pub fn new() -> Result<Self, ServiceError> {
        let (requests, incoming) = mpsc::sync_channel::<IndexRequest>(1);
        let (outgoing, results) = mpsc::sync_channel(1);
        thread::Builder::new()
            .name("ide-symbols".into())
            .stack_size(8 * 1024 * 1024)
            .spawn(move || {
                let mut cache = Cache::default();
                while let Ok(request) = incoming.recv() {
                    if outgoing.send(cache.index(request)).is_err() {
                        break;
                    }
                }
            })?;
        Ok(Self {
            requests,
            results: Mutex::new(results),
            pending: false,
        })
    }
    /// Whether a scan or its unconsumed result owns the worker slot.
    pub fn is_pending(&self) -> bool {
        self.pending
    }
    /// Submit without waiting or accumulating requests.
    pub fn request(&mut self, request: IndexRequest) -> Result<(), ServiceError> {
        if self.pending {
            return Err(ServiceError::Busy("Symbol index"));
        }
        self.requests
            .try_send(request)
            .map_err(|_| ServiceError::WorkerUnavailable)?;
        self.pending = true;
        Ok(())
    }
    /// Take a completed scan; the caller must validate its request snapshot.
    pub fn poll(&mut self) -> Result<Option<IndexReport>, ServiceError> {
        if !self.pending {
            return Ok(None);
        }
        match self
            .results
            .get_mut()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .try_recv()
        {
            Ok(report) => {
                self.pending = false;
                Ok(Some(report))
            }
            Err(mpsc::TryRecvError::Empty) => Ok(None),
            Err(mpsc::TryRecvError::Disconnected) => {
                self.pending = false;
                Err(ServiceError::WorkerUnavailable)
            }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn include_visibility_handles_cycles_relative_paths_fallback_and_unsaved_edges() {
        let dir = tempfile::tempdir().unwrap();
        let scripts = dir.path().join("scripts");
        std::fs::create_dir_all(scripts.join("nested")).unwrap();
        for (path, source) in [
            ("main", "add \"nested/start\" code\nvar root_value=1"),
            (
                "nested/start.code",
                "add \"../library\" code\nvar nested_value=1",
            ),
            ("library.code", "add \"main\" code\nvar library_value=1"),
            ("unrelated.code", "var unrelated_value=1"),
            ("editor.code", "add \"missing\" code\nvar editor_value=1"),
        ] {
            std::fs::write(scripts.join(path), source).unwrap();
        }
        let project = Filesystem::open_project(dir.path()).unwrap();
        let active = scripts.join("editor.code").canonicalize().unwrap();
        let mut cache = Cache::default();
        let request = |buffers| IndexRequest {
            root: project.root().into(),
            paths: project.scripts().to_vec(),
            buffers,
            excluded: BTreeSet::new(),
        };
        let report = cache.index(request(BTreeMap::new()));
        let names: BTreeSet<_> = report
            .suggestions(&active)
            .into_iter()
            .map(|s| s.label)
            .collect();
        assert_eq!(
            names,
            BTreeSet::from([
                "root_value".into(),
                "nested_value".into(),
                "library_value".into()
            ])
        );
        assert_eq!(report.unresolved, 1);
        let main = project.entry_point().unwrap().to_path_buf();
        let report = cache.index(request(BTreeMap::from([
            (main, "var newer_root=2".into()),
            (active.clone(), "add \"/unrelated\" code".into()),
        ])));
        let names: BTreeSet<_> = report
            .suggestions(&active)
            .into_iter()
            .map(|s| s.label)
            .collect();
        assert_eq!(
            names,
            BTreeSet::from(["newer_root".into(), "unrelated_value".into()])
        );
        assert_eq!(report.unresolved, 0);
    }
    #[test]
    fn include_resolution_uses_only_inventory_and_prefers_exact_file() {
        let root = std::env::current_dir().unwrap().join("fixture");
        let source = root.join("nested/main");
        let paths = BTreeSet::from([root.join("library"), root.join("library.code")]);
        assert_eq!(
            resolve_include(&source, "../library", &paths),
            Some(root.join("library"))
        );
        assert!(resolve_include(&source, "../../outside", &paths).is_none());
        assert!(resolve_include(&source, "missing", &paths).is_none());
    }
    #[test]
    fn unsaved_source_replaces_disk_and_failed_parse_never_reuses_old_symbols() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        std::fs::write(&path, "var disk_value=1").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let mut cache = Cache::default();
        let request = |source: &str| IndexRequest {
            root: project.root().into(),
            paths: project.scripts().to_vec(),
            buffers: BTreeMap::from([(project.scripts()[0].clone(), source.into())]),
            excluded: BTreeSet::new(),
        };
        let report = cache.index(request("var unsaved_value=2"));
        assert!(
            report.files[0]
                .items
                .iter()
                .any(|s| s.label == "unsaved_value")
        );
        assert!(
            !report.files[0]
                .items
                .iter()
                .any(|s| s.label == "disk_value")
        );
        let report = cache.index(request("controller.event(clip, ???)"));
        assert!(report.files.is_empty());
        assert_eq!(report.skipped, 1);
        std::fs::remove_file(&path).unwrap();
        let report = cache.index(request("var replacement=3"));
        assert_eq!(report.files[0].items[0].label, "replacement");
    }
    #[test]
    fn nested_function_parameters_and_locals_are_not_exported() {
        let function = Statement::FunctionDef(scenemax_parser::FunctionDefStatement {
            name: "helper".into(),
            params: vec!["parameter".into()],
            guard: None,
            guard_recheck: false,
            actions: vec![Statement::LocalAssignment(
                scenemax_parser::AssignmentStatement {
                    name: "private_value".into(),
                    value: AssignmentValue::Number(1.),
                },
            )],
        });
        let result = symbol(&function).unwrap();
        assert_eq!(result.label, "helper");
        assert_eq!(result.category, "Project function");
    }
}
