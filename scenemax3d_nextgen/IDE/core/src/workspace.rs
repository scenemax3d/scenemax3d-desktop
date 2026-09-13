use crate::{Document, EditorError, Project};
use std::{collections::BTreeMap, path::Path};

/// Stable buffer identity, never reused during an editor workspace session.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct DocumentId(u64);
/// Authoritative project, buffer and selection state, independent of UI entities.
#[derive(Debug)]
pub struct EditorWorkspace {
    project: Project,
    documents: BTreeMap<DocumentId, Document>,
    active: Option<DocumentId>,
    next_id: u64,
}
impl EditorWorkspace {
    /// Start with no open buffers.
    pub fn new(project: Project) -> Self {
        Self {
            project,
            documents: BTreeMap::new(),
            active: None,
            next_id: 0,
        }
    }
    /// Currently loaded project.
    pub fn project(&self) -> &Project {
        &self.project
    }
    /// Active buffer, if any.
    pub fn active_id(&self) -> Option<DocumentId> {
        self.active
    }
    /// Require an active buffer for an editing command.
    pub fn require_active(&self) -> Result<DocumentId, EditorError> {
        self.active.ok_or(EditorError::NoActiveDocument)
    }
    /// Iterate in buffer opening order.
    pub fn documents(&self) -> impl Iterator<Item = (DocumentId, &Document)> {
        self.documents.iter().map(|(id, doc)| (*id, doc))
    }
    /// Exclusively visit buffers for operations such as Save All.
    pub fn documents_mut(&mut self) -> impl Iterator<Item = &mut Document> {
        self.documents.values_mut()
    }
    /// Resolve a stable buffer identity.
    pub fn document(&self, id: DocumentId) -> Result<&Document, EditorError> {
        self.documents.get(&id).ok_or(EditorError::UnknownDocument)
    }
    /// Mutate a buffer through its revision-aware methods.
    pub fn document_mut(&mut self, id: DocumentId) -> Result<&mut Document, EditorError> {
        self.documents
            .get_mut(&id)
            .ok_or(EditorError::UnknownDocument)
    }
    /// Find an existing canonical path without reloading unsaved edits.
    pub fn find_document(&self, path: &Path) -> Option<DocumentId> {
        self.documents()
            .find(|(_, doc)| doc.path() == path)
            .map(|(id, _)| id)
    }
    /// Whether replacing the project would lose work.
    pub fn has_dirty_documents(&self) -> bool {
        self.documents.values().any(Document::is_dirty)
    }
    /// Add or activate a buffer. Duplicate paths preserve the current buffer.
    pub fn open_document(&mut self, document: Document) -> Result<DocumentId, EditorError> {
        if let Some(id) = self.find_document(document.path()) {
            self.select(id)?;
            return Ok(id);
        }
        let next = self
            .next_id
            .checked_add(1)
            .ok_or(EditorError::IdentifierExhausted)?;
        let id = DocumentId(self.next_id);
        self.next_id = next;
        self.documents.insert(id, document);
        self.active = Some(id);
        Ok(id)
    }
    /// Select only an existing buffer.
    pub fn select(&mut self, id: DocumentId) -> Result<(), EditorError> {
        self.document(id)?;
        self.active = Some(id);
        Ok(())
    }
    /// Close a clean buffer and select an adjacent surviving tab.
    pub fn close_document(&mut self, id: DocumentId) -> Result<(), EditorError> {
        if self.document(id)?.is_dirty() {
            return Err(EditorError::UnsavedDocuments);
        }
        self.discard_document(id)
    }
    /// Discard a buffer only after an explicit user decision.
    pub fn discard_document(&mut self, id: DocumentId) -> Result<(), EditorError> {
        self.document(id)?;
        self.documents.remove(&id);
        if self.active == Some(id) {
            self.active = self.documents.keys().next_back().copied();
        }
        Ok(())
    }
    /// Replace inventory without discarding open buffers.
    pub fn refresh_project(&mut self, project: Project) {
        self.project = project;
    }
    /// Replace a clean project without resetting document identity allocation.
    pub fn switch_project(&mut self, project: Project) -> Result<(), EditorError> {
        if self.has_dirty_documents() {
            return Err(EditorError::UnsavedDocuments);
        }
        self.project = project;
        self.documents.clear();
        self.active = None;
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn project() -> Project {
        Project::new("project".into(), vec![])
    }
    fn document() -> Document {
        Document::from_bytes("project/test.code".into(), b"source".to_vec()).unwrap()
    }
    #[test]
    fn duplicate_open_preserves_dirty_buffer_and_identity() {
        let mut workspace = EditorWorkspace::new(project());
        let id = workspace.open_document(document()).unwrap();
        workspace
            .document_mut(id)
            .unwrap()
            .replace_text("unsaved".into());
        assert_eq!(workspace.open_document(document()).unwrap(), id);
        assert_eq!(workspace.document(id).unwrap().text(), "unsaved");
    }
    #[test]
    fn project_switch_cannot_alias_old_document_ids() {
        let mut workspace = EditorWorkspace::new(project());
        let old = workspace.open_document(document()).unwrap();
        workspace.switch_project(project()).unwrap();
        let new = workspace.open_document(document()).unwrap();
        assert_ne!(old, new);
        assert!(workspace.select(old).is_err());
        assert_eq!(workspace.active_id(), Some(new));
    }
    #[test]
    fn project_switch_refuses_dirty_buffers() {
        let mut workspace = EditorWorkspace::new(project());
        let id = workspace.open_document(document()).unwrap();
        workspace
            .document_mut(id)
            .unwrap()
            .replace_text("unsaved".into());
        assert!(matches!(
            workspace.switch_project(project()),
            Err(EditorError::UnsavedDocuments)
        ));
        assert_eq!(workspace.document(id).unwrap().text(), "unsaved");
    }
}
