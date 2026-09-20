use super::{Session, ViewChange};
use anyhow::{Result, bail};
use bevy::prelude::*;
#[derive(Clone)]
pub(crate) enum EditCommand {
    Undo,
    Redo,
    Find(String, bool),
    Replace(String, String),
    ReplaceAll(String, String),
    GoToLine(usize),
    Indent(bool),
    Comment,
}
pub(crate) fn edit(
    command: EditCommand,
    session: &mut Session,
    changes: &mut MessageWriter<ViewChange>,
) -> Result<()> {
    let id = session.workspace.require_active()?;
    let document = session.workspace.document_mut(id)?;
    if document.path().extension().is_some_and(|e| {
        e.eq_ignore_ascii_case("smdesign")
            || e.eq_ignore_ascii_case("smmat")
            || e.eq_ignore_ascii_case("smmotion")
            || e.eq_ignore_ascii_case("smweapon")
    }) && !matches!(command, EditCommand::Undo | EditCommand::Redo)
    {
        bail!("Use the scene property inspector to edit this document");
    }
    match command {
        EditCommand::Undo => {
            if !document.undo() {
                bail!("Nothing to undo");
            }
        }
        EditCommand::Redo => {
            if !document.redo() {
                bail!("Nothing to redo");
            }
        }
        EditCommand::Find(needle, backwards) => {
            if !document.find(&needle, backwards) {
                bail!("No matching text");
            }
        }
        EditCommand::Replace(needle, replacement) => {
            if !document.replace_match(&needle, &replacement) {
                bail!("No matching text");
            }
        }
        EditCommand::ReplaceAll(needle, replacement) => {
            let count = document
                .replace_all(&needle, &replacement)
                .map_err(anyhow::Error::msg)?;
            session.status = format!("Replaced {count} occurrences");
        }
        EditCommand::GoToLine(line) => document.go_to_line(line),
        EditCommand::Indent(outdent) => document.indent(outdent),
        EditCommand::Comment => document.toggle_comment(),
    }
    changes.write(ViewChange::BufferChanged(id));
    Ok(())
}
