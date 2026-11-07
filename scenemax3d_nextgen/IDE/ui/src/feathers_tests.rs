//! Shared control integration tests, using the same Feathers scenes as the IDE.
use bevy::{feathers::controls::*, prelude::*, text::EditableText, ui_widgets::ValueChange};

#[derive(Component)]
struct Field;
fn app() -> (App, Entity) {
    let mut app = App::new();
    app.add_plugins((
        bevy::app::TaskPoolPlugin::default(),
        bevy::asset::AssetPlugin::default(),
        bevy::scene::ScenePlugin,
    ))
    .init_asset::<Image>()
    .init_asset::<bevy::text::Font>()
    .insert_resource(super::theme())
    .init_resource::<bevy::input_focus::InputFocus>()
    .init_resource::<ColorInputSettings>()
    .init_resource::<UnitsRegistry>();
    app.world_mut()
        .resource_mut::<UnitsRegistry>()
        .insert(&Dimensionless);
    let parent = app.world_mut().spawn(Node::default()).id();
    (app, parent)
}
#[test]
fn dropdown_retains_unknown_values_and_maps_labels_to_ids() {
    let (mut app, parent) = app();
    crate::property::dropdown_labeled(
        &mut app.world_mut().commands(),
        parent,
        Field,
        "imported",
        &[("walk".into(), "Walking animation".into())],
    );
    app.world_mut().flush();
    let world = app.world_mut();
    let control = world
        .query_filtered::<Entity, With<Field>>()
        .single(world)
        .unwrap();
    assert_eq!(
        world.get::<crate::property::Choice>(control).unwrap().0,
        "imported"
    );
    assert!(world.get::<FeathersSelect>(control).is_some());
    let row = world
        .query::<(Entity, &OptionIndex)>()
        .iter(world)
        .find(|(_, index)| index.0 == 1)
        .unwrap()
        .0;
    world.trigger(ValueChange {
        source: control,
        value: row,
        is_final: true,
    });
    assert_eq!(
        world.get::<crate::property::Choice>(control).unwrap().0,
        "walk"
    );
}
#[test]
fn number_scrubbing_and_external_edits_share_the_real_text_field() {
    use bevy::ecs::system::RunSystemOnce;
    let (mut app, parent) = app();
    let control = crate::number::input(
        &mut app.world_mut().commands(),
        parent,
        Field,
        "-12.125",
        -10.,
        10.,
        0.125,
    );
    app.world_mut().flush();
    let world = app.world_mut();
    let editor = world
        .query_filtered::<Entity, With<Field>>()
        .single(world)
        .unwrap();
    assert!(world.get::<FeathersNumberInput>(control).is_some());
    assert_eq!(
        world.get::<EditableText>(editor).unwrap().value(),
        "-12.125"
    );
    assert!(world.get::<HardLimit>(control).is_none());
    world.trigger(ValueChange {
        source: control,
        value: 3.5_f64,
        is_final: false,
    });
    world.flush();
    assert_eq!(
        *world.get::<NumberInputValue>(control).unwrap(),
        NumberInputValue::F64(3.5)
    );
    let mut text = world.get_mut::<EditableText>(editor).unwrap();
    assert!(!text.pending_edits.is_empty());
    text.pending_edits.clear();
    text.editor_mut().set_text("250.25");
    world.run_system_once(crate::number::update).unwrap();
    assert_eq!(
        *world.get::<NumberInputValue>(control).unwrap(),
        NumberInputValue::F64(250.25)
    );
}
#[test]
fn color_picker_preserves_alpha_and_accepts_external_hex_edits() {
    use bevy::ecs::system::RunSystemOnce;
    let (mut app, parent) = app();
    let editor = crate::color::input(&mut app.world_mut().commands(), parent, Field, "#AABBCC80");
    app.world_mut().flush();
    let world = app.world_mut();
    let picker = world
        .query_filtered::<Entity, With<FeathersColorInput>>()
        .single(world)
        .unwrap();
    world.trigger(ValueChange {
        source: picker,
        value: Color::srgba_u8(10, 20, 30, 64),
        is_final: true,
    });
    world.flush();
    assert_eq!(
        world.get::<EditableText>(editor).unwrap().value(),
        "#0A141E40"
    );
    world
        .get_mut::<EditableText>(editor)
        .unwrap()
        .editor_mut()
        .set_text("#112233");
    world.run_system_once(crate::color::update).unwrap();
    assert_eq!(
        world
            .get::<ColorInputValue>(picker)
            .unwrap()
            .0
            .to_srgba()
            .to_u8_array(),
        [17, 34, 51, 255]
    );
    world
        .get_mut::<EditableText>(editor)
        .unwrap()
        .editor_mut()
        .set_text("#invalid");
    world.run_system_once(crate::color::update).unwrap();
    assert_eq!(
        world.get::<EditableText>(editor).unwrap().value(),
        "#invalid"
    );
    assert_eq!(
        world
            .get::<ColorInputValue>(picker)
            .unwrap()
            .0
            .to_srgba()
            .to_u8_array(),
        [17, 34, 51, 255]
    );
}
#[test]
fn popup_escape_does_not_close_the_parent_designer() {
    use bevy::ecs::system::RunSystemOnce;
    let (mut app, _) = app();
    let world = app.world_mut();
    world.init_resource::<ButtonInput<KeyCode>>();
    world
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::Escape);
    world.spawn((FeathersMenuPopup, Visibility::Visible));
    world.run_system_once(super::popup_escape).unwrap();
    assert!(
        !world
            .resource::<ButtonInput<KeyCode>>()
            .just_pressed(KeyCode::Escape)
    );
}
#[test]
fn dropdown_programmatic_selection_updates_caption_and_selected_row() {
    use bevy::ecs::system::RunSystemOnce;
    let (mut app, parent) = app();
    app.add_plugins((SelectPlugin, bevy::ui_widgets::ListBoxPlugin));
    crate::property::dropdown_labeled(
        &mut app.world_mut().commands(),
        parent,
        Field,
        "first",
        &[
            ("first".into(), "First label".into()),
            ("second".into(), "Second label".into()),
        ],
    );
    app.world_mut().flush();
    let world = app.world_mut();
    let control = world
        .query_filtered::<Entity, With<Field>>()
        .single(world)
        .unwrap();
    world.get_mut::<crate::property::Choice>(control).unwrap().0 = "second".into();
    world
        .run_system_once(crate::property::synchronize_choices)
        .unwrap();
    app.update();
    let world = app.world_mut();
    let row = world
        .query_filtered::<&OptionIndex, With<bevy::ui::Selected>>()
        .single(world)
        .unwrap();
    assert_eq!(row.0, 1);
    // There must be both the option caption and the closed control's caption.
    assert_eq!(
        world
            .query::<&Text>()
            .iter(world)
            .filter(|t| t.0 == "Second label")
            .count(),
        2
    );
}
#[test]
fn popups_render_above_their_modal_designer() {
    use bevy::ecs::system::RunSystemOnce;
    let (mut app, parent) = app();
    let world = app.world_mut();
    world.entity_mut(parent).insert(GlobalZIndex(600));
    let popup = world
        .spawn((FeathersMenuPopup, GlobalZIndex(100), ChildOf(parent)))
        .id();
    world.run_system_once(super::popup_layers).unwrap();
    assert_eq!(world.get::<GlobalZIndex>(popup).unwrap().0, 601);
}

#[test]
fn dropdown_keyboard_navigation_commits_serialized_value() {
    use bevy::{
        ecs::system::RunSystemOnce,
        input::{
            ButtonState,
            keyboard::{Key, KeyboardInput},
        },
        input_focus::{FocusedInput, InputFocus, InputFocusVisible},
        ui_widgets::ListBox,
    };
    let (mut app, parent) = app();
    app.add_plugins((SelectPlugin, bevy::ui_widgets::ListBoxPlugin));
    app.init_resource::<InputFocusVisible>();
    crate::property::dropdown(
        &mut app.world_mut().commands(),
        parent,
        Field,
        "first",
        &["first".into(), "second".into()],
    );
    app.world_mut().flush();
    let world = app.world_mut();
    let popup = world
        .query_filtered::<Entity, With<FeathersMenuPopup>>()
        .single(world)
        .unwrap();
    world.entity_mut(popup).insert(Visibility::Visible);
    world.run_system_once(super::focus_dropdown).unwrap();
    let list = world
        .query_filtered::<Entity, With<ListBox>>()
        .single(world)
        .unwrap();
    assert_eq!(world.resource::<InputFocus>().get(), Some(list));
    for (key_code, logical_key) in [
        (KeyCode::ArrowDown, Key::ArrowDown),
        (KeyCode::Enter, Key::Enter),
    ] {
        world.trigger(FocusedInput::new(
            list,
            KeyboardInput {
                key_code,
                logical_key,
                text: None,
                state: ButtonState::Pressed,
                repeat: false,
                window: Entity::PLACEHOLDER,
            },
            Entity::PLACEHOLDER,
        ));
        world.flush();
    }
    assert_eq!(
        world
            .query::<&crate::property::Choice>()
            .single(world)
            .unwrap()
            .0,
        "second"
    );
}
