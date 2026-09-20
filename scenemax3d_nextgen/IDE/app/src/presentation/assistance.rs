//! Native input adaptation and bracket-selection presentation.
use super::editing::CodeEditorFilter;
use bevy::{
    prelude::*,
    text::{EditableText, EditableTextFilter, FontCx, LayoutCx, TextEdit},
};
use scenemax_ide_core::{
    Selection,
    assistance::{bracket_at, indented_newline},
};

#[derive(Component)]
pub(crate) struct BracketIndex(pub(crate) Vec<(usize, usize)>);

type CodeInputs<'w, 's> =
    Query<'w, 's, &'static mut EditableText, (CodeEditorFilter, Without<EditableTextFilter>)>;

pub(crate) fn indent_newlines(
    mut inputs: CodeInputs,
    fonts: Option<ResMut<FontCx>>,
    layouts: Option<ResMut<LayoutCx>>,
    clipboard: Option<ResMut<bevy::clipboard::Clipboard>>,
) {
    let (Some(mut fonts), Some(mut layouts), Some(mut clipboard)) = (fonts, layouts, clipboard)
    else {
        return;
    };
    for mut input in &mut inputs {
        if input.is_composing()
            || input.pending_paste.is_some()
            || !input
                .pending_edits
                .iter()
                .any(|e| matches!(e, TextEdit::Insert(s) if s == "\n"))
            || input.pending_edits.iter().any(|e| {
                matches!(
                    e,
                    TextEdit::Paste | TextEdit::ImeSetCompose { .. } | TextEdit::ImeCommit { .. }
                )
            })
        {
            continue;
        }
        // Use Bevy's own edit application for every action, in order. This sees
        // the caret after any earlier key events in the same frame. The final
        // result is synchronized into the document as one undo transaction.
        let pending = std::mem::take(&mut input.pending_edits);
        for mut edit in pending {
            if matches!(&edit, TextEdit::Insert(s) if s == "\n") {
                let selection = input.editor().raw_selection();
                edit = TextEdit::Insert(
                    indented_newline(
                        input.editor().raw_text(),
                        Selection {
                            anchor: selection.anchor().index(),
                            focus: selection.focus().index(),
                        },
                    )
                    .into(),
                );
            }
            input.queue_edit(edit);
            input.apply_pending_edits(&mut fonts.context, &mut layouts.0, &mut clipboard, |_| true);
        }
    }
}

type BracketInput<'a> = (
    Entity,
    &'a EditableText,
    &'a BracketIndex,
    &'a scenemax_ide_ui::TextHighlights,
    Option<&'a scenemax_ide_ui::TextEmphasis>,
);

pub(crate) fn bracket_emphasis(
    mut commands: Commands,
    inputs: Query<BracketInput<'static>, CodeEditorFilter>,
) {
    for (entity, input, index, highlights, previous) in &inputs {
        let selection = input.editor().raw_selection();
        let ranges = if input.is_composing()
            || input.value() != highlights.source.as_str()
            || selection.anchor().index() != selection.focus().index()
        {
            Vec::new()
        } else {
            bracket_at(&index.0, selection.focus().index())
                .map_or_else(Vec::new, |(a, b)| vec![a..a + 1, b..b + 1])
        };
        if previous.is_none_or(|p| p.ranges != ranges) {
            commands
                .entity(entity)
                .insert(scenemax_ide_ui::TextEmphasis { ranges });
        }
    }
}
