//! Filesystem, diagnostics and process adapters. No Bevy or window dependencies.
#![forbid(unsafe_code)]
#![deny(missing_docs)]
#![cfg_attr(not(test), deny(clippy::unwrap_used, clippy::expect_used))]

mod catalog;
mod diagnostics;
mod filesystem;
mod projector;
mod recovery;
mod runtime_log;
mod search;
pub use search::SearchReport;
mod storage;
mod tree_operations;
pub use diagnostics::{Diagnostics, SourceSnapshot, SyntaxReport};
pub use filesystem::{Filesystem, MAX_DOCUMENT_BYTES, MAX_SCRIPTS};
pub use projector::ProjectorProcess;
pub use recovery::RecoveryBatch;
pub use storage::{Storage, StorageRequest, StorageResult};
pub use tree_operations::{TreeOperation, TreeOutcome};

use std::{io, path::PathBuf};

/// Typed errors at IDE service boundaries.
#[derive(Debug, thiserror::Error)]
pub enum ServiceError {
    /// Invalid domain transition or source encoding.
    #[error(transparent)]
    Editor(#[from] scenemax_ide_core::EditorError),
    /// A filesystem operation failed.
    #[error("{operation}: {path}: {source}")]
    Io {
        /// Operation that failed.
        operation: &'static str,
        /// Affected path.
        path: PathBuf,
        /// Underlying operating system error.
        source: io::Error,
    },
    /// The supplied project root is not a directory.
    #[error("Project must be a directory: {0}")]
    NotDirectory(PathBuf),
    /// A path resolves outside the selected project.
    #[error("File is outside the project: {0}")]
    OutsideProject(PathBuf),
    /// Limit prevents unbounded synchronous work in the initial implementation.
    #[error("{0}")]
    Limit(&'static str),
    /// The file changed since it was loaded.
    #[error("File changed on disk; save cancelled to protect external edits: {0}")]
    ExternalChange(PathBuf),
    /// Save must not bypass read-only protection.
    #[error("File is read-only: {0}")]
    ReadOnly(PathBuf),
    /// The requested operation is already running.
    #[error("{0} is already running")]
    Busy(&'static str),
    /// A parser worker disconnected.
    #[error("Parser worker is unavailable")]
    WorkerUnavailable,
    /// The storage worker disconnected or could not accept a request.
    #[error("File worker is unavailable")]
    StorageUnavailable,
    /// Invalid project catalog data.
    #[error("Project catalog: {0}")]
    Catalog(String),
    /// Invalid or unreadable recovery data.
    #[error("Recovery data: {0}")]
    Recovery(String),
    /// A child process operation failed.
    #[error("Projector process: {0}")]
    Process(#[from] io::Error),
    /// A separate projector build was not found.
    #[error("Bevy projector not found at {0}; build it or pass --projector")]
    MissingProjector(PathBuf),
}

fn io_result<T>(
    path: &std::path::Path,
    operation: &'static str,
    result: io::Result<T>,
) -> Result<T, ServiceError> {
    result.map_err(|source| ServiceError::Io {
        operation,
        path: path.to_owned(),
        source,
    })
}

mod symbols;
pub use symbols::{FileSymbols, IndexReport, IndexRequest, SymbolIndexer};

/// Embedded UI designer preview adapter.
pub mod scene;

/// Read-only Java 3D scene import.
pub mod scene3d;

pub mod imports;
