//! Java-style cinematic stack: descriptive easing combos and a retained cell grid.
use super::*;
use scenemax_ide_services::scene3d::Entity3d;
use scenemax_ide_ui::property;
#[derive(Resource, Default)]
pub(crate) struct Selection {
    pub(super) rig: String,
    pub(super) row: Option<usize>,
}
#[derive(Component)]
pub(crate) struct Row(usize);
pub(super) const EASING: &[(&str, &str)] = &[
    ("linear", "Linear - constant speed, no easing"),
    ("ease_in_quad", "Ease In Quad - simple acceleration"),
    ("ease_out_quad", "Ease Out Quad - natural stopping"),
    ("ease_in_cubic", "Ease In Cubic - stronger acceleration"),
    ("ease_out_cubic", "Ease Out Cubic - smoother dramatic stop"),
    (
        "ease_in_expo",
        "Ease In Expo - very fast ramp-up after a gentle start",
    ),
    (
        "ease_out_expo",
        "Ease Out Expo - dramatic deceleration into the stop",
    ),
    ("ease_in_sine", "Ease In Sine - soft cinematic acceleration"),
    ("ease_out_sine", "Ease Out Sine - soft cinematic settling"),
];
pub(super) fn build(commands: &mut Commands, parent: Entity, scene: &Scene3d, entry: &Entity3d) {
    use super::inspector::Property;
    let p = &entry.properties;
    property::heading(commands, parent, "Cinematic stack");
    button(
        commands,
        parent,
        "Add Rail",
        super::tools::Action::Add("CINEMATIC_RIG"),
    );
    field(
        commands,
        parent,
        "Runtime ID",
        "cinematicRuntimeId",
        p["cinematicRuntimeId"].as_str().unwrap_or(""),
    );
    choice(
        commands,
        parent,
        "Look at target",
        "cinematicTargetEntityName",
        p,
        &scene
            .entities
            .iter()
            .filter(|e| e.id != entry.id && e.kind == "MODEL")
            .map(|e| e.name.clone())
            .chain(std::iter::once(String::new()))
            .collect::<Vec<_>>(),
    );
    vector(
        commands,
        parent,
        "Target offset",
        "cinematicTargetOffset",
        std::array::from_fn(|i| p["cinematicTargetOffset"][i].as_f64().unwrap_or(0.) as f32),
    );
    for (key, title) in [
        ("cinematicEaseIn", "Ease in"),
        ("cinematicEaseOut", "Ease out"),
    ] {
        let row = labeled_row(commands, parent, title);
        let value = p[key].as_str().unwrap_or("linear");
        let mut options: Vec<_> = EASING
            .iter()
            .map(|(id, title)| (id.to_string(), title.to_string()))
            .collect();
        if !options.iter().any(|(id, _)| id == value) {
            options.push((value.into(), value.into()));
        }
        property::dropdown_labeled(
            commands,
            row,
            Property(key.into(), value.into()),
            value,
            &options,
        );
    }
    field(
        commands,
        parent,
        "Preview duration (seconds)",
        "number:cinematicPreviewDuration",
        &p["cinematicPreviewDuration"]
            .as_f64()
            .unwrap_or(5.)
            .to_string(),
    );
    let entries = p["cinematicSegments"]
        .as_array()
        .map(Vec::as_slice)
        .unwrap_or(&[]);
    commands.insert_resource(Selection {
        rig: entry.id.clone(),
        row: (!entries.is_empty()).then_some(0),
    });
    let table = commands
        .spawn((
            Node {
                width: percent(100.),
                flex_direction: FlexDirection::Column,
                border: px(1.).all(),
                margin: UiRect::top(px(6.)),
                ..default()
            },
            BorderColor::all(EDGE),
            ChildOf(parent),
        ))
        .id();
    let header = grid_row(commands, table);
    for (title, width) in [("Rail", 40.), ("Start", 19.), ("End", 19.), ("Weight", 22.)] {
        let cell = cell(commands, header, width);
        commands.spawn((label(title, 11.), ChildOf(cell)));
    }
    for (i, segment) in entries.iter().enumerate() {
        let row = grid_row(commands, table);
        commands
            .entity(row)
            .insert((Row(i), BackgroundColor(PANEL)))
            .observe(
                move |_: On<Pointer<Press>>, mut selection: ResMut<Selection>| {
                    selection.row = Some(i);
                },
            );
        let name = cell(commands, row, 40.);
        commands.spawn((
            label(segment["trackName"].as_str().unwrap_or("Missing rail"), 11.),
            TextLayout::no_wrap(),
            ChildOf(name),
        ));
        for (key, width, default) in [
            ("startAnchor", 19., 0.),
            ("endAnchor", 19., 0.),
            ("speed", 22., 30.),
        ] {
            let cell = cell(commands, row, width);
            let value = segment[key].as_f64().unwrap_or(default).to_string();
            property::input(
                commands,
                cell,
                Property(format!("segment:{i}:{key}"), value.clone()),
                &value,
            );
        }
    }
    if entries.is_empty() {
        commands.spawn((
            label(
                "No rails in the stack. Add a rail and its anchor range.",
                11.,
            ),
            ChildOf(table),
        ));
    }
    let actions = grid_row(commands, parent);
    for (title, action) in [
        ("↑", super::segments::Action::MoveSelected(-1)),
        ("↓", super::segments::Action::MoveSelected(1)),
        ("Remove", super::segments::Action::RemoveSelected),
    ] {
        button(commands, actions, title, action);
    }
}
fn grid_row(commands: &mut Commands, parent: Entity) -> Entity {
    commands
        .spawn((
            Node {
                width: percent(100.),
                min_height: px(28.),
                align_items: AlignItems::Center,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(parent),
        ))
        .id()
}
fn cell(commands: &mut Commands, parent: Entity, width: f32) -> Entity {
    commands
        .spawn((
            Node {
                width: percent(width),
                min_width: px(0.),
                min_height: px(28.),
                padding: UiRect::horizontal(px(3.)),
                border: UiRect {
                    right: px(1.),
                    bottom: px(1.),
                    ..default()
                },
                overflow: Overflow::clip(),
                align_items: AlignItems::Center,
                ..default()
            },
            BorderColor::all(EDGE),
            ChildOf(parent),
        ))
        .id()
}
pub(crate) fn highlight(selection: Res<Selection>, mut rows: Query<(&Row, &mut BackgroundColor)>) {
    for (row, mut color) in &mut rows {
        let desired = if selection.row == Some(row.0) {
            SELECTED
        } else {
            PANEL
        };
        if color.0 != desired {
            color.0 = desired;
        }
    }
}

// Keep the cinematic stack visible while retaining precise input and sliders.
fn labeled_row(commands: &mut Commands, parent: Entity, title: &str) -> Entity {
    let row = grid_row(commands, parent);
    commands.spawn((
        label(title, 11.),
        Node {
            width: px(100.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(row),
    ));
    commands
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            ChildOf(row),
        ))
        .id()
}
fn field(commands: &mut Commands, parent: Entity, title: &str, key: &str, value: &str) {
    let content = labeled_row(commands, parent, title);
    super::inspector::field(commands, content, "", key, value);
}
fn choice(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    key: &str,
    p: &serde_json::Value,
    options: &[String],
) {
    let content = labeled_row(commands, parent, title);
    property::dropdown(
        commands,
        content,
        super::inspector::Property(key.into(), p[key].as_str().unwrap_or("").into()),
        p[key].as_str().unwrap_or(""),
        options,
    );
}
fn vector(commands: &mut Commands, parent: Entity, title: &str, key: &str, values: [f32; 3]) {
    let content = labeled_row(commands, parent, title);
    let row = grid_row(commands, content);
    for (i, value) in values.into_iter().enumerate() {
        let cell = commands
            .spawn((
                Node {
                    width: percent(33.333),
                    min_width: px(0.),
                    flex_direction: FlexDirection::Column,
                    padding: UiRect::horizontal(px(2.)),
                    ..default()
                },
                ChildOf(row),
            ))
            .id();
        super::inspector::field(
            commands,
            cell,
            ["X", "Y", "Z"][i],
            &format!("{key}:{i}"),
            &format!("{value:.4}"),
        );
    }
}
pub(super) fn basics(commands: &mut Commands, parent: Entity, entry: &Entity3d) {
    field(commands, parent, "Name", "name", &entry.name);
    vector(commands, parent, "Position", "position", entry.position);
    let (y, z, x) = Quat::from_array(entry.rotation)
        .normalize()
        .to_euler(EulerRot::YZX);
    vector(
        commands,
        parent,
        "Rotation (deg)",
        "angles",
        [x.to_degrees(), y.to_degrees(), z.to_degrees()],
    );
    property::checkbox(
        commands,
        parent,
        super::inspector::Proportional,
        "Proportional scale",
        true,
    );
    vector(commands, parent, "Scale", "scale", entry.scale);
}
