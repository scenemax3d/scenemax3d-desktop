//! Editor domain state. No renderer, filesystem or process access.
#![forbid(unsafe_code)]
#![deny(missing_docs)]
pub mod assistance;
pub mod completion;
mod document;
mod editing;
mod history;
mod project;
pub mod syntax;
mod workspace;
pub use document::{Document, DocumentRevision, Selection};
pub use project::{Project, ProjectCatalog, ProjectEntry, ProjectSummary, SearchHit};
pub use workspace::{DocumentId, EditorWorkspace};

/// State transition errors independent of presentation.
#[derive(Debug, thiserror::Error)]
pub enum EditorError {
    /// Invalid or stale document identity.
    #[error("The requested document is not open")]
    UnknownDocument,
    /// The command requires a selected document.
    #[error("Open a script first")]
    NoActiveDocument,
    /// Replacing the project would lose edits.
    #[error("Save your documents before switching projects")]
    UnsavedDocuments,
    /// Source cannot be decoded without loss.
    #[error("Editor currently supports UTF-8 files only: {0}")]
    InvalidEncoding(#[from] std::str::Utf8Error),
    /// Identities must never wrap and alias old documents.
    #[error("Document identifier space exhausted; save and restart the IDE")]
    IdentifierExhausted,
}

/// Lossless editing of existing UI scene JSON.
pub mod scene;

pub mod scene3d;
