//! IK edits participate in ordinary document history and revision checks.
use super::{Session, ViewChange, material::Edit};
use bevy::prelude::*;
pub(crate) fn edit(
    edit: Edit,
    session: &mut Session,
    changes: &mut MessageWriter<ViewChange>,
) -> anyhow::Result<()> {
    let doc = session.workspace.document_mut(edit.id)?;
    anyhow::ensure!(
        doc.revision() == edit.revision,
        "IK changed; retry the edit"
    );
    let index = edit.value.as_u64().unwrap_or(0) as usize;
    let source = scenemax_ide_core::ik::edit(doc.text(), &edit.pointer, edit.value, index)
        .map_err(anyhow::Error::msg)?;
    if edit.continuing {
        doc.replace_text_continuing(source);
    } else {
        doc.replace_text(source);
    }
    changes.write(ViewChange::BufferChanged(edit.id));
    Ok(())
}
