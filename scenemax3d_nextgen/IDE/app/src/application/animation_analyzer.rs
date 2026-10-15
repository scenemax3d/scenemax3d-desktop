//! Revision-checked analyzer transactions use the ordinary catalog document.
use super::{Session, ViewChange, material::Edit};
use bevy::prelude::*;
pub(crate) fn edit(
    edit: Edit,
    session: &mut Session,
    changes: &mut MessageWriter<ViewChange>,
) -> anyhow::Result<()> {
    let doc = session.workspace.document_mut(edit.id)?;
    anyhow::ensure!(
        scenemax_ide_core::animation_analyzer::is_file(doc.path()),
        "Animation records require a model catalog"
    );
    anyhow::ensure!(
        doc.revision() == edit.revision,
        "Model catalog changed; retry the edit"
    );
    let text = if edit.pointer == "replace" {
        serde_json::to_string_pretty(&edit.value)?
    } else {
        scenemax_ide_core::animation_analyzer::edit(doc.text(), &edit.pointer, edit.value)
            .map_err(anyhow::Error::msg)?
    };
    if edit.continuing {
        doc.replace_text_continuing(text);
    } else {
        doc.replace_text(text);
    }
    changes.write(ViewChange::BufferChanged(edit.id));
    Ok(())
}
