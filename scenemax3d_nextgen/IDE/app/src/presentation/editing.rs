use super::components::*;
use crate::application::{Session, ViewChange};
use bevy::{
    input_focus::InputFocus,
    prelude::*,
    text::{EditableText, FontCx, LayoutCx},
};

/// Code-editor typography shared by open and newly opened tabs for this session.
#[derive(Resource)]
pub(crate) struct EditorZoom(pub(crate) f32);
impl Default for EditorZoom {
    fn default() -> Self {
        Self(16.)
    }
}
impl EditorZoom {
    pub(crate) fn adjust(&mut self, delta: f32) {
        self.0 = (self.0 + delta).clamp(8., 48.);
    }
    fn line_height(&self) -> f32 {
        self.0 * 22. / 16.
    }
}

type EditorTypography<'w, 's> = Query<
    'w,
    's,
    (&'static mut TextFont, &'static mut bevy::text::LineHeight),
    Or<(
        With<Editor>,
        With<Gutter>,
        With<super::scene3d::inspector::CodeEditor>,
    )>,
>;

pub(crate) fn apply_editor_zoom(
    zoom: Res<EditorZoom>,
    mut text: EditorTypography,
    mut gutters: Query<&mut Node, With<GutterPanel>>,
) {
    for mut gutter in &mut gutters {
        let width = px((zoom.0 * 2.6 + 16.).max(58.));
        if gutter.width != width {
            gutter.width = width;
        }
    }
    for (mut font, mut height) in &mut text {
        let size = FontSize::Px(zoom.0);
        if font.font_size != size {
            font.font_size = size;
        }
        let line_height = bevy::text::LineHeight::Px(zoom.line_height());
        if *height != line_height {
            *height = line_height;
        }
    }
}

pub(crate) fn project_edits(
    mut changes: MessageReader<ViewChange>,
    session: Res<Session>,
    mut inputs: Query<(Entity, &Editor, &mut EditableText)>,
    mut fonts: Option<ResMut<FontCx>>,
    mut layouts: Option<ResMut<LayoutCx>>,
    mut focus: Option<ResMut<InputFocus>>,
) {
    for change in changes.read() {
        let id = match change {
            ViewChange::BufferChanged(id) | ViewChange::DocumentOpened(id) => Some(*id),
            ViewChange::ActiveChanged => session.workspace.active_id(),
            _ => None,
        };
        let Some(id) = id else {
            continue;
        };
        let Ok(document) = session.workspace.document(id) else {
            continue;
        };
        for (entity, editor, mut input) in &mut inputs {
            if editor.0 != id {
                continue;
            }
            if input.value() != document.text() {
                input.editor_mut().set_text(document.text());
            }
            if let (Some(fonts), Some(layouts)) = (fonts.as_mut(), layouts.as_mut()) {
                input.pending_edits.clear();
                let selection = document.selection();
                input
                    .editor_mut()
                    .driver(&mut fonts.context, &mut layouts.0)
                    .select_byte_range(selection.anchor, selection.focus);
            }
            if session.workspace.active_id() == Some(id)
                && let Some(focus) = focus.as_mut()
            {
                focus.set(entity, bevy::input_focus::FocusCause::Navigated);
            }
        }
    }
}

#[derive(PartialEq)]
pub(crate) struct GutterState {
    id: scenemax_ide_core::DocumentId,
    revision: scenemax_ide_core::DocumentRevision,
    selection: scenemax_ide_core::Selection,
    scroll: Vec2,
    viewport: Vec2,
    line_height: f32,
}
pub(crate) fn update_gutters(
    session: Res<Session>,
    zoom: Res<EditorZoom>,
    inputs: Query<(&Editor, &bevy::ui::widget::TextScroll, &ComputedNode)>,
    mut gutters: Query<(&Gutter, &mut Text, &mut Node), Without<CaretLabel>>,
    mut caret: Single<(&mut Text, &mut Node), With<CaretLabel>>,
    mut previous: Local<Option<GutterState>>,
) {
    let ui_designer = session
        .workspace
        .active_id()
        .and_then(|id| session.workspace.document(id).ok())
        .is_some_and(|doc| {
            scenemax_ide_core::ik::is_file(doc.path())
                || doc.path().extension().is_some_and(|ext| {
                    ext.eq_ignore_ascii_case("smik")
                        || ext.eq_ignore_ascii_case("smmotion")
                        || ext.eq_ignore_ascii_case("smweapon")
                        || ext.eq_ignore_ascii_case("smmat")
                        || ext.eq_ignore_ascii_case("smui")
                        || ext.eq_ignore_ascii_case("smmodelimport")
                        || ext.eq_ignore_ascii_case("smspriteimport")
                        || ext.eq_ignore_ascii_case("smeffectimport")
                })
        });
    let (caret, node) = &mut *caret;
    let display = if ui_designer {
        Display::None
    } else {
        Display::Flex
    };
    if node.display != display {
        node.display = display;
    }
    if ui_designer {
        previous.take();
        return;
    }
    if session.workspace.active_id().is_none() {
        previous.take();
        if caret.0 != "Open a document to begin" {
            caret.0 = "Open a document to begin".into();
        }
        return;
    }
    if let Some(id) = session.workspace.active_id()
        && let Ok(doc) = session.workspace.document(id)
        && doc
            .path()
            .extension()
            .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
    {
        previous.take();
        let text = "Scene designer · Live properties · Ctrl+S save · Ctrl+Z undo · Click objects to select";
        if caret.0 != text {
            caret.0 = text.into();
        }
        return;
    }
    for (editor, scroll, node) in &inputs {
        if session.workspace.active_id() != Some(editor.0) {
            continue;
        }
        let Ok(doc) = session.workspace.document(editor.0) else {
            continue;
        };
        let state = GutterState {
            id: editor.0,
            revision: doc.revision(),
            selection: doc.selection(),
            scroll: scroll.0,
            viewport: node.size(),
            line_height: zoom.line_height(),
        };
        if previous.as_ref() == Some(&state) {
            return;
        }
        *previous = Some(state);
        let scale = node.inverse_scale_factor();
        let y = scroll.0.y * scale;
        let first = (y / zoom.line_height()).floor() as usize;
        let count = ((node.size().y * scale / zoom.line_height()).ceil() as usize + 2).min(200);
        let total = doc.text().bytes().filter(|b| *b == b'\n').count() + 1;
        for (gutter, mut text, mut style) in &mut gutters {
            if gutter.0 != editor.0 {
                continue;
            }
            let value = (first + 1..=(first + count).min(total))
                .map(|line| line.to_string())
                .collect::<Vec<_>>()
                .join("\n");
            if text.0 != value {
                text.0 = value;
            }
            style.top = px(16. - y % zoom.line_height());
        }
        let (line, column) = doc.line_column();
        let value = format!(
            "Ln {line}, Col {column}   |   {total} lines   |   UTF-8   |   Ctrl+F find  •  Ctrl+G line  •  Tab indent"
        );
        if caret.0 != value {
            caret.0 = value;
        }
    }
}

pub(crate) type CodeEditorFilter = Or<(With<Editor>, With<super::scene3d::inspector::CodeEditor>)>;

pub(crate) fn highlight_documents(
    mut commands: Commands,
    inputs: Query<
        (
            Entity,
            &EditableText,
            Option<&scenemax_ide_ui::TextHighlights>,
        ),
        CodeEditorFilter,
    >,
) {
    use scenemax_ide_core::syntax::{TokenKind, highlight};
    for (entity, input, previous) in &inputs {
        if input.is_composing() || previous.is_some_and(|p| input.value() == p.source.as_str()) {
            continue;
        }
        let source = input.value().to_string();
        let tokens = highlight(&source);
        let brackets = scenemax_ide_core::assistance::bracket_index(&source, &tokens);
        let spans = tokens
            .into_iter()
            .map(|token| {
                let (r, g, b) = match token.kind {
                    TokenKind::Keyword => (183, 168, 217),
                    TokenKind::Scope | TokenKind::Separator => (174, 199, 207),
                    TokenKind::Type => (193, 185, 145),
                    TokenKind::Function => (184, 199, 161),
                    TokenKind::Literal => (172, 187, 209),
                    TokenKind::String => (201, 178, 143),
                    TokenKind::Comment => (126, 135, 148),
                    TokenKind::Operator => (185, 167, 161),
                    TokenKind::Error => (207, 146, 146),
                };
                (token.range, Color::srgb_u8(r, g, b))
            })
            .collect();
        commands.entity(entity).insert((
            scenemax_ide_ui::TextHighlights { source, spans },
            super::assistance::BracketIndex(brackets),
        ));
    }
}
