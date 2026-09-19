//! Owned build jobs. UI polling never touches the filesystem or waits for tools.
mod files;
mod assets;
mod pipeline;
mod process;
mod size_report;
#[cfg(test)]
mod tests;
use scenemax_ide_core::deployment::Settings;
use std::{
    collections::VecDeque,
    io::{self, Write},
    path::PathBuf,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    thread::JoinHandle,
};

/// An immutable build input tied to the project visible when the user pressed Build.
#[derive(Debug, Clone)]
pub struct Request {
    /// Canonical source project.
    pub project: PathBuf,
    /// Entry script inside this project.
    pub entry: PathBuf,
    /// Independent Rust workspace containing Projector/app and Projector/launcher.
    pub workspace: PathBuf,
    /// Non-secret form values.
    pub settings: Settings,
}
/// An operation submitted to the single packaging worker.
#[derive(Clone)]
pub enum Operation {
    /// Read saved form values without touching source documents.
    Load(PathBuf),
    /// Check settings, source containment and required build tools.
    Check(Request),
    /// Snapshot, build, package and optionally publish.
    Build(Request),
    /// Retry publishing previously completed artifacts without rebuilding them.
    Upload(Request, Vec<PathBuf>, PathBuf),
    /// Authenticate using Butler's own browser flow and credential store.
    Login(String),
    /// Open the native file/folder chooser, with a cancellable owned process.
    Browse(bool),
}
/// Snapshot of a job's current state, with bounded UI log retention.
#[derive(Clone, Default)]
pub struct Report {
    /// Monotonic change counter for retained UI updates.
    pub revision: u64,
    /// Completed phase percentage, not an estimated compiler completion percentage.
    pub progress: f32,
    /// Current stage or actionable error.
    pub status: String,
    /// Recent log chunks, capped at 24 KiB.
    pub logs: VecDeque<String>,
    /// True only after the worker exits its operation.
    pub finished: bool,
    /// True only for a successful operation.
    pub success: bool,
    /// Complete artifacts published locally, including builds whose upload failed.
    pub artifacts: Vec<PathBuf>,
    /// Persistent build log (bounded to 16 MiB).
    pub log_path: Option<PathBuf>,
    /// Human-readable staged content sizing analysis, retained even if a later build fails.
    pub size_report_path: Option<PathBuf>,
    /// Saved form returned by Load.
    pub settings: Option<Settings>,
    /// Optional path returned by a native chooser.
    pub picked: Option<PathBuf>,
}
struct Context {
    report: Arc<Mutex<Report>>,
    cancel: Arc<AtomicBool>,
}
impl Context {
    fn check(&self) -> io::Result<()> {
        if self.cancel.load(Ordering::Relaxed) {
            Err(io::Error::new(
                io::ErrorKind::Interrupted,
                "Cancelled; completed artifacts were preserved.",
            ))
        } else {
            Ok(())
        }
    }
    fn edit(&self, f: impl FnOnce(&mut Report)) {
        let mut r = self
            .report
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        f(&mut r);
        r.revision += 1;
    }
    fn stage(&self, progress: f32, status: impl Into<String>) -> io::Result<()> {
        self.check()?;
        let status = status.into();
        self.edit(|r| {
            r.progress = progress;
            r.status = status;
        });
        Ok(())
    }
    fn advance(&self, amount: f32, status: impl Into<String>) -> io::Result<()> {
        self.check()?;
        self.edit(|r| {
            r.progress = (r.progress + amount).min(99.);
            r.status = status.into();
        });
        Ok(())
    }
    fn log(&self, text: impl Into<String>) {
        let mut text = text.into();
        // Credentials belong exclusively to Butler's environment or credential store.
        if let Ok(secret) = std::env::var("BUTLER_API_KEY")
            && !secret.is_empty()
        {
            text = text.replace(&secret, "[redacted]");
        }
        self.edit(|r| {
            r.logs.push_back(text);
            while r.logs.iter().map(String::len).sum::<usize>() > 24 * 1024 {
                r.logs.pop_front();
            }
        });
    }
}
/// Owns one cancellable job and joins its worker on destruction.
#[derive(Default)]
pub struct Builder {
    job: Option<Job>,
}
struct Job {
    context: Context,
    thread: Option<JoinHandle<()>>,
}
impl Builder {
    /// Whether a result is still being produced.
    pub fn busy(&self) -> bool {
        self.job.as_ref().is_some_and(|j| {
            !j.context
                .report
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .finished
        })
    }
    /// Submit one operation; repeated clicks cannot queue duplicate builds.
    pub fn start(&mut self, operation: Operation) -> io::Result<()> {
        if self.busy() {
            return Err(io::Error::other(
                "A deployment operation is already running.",
            ));
        }
        let previous = if matches!(operation, Operation::Build(_) | Operation::Load(_)) {
            None
        } else {
            self.report()
        };
        if let Some(mut job) = self.job.take()
            && let Some(thread) = job.thread.take()
        {
            let _ = thread.join();
        }
        let report = Arc::new(Mutex::new(Report {
            status: "Starting…".into(),
            artifacts: previous
                .as_ref()
                .map(|r| r.artifacts.clone())
                .unwrap_or_default(),
            size_report_path: previous.as_ref().and_then(|r| r.size_report_path.clone()),
            log_path: previous.and_then(|r| r.log_path),
            ..Default::default()
        }));
        let cancel = Arc::new(AtomicBool::new(false));
        let worker = Context {
            report: report.clone(),
            cancel: cancel.clone(),
        };
        let persist_summary = matches!(operation, Operation::Build(_) | Operation::Upload(_, _, _));
        let thread = std::thread::Builder::new().name("scenemax-deployment".into()).spawn(move || {
                let result = match operation {
                Operation::Load(root) => files::load_settings(&root).map(|settings| worker.edit(|r| {r.settings = Some(settings); r.status = "Choose platforms, then check requirements or build.".into(); })),
                Operation::Check(request) => pipeline::check(&request, &worker).map(|_| worker.edit(|r| r.status = "Requirements checked. Compiler and SDK diagnostics appear during Build.".into())),
                Operation::Build(request) => pipeline::build(&request, &worker),
                Operation::Upload(request, artifacts, log) => pipeline::upload(&request, &artifacts, &worker, &log),
                Operation::Login(butler) => process::login(&butler, &worker),
                Operation::Browse(folder) => process::browse(folder, &worker).map(|path| worker.edit(|r| { r.picked = path; r.status = "Path selection complete.".into(); })),
                };
                if persist_summary {
                    let (path, status, activity) = { let r = worker.report.lock().unwrap_or_else(std::sync::PoisonError::into_inner); (r.log_path.clone(), result.as_ref().err().map(ToString::to_string).unwrap_or_else(|| r.status.clone()), r.logs.iter().cloned().collect::<String>()) };
                    if let Some(path) = path && let Ok(mut file) = std::fs::File::options().append(true).open(path) && file.metadata().is_ok_and(|m| m.len() < 16 * 1024 * 1024 - 32768) {
                        let mut summary = status;
                        if let Ok(secret) = std::env::var("BUTLER_API_KEY") && !secret.is_empty() { summary = summary.replace(&secret, "[redacted]"); }
                        let _ = writeln!(file, "\n[deployment recent activity]\n{activity}\n[deployment] {summary}");
                    }
                }
            worker.edit(|r| { r.finished = true; r.success = result.is_ok(); if let Err(error) = result { r.status = error.to_string(); } else { r.progress = 100.; } });
        })?;
        self.job = Some(Job {
            context: Context { report, cancel },
            thread: Some(thread),
        });
        Ok(())
    }
    /// Cancel this job and its owned tool process tree, preserving prior output.
    pub fn cancel(&self) {
        if let Some(job) = &self.job {
            job.context.cancel.store(true, Ordering::Relaxed);
        }
    }
    /// Read a bounded, cheap presentation snapshot.
    pub fn report(&self) -> Option<Report> {
        self.job.as_ref().map(|j| {
            j.context
                .report
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .clone()
        })
    }
}
impl Drop for Builder {
    fn drop(&mut self) {
        self.cancel();
        if let Some(job) = &mut self.job
            && let Some(thread) = job.thread.take()
        {
            let _ = thread.join();
        }
    }
}
/// Resolve the installed source workspace; distributors can override it explicitly.
pub fn workspace() -> PathBuf {
    std::env::var_os("SCENEMAX_BUILD_WORKSPACE")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../.."))
}
