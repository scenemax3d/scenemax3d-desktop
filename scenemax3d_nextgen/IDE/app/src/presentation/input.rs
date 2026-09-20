use super::components::{Editor, Field};
use crate::application::{Command, CommandQueue, EditCommand, Session};
use bevy::{prelude::*, text::EditableText, window::WindowCloseRequested};
use scenemax_ide_core::DocumentId;
use std::path::PathBuf;

#[derive(Component, Clone)]
pub(crate) enum Action {
    SaveCopy,
    SaveCloseTab,
    DiscardTab,
    CancelTab,
    FindProject,
    OpenAt(PathBuf, usize),
    RestoreRecovery,
    DiscardRecovery,
    ClearConsole,
    Undo,
    Redo,
    CloseDocument(DocumentId),
    NewProject,
    NewScript,
    Refresh,
    FindNext,
    FindPrevious,
    Replace,
    ReplaceAll,
    GoToLine,
    Comment,
    Select(DocumentId),
    Save,
    SaveAll,
    Check,
    Run,
    RunProject,
    RefreshProjects,
    ChooseProject(PathBuf),
    Exit,
    Restart,
    Stop,
    OpenProject,
    CancelClose,
    DiscardExit,
}
impl Action {
    fn command(&self, values: &[(Entity, Field, String)]) -> Command {
        let value = |field| {
            values
                .iter()
                .find(|(_, kind, _)| *kind == field)
                .map(|(_, _, text)| text.clone())
                .unwrap_or_default()
        };
        match self {
            Self::SaveCopy => Command::SaveCopy(value(Field::NewScript).into()),
            Self::SaveCloseTab => Command::SaveCloseTab,
            Self::DiscardTab => Command::DiscardTab,
            Self::CancelTab => Command::CancelTab,
            Self::FindProject => Command::FindProject(value(Field::Find)),
            Self::OpenAt(path, line) => Command::OpenAt(path.clone(), *line),
            Self::RestoreRecovery => Command::RestoreRecovery,
            Self::DiscardRecovery => Command::DiscardRecovery,
            Self::ClearConsole => Command::ClearConsole,
            Self::Undo => Command::Edit(EditCommand::Undo),
            Self::Redo => Command::Edit(EditCommand::Redo),
            Self::CloseDocument(id) => Command::CloseDocument(*id),
            Self::NewProject => Command::NewProject(value(Field::Project).into()),
            Self::NewScript => Command::Create(value(Field::NewScript).into()),
            Self::Refresh => Command::Refresh,
            Self::FindNext => Command::Edit(EditCommand::Find(value(Field::Find), false)),
            Self::FindPrevious => Command::Edit(EditCommand::Find(value(Field::Find), true)),
            Self::Replace => Command::Edit(EditCommand::Replace(
                value(Field::Find),
                value(Field::Replace),
            )),
            Self::ReplaceAll => Command::Edit(EditCommand::ReplaceAll(
                value(Field::Find),
                value(Field::Replace),
            )),
            Self::GoToLine => Command::Edit(EditCommand::GoToLine(
                value(Field::Line).parse().unwrap_or(1),
            )),
            Self::Comment => Command::Edit(EditCommand::Comment),
            Self::Select(id) => Command::Select(*id),
            Self::Save => Command::Save,
            Self::SaveAll => Command::SaveAll,
            Self::Check => Command::Check,
            Self::Run => Command::Run,
            Self::RunProject => Command::RunProject,
            Self::RefreshProjects => Command::RefreshProjects,
            Self::ChooseProject(path) => Command::OpenProject(path.clone()),
            Self::Exit => Command::RequestClose,
            Self::Restart => Command::Restart,
            Self::Stop => Command::Stop,
            Self::OpenProject => Command::OpenProject(value(Field::Project).into()),
            Self::CancelClose => Command::CancelClose,
            Self::DiscardExit => Command::DiscardExit,
        }
    }
}
type ChangedButtons<'w, 's> =
    Query<'w, 's, (&'static Interaction, &'static Action), (Changed<Interaction>, With<Button>)>;

type PropertyInputs<'w, 's> = Query<
    'w,
    's,
    (),
    Or<(
        With<super::designer::Property>,
        With<super::material::Field>,
        With<super::model_import::Field>,
        With<super::sprite_import::Field>,
        With<super::effect_import::Field>,
        With<super::scene3d::inspector::Property>,
    )>,
>;

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct InputFields<'w, 's> {
    zoom: ResMut<'w, super::editing::EditorZoom>,
    editors: Query<'w, 's, (), super::editing::CodeEditorFilter>,
    deployment: Option<Res<'w, crate::application::deployment::Deployment>>,
    menu: Option<Res<'w, super::tree_menu::State>>,
    imports: Option<Res<'w, super::asset_import::State>>,
    fields: Query<'w, 's, (Entity, &'static Field, &'static EditableText)>,
    properties: PropertyInputs<'w, 's>,
}
pub(crate) fn collect_actions(
    buttons: ChangedButtons,
    keys: Res<ButtonInput<KeyCode>>,
    input_fields: InputFields,
    mut focus: Option<ResMut<bevy::input_focus::InputFocus>>,
    mut closes: MessageReader<WindowCloseRequested>,
    mut queue: ResMut<CommandQueue>,
    session: Res<Session>,
) {
    if input_fields.imports.as_ref().is_some_and(|m| m.is_open())
        || input_fields.deployment.as_ref().is_some_and(|m| m.open)
        || input_fields.menu.as_ref().is_some_and(|m| m.is_open())
    {
        return;
    }
    let InputFields {
        imports: _,
        fields,
        properties,
        mut zoom,
        editors,
        ..
    } = input_fields;
    let values = fields
        .iter()
        .map(|(entity, kind, input)| (entity, *kind, input.value().to_string()))
        .collect::<Vec<_>>();
    for (interaction, action) in &buttons {
        if *interaction == Interaction::Pressed {
            queue.0.push_back(action.command(&values));
        }
    }
    let ctrl = keys.any_pressed([KeyCode::ControlLeft, KeyCode::ControlRight]);
    let shift = keys.any_pressed([KeyCode::ShiftLeft, KeyCode::ShiftRight]);
    if ctrl
        && !session.composing
        && focus
            .as_ref()
            .and_then(|f| f.get())
            .is_some_and(|e| editors.contains(e))
    {
        if keys.any_just_pressed([KeyCode::Equal, KeyCode::NumpadAdd]) {
            zoom.adjust(1.);
        }
        if keys.any_just_pressed([KeyCode::Minus, KeyCode::NumpadSubtract]) {
            zoom.adjust(-1.);
        }
    }

    for (key, field) in [
        (KeyCode::KeyF, Field::Find),
        (KeyCode::KeyG, Field::Line),
        (KeyCode::KeyN, Field::NewScript),
    ] {
        if ctrl
            && keys.just_pressed(key)
            && let (Some(focus), Some((entity, _, _))) = (
                focus.as_mut(),
                values.iter().find(|(_, kind, _)| *kind == field),
            )
        {
            focus.set(*entity, bevy::input_focus::FocusCause::Navigated);
        }
    }
    let in_field = focus
        .as_ref()
        .and_then(|f| f.get())
        .is_some_and(|entity| fields.get(entity).is_ok() || properties.get(entity).is_ok());
    if !in_field && !session.composing {
        if ctrl && keys.just_pressed(KeyCode::KeyZ) {
            queue.0.push_back(Command::Edit(if shift {
                EditCommand::Redo
            } else {
                EditCommand::Undo
            }));
        }
        if ctrl && keys.just_pressed(KeyCode::KeyY) {
            queue.0.push_back(Command::Edit(EditCommand::Redo));
        }
        if ctrl && keys.just_pressed(KeyCode::Slash) {
            queue.0.push_back(Command::Edit(EditCommand::Comment));
        }
    }
    if ctrl
        && keys.any_pressed([KeyCode::AltLeft, KeyCode::AltRight])
        && keys.just_pressed(KeyCode::KeyR)
    {
        queue.0.push_back(Command::Restart);
    }
    if ctrl && keys.just_pressed(KeyCode::KeyW) {
        queue.0.push_back(Command::CloseTab);
    }
    if keys.just_pressed(KeyCode::F3) {
        queue.0.push_back(
            if shift {
                Action::FindPrevious
            } else {
                Action::FindNext
            }
            .command(&values),
        );
    }
    if keys.just_pressed(KeyCode::Enter) && !ctrl {
        let selected = focus
            .as_ref()
            .and_then(|f| f.get())
            .and_then(|entity| fields.get(entity).ok())
            .map(|(_, kind, _)| *kind);
        let action = match selected {
            Some(Field::Find) => Some(Action::FindNext),
            Some(Field::Project) => Some(Action::OpenProject),
            Some(Field::Line) => Some(Action::GoToLine),
            Some(Field::NewScript) => Some(Action::NewScript),
            _ => None,
        };
        if let Some(action) = action {
            queue.0.push_back(action.command(&values));
        }
    }
    if ctrl && keys.just_pressed(KeyCode::KeyS) {
        queue.0.push_back(if shift {
            Command::SaveAll
        } else {
            Command::Save
        });
    }
    if ctrl && keys.just_pressed(KeyCode::Enter) {
        queue.0.push_back(Command::Check);
    }
    if keys.just_pressed(KeyCode::F8) || (ctrl && keys.just_pressed(KeyCode::F12)) {
        queue.0.push_back(Command::Run);
    }
    if keys.just_pressed(KeyCode::F10) || (!ctrl && keys.just_pressed(KeyCode::F12)) {
        queue.0.push_back(Command::RunProject);
    }
    if keys.just_pressed(KeyCode::F5) {
        queue
            .0
            .push_back(if shift { Command::Stop } else { Command::Run });
    }
    if closes.read().next().is_some() {
        queue.0.push_back(Command::RequestClose);
    }
}
pub(crate) fn sync_documents(
    inputs: Query<(Entity, &Editor, &EditableText), Changed<EditableText>>,
    focus: Option<Res<bevy::input_focus::InputFocus>>,
    mut session: ResMut<Session>,
) {
    for (entity, editor, input) in &inputs {
        let text = input.value().to_string();
        if session.workspace.active_id() == Some(editor.0) {
            session.composing = input.is_composing();
        }
        if input.is_composing() {
            continue;
        }
        let selection = input.editor().raw_selection();
        let selection = scenemax_ide_core::Selection {
            anchor: selection.anchor().index(),
            focus: selection.focus().index(),
        };
        if let Ok(doc) = session.workspace.document_mut(editor.0) {
            let selection = if focus
                .as_ref()
                .is_none_or(|focus| focus.get() == Some(entity))
            {
                selection
            } else {
                doc.selection()
            };
            doc.edit_text(text, selection);
        }
    }
}
