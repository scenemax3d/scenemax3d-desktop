use super::{components::*, input::Action};
use bevy::prelude::*;
use scenemax_ide_core::{Document, DocumentId};
use scenemax_ide_ui::{
    ButtonSurface, label, spawn_editor,
    theme::{EDGE, PANEL},
};
pub(crate) fn editor(
    commands: &mut Commands,
    parent: Entity,
    id: DocumentId,
    doc: &Document,
    active: bool,
) {
    let host = commands
        .spawn((
            EditorHost(id),
            Node {
                width: percent(100.),
                height: percent(100.),
                min_width: px(0.),
                display: if active { Display::Flex } else { Display::None },
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smeffectimport"))
    {
        commands
            .entity(host)
            .insert(super::effect_import::Import::default());
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smspriteimport"))
    {
        commands
            .entity(host)
            .insert(super::sprite_import::Import::default());
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smmodelimport"))
    {
        commands
            .entity(host)
            .insert(super::model_import::Import::default());
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|ext| ext.eq_ignore_ascii_case("smui"))
    {
        commands
            .entity(host)
            .insert(super::designer::Designer::default());
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|ext| ext.eq_ignore_ascii_case("smdesign"))
    {
        commands.entity(host).insert(super::scene3d::SceneHost);
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smmat"))
    {
        commands.entity(host).insert(super::material::MaterialHost);
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smweapon"))
    {
        commands.entity(host).insert(super::weapon::WeaponHost);
        return;
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smmotion"))
    {
        commands.entity(host).insert(super::motion::MotionHost);
        return;
    }
    if scenemax_ide_core::ik::is_file(doc.path()) {
        commands.entity(host).insert(super::ik::IkHost);
        return;
    }
    let gutter = commands
        .spawn((
            GutterPanel,
            Node {
                width: px(58.),
                height: percent(100.),
                flex_shrink: 0.,
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(host),
        ))
        .id();
    commands.spawn((
        Gutter(id),
        Text::new("1"),
        TextColor(scenemax_ide_ui::theme::INK),
        TextFont {
            font: bevy::text::FontSource::Monospace,
            font_size: FontSize::Px(16.),
            ..default()
        },
        bevy::text::LineHeight::Px(22.),
        Node {
            position_type: PositionType::Absolute,
            right: px(8.),
            top: px(16.),
            ..default()
        },
        ChildOf(gutter),
    ));
    spawn_editor(commands, host, Editor(id), doc.text(), true);
    commands.entity(host).observe(
        |mut event: On<bevy::input_focus::FocusedInput<bevy::input::keyboard::KeyboardInput>>,
         keys: Res<ButtonInput<KeyCode>>,
         inputs: Query<&bevy::text::EditableText>,
         mut queue: ResMut<crate::application::CommandQueue>,
         mut completion: ResMut<super::completion::CompletionState>,
         focus: Option<Res<bevy::input_focus::InputFocus>>| {
            // Bubbling changes the event target to this host; resolve the actual
            // input owner through focus before routing Tab.
            let focused = focus
                .as_ref()
                .and_then(|f| f.get())
                .unwrap_or(event.focused_entity);
            if event.input.key_code == KeyCode::Tab
                && !inputs.get(focused).is_ok_and(|i| i.is_composing())
            {
                event.propagate(false);
                if completion.is_open(focused) {
                    if event.input.state.is_pressed() {
                        completion.accept = true;
                    }
                    return;
                }
                if event.input.state.is_pressed() {
                    queue.0.push_back(crate::application::Command::Edit(
                        crate::application::EditCommand::Indent(
                            keys.any_pressed([KeyCode::ShiftLeft, KeyCode::ShiftRight]),
                        ),
                    ));
                }
            }
        },
    );
}
pub(crate) fn tab(commands: &mut Commands, parent: Entity, index: DocumentId, doc: &Document) {
    let root = commands
        .spawn((
            TabButton(index),
            Interaction::None,
            ButtonSurface(PANEL),
            BorderColor::all(EDGE),
            Node {
                height: px(28.),
                align_items: AlignItems::Center,
                border: UiRect::bottom(px(2.)),
                flex_shrink: 0.,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(parent),
        ))
        .id();
    let select = commands
        .spawn((
            Button,
            Action::Select(index),
            ButtonSurface(Color::NONE),
            BackgroundColor(Color::NONE),
            Node {
                height: percent(100.),
                align_items: AlignItems::Center,
                padding: UiRect::axes(px(8.), px(0.)),
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        label(
            doc.path().file_name().unwrap_or_default().to_string_lossy(),
            10.,
        ),
        TabLabel(index),
        ChildOf(select),
    ));
    let close = scenemax_ide_ui::button(commands, root, "×", Action::CloseDocument(index));
    commands
        .entity(close)
        .insert((ButtonSurface(Color::NONE), BackgroundColor(Color::NONE)));
}
