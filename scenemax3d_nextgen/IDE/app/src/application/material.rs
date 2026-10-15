//! Material document transactions and background asset inventory.
use super::{EditorServices, Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, DocumentRevision};
use scenemax_ide_services::{StorageRequest, StorageResult, material::Library};
use serde_json::Value;
use std::path::PathBuf;
#[derive(Resource, Default)]
pub(crate) struct MaterialLibrary {
    pub(crate) project: PathBuf,
    pub(crate) assets: Option<Library>,
    pub(crate) error: String,
    pending: Option<PathBuf>,
}
pub(crate) fn library(
    mut library: ResMut<MaterialLibrary>,
    mut services: ResMut<EditorServices>,
    session: Res<Session>,
) {
    if services.material_storage.is_pending() {
        match services.material_storage.poll() {
            Ok(Some(StorageResult::MaterialLibrary(result))) => {
                if library.pending.take().as_deref() == Some(session.workspace.project().root()) {
                    match result {
                        Ok(assets) => {
                            library.assets = Some(assets);
                            library.error.clear();
                        }
                        Err(e) => library.error = e,
                    }
                }
            }
            Err(e) => {
                library.error = e.to_string();
                library.pending = None;
            }
            _ => {}
        }
    }
    let active = session
        .workspace
        .active_id()
        .and_then(|id| session.workspace.document(id).ok())
        .is_some_and(|d| d.path().extension().is_some_and(|e| e == "smmat"));
    let project = session.workspace.project().root();
    if active && library.project != project && !services.material_storage.is_pending() {
        library.project = project.to_owned();
        library.assets = None;
        library.error = "Loading project textures and models…".into();
        match services
            .material_storage
            .request(StorageRequest::MaterialLibrary(project.to_owned()))
        {
            Ok(()) => library.pending = Some(project.to_owned()),
            Err(e) => library.error = e.to_string(),
        }
    }
}
#[derive(Clone)]
pub(crate) struct Edit {
    pub(crate) id: DocumentId,
    pub(crate) revision: DocumentRevision,
    pub(crate) pointer: String,
    pub(crate) value: Value,
    pub(crate) continuing: bool,
}
pub(crate) fn edit(
    edit: Edit,
    session: &mut Session,
    changes: &mut MessageWriter<ViewChange>,
) -> anyhow::Result<()> {
    let doc = session.workspace.document_mut(edit.id)?;
    anyhow::ensure!(
        doc.revision() == edit.revision,
        "Material changed; please retry the edit"
    );
    let source = if edit.pointer == "preset" {
        let mut v = scenemax_assets::material::preset(edit.value.as_str().unwrap_or_default());
        let old: Value = serde_json::from_str(doc.text())?;
        // Preserve extension metadata and identity; presets replace only surface controls.
        let mut merged = old.clone();
        for (k, value) in v.as_object_mut().into_iter().flat_map(|v| v.iter_mut()) {
            if !matches!(k.as_str(), "name" | "targetSlot" | "textures") {
                merged[k] = value.clone();
            }
        }
        serde_json::to_string_pretty(&merged)?
    } else {
        scenemax_assets::material::patch(doc.text(), &edit.pointer, edit.value)
            .map_err(anyhow::Error::msg)?
    };
    if edit.continuing {
        doc.replace_text_continuing(source);
    } else {
        doc.replace_text(source);
    }
    changes.write(ViewChange::BufferChanged(edit.id));
    Ok(())
}
